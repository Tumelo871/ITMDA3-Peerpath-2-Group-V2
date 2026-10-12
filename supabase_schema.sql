-- PEERPATH SUPABASE DATABASE
-- Run this whole file in Supabase SQL Editor.

create extension if not exists pgcrypto;

create table if not exists public.admin_emails (
  email text primary key check (email = lower(email))
);

-- ADD YOUR REAL ADMIN EMAILS HERE before going live.
-- Example:
-- insert into public.admin_emails(email) values ('admin@vossie.net');

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text not null unique,
  full_name text not null,
  student_number text not null,
  faculty text not null,
  role text not null default 'learner' check (role in ('learner','tutor','admin')),
  verification_status text not null default 'not_applied' check (verification_status in ('not_applicable','not_applied','pending','verified','rejected')),
  tutor_subject text,
  tutor_bio text,
  tutor_experience text,
  module_codes text,
  rating_average numeric(3,2) not null default 0,
  rating_count integer not null default 0,
  xp integer not null default 0,
  level integer not null default 1,
  streak_days integer not null default 0,
  completed_sessions integer not null default 0,
  total_sessions integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.tutor_applications (
  id uuid primary key default gen_random_uuid(),
  tutor_id uuid not null references public.profiles(id) on delete cascade,
  module_codes text not null,
  evidence_path text not null,
  evidence_url text,
  status text not null default 'pending' check (status in ('pending','verified','rejected')),
  reviewer_id uuid references public.profiles(id),
  reviewer_note text,
  created_at timestamptz not null default now(),
  reviewed_at timestamptz
);

create table if not exists public.sessions (
  id uuid primary key default gen_random_uuid(),
  learner_id uuid not null references public.profiles(id) on delete cascade,
  tutor_id uuid not null references public.profiles(id) on delete cascade,
  subject text not null,
  session_date date not null,
  session_time text not null,
  learner_message text,
  status text not null default 'pending' check (status in ('pending','accepted','rejected','completed','cancelled')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.sessions(id) on delete cascade,
  sender_id uuid not null references public.profiles(id) on delete cascade,
  receiver_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (length(trim(body)) > 0),
  created_at timestamptz not null default now(),
  read_at timestamptz
);

create table if not exists public.reviews (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null unique references public.sessions(id) on delete cascade,
  learner_id uuid not null references public.profiles(id) on delete cascade,
  tutor_id uuid not null references public.profiles(id) on delete cascade,
  rating integer not null check (rating between 1 and 5),
  comment text,
  created_at timestamptz not null default now()
);

create table if not exists public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  title text not null,
  message text not null,
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.resources (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles(id) on delete cascade,
  title text not null,
  description text,
  module_code text,
  file_path text,
  file_url text,
  created_at timestamptz not null default now()
);

-- Automatically create a PeerPath profile whenever Supabase Auth creates a user.
-- Admin status is derived from the server-side allow-list, not from the phone.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
declare
  is_admin boolean;
begin
  if lower(new.email) not like '%@vossie.net' then
    raise exception 'Only Vossie accounts may use PeerPath';
  end if;

  select exists(
    select 1 from public.admin_emails
    where email = lower(new.email)
  ) into is_admin;

  insert into public.profiles (
    id, email, full_name, student_number, faculty, role,
    verification_status, tutor_subject, tutor_bio, tutor_experience, module_codes
  ) values (
    new.id,
    lower(new.email),
    coalesce(new.raw_user_meta_data->>'full_name', 'PeerPath User'),
    coalesce(new.raw_user_meta_data->>'student_number', ''),
    coalesce(new.raw_user_meta_data->>'faculty', ''),
    case when is_admin then 'admin' else coalesce(new.raw_user_meta_data->>'role', 'learner') end,
    case when is_admin then 'verified' else 'not_applied' end,
    new.raw_user_meta_data->>'tutor_subject',
    new.raw_user_meta_data->>'tutor_bio',
    new.raw_user_meta_data->>'tutor_experience',
    new.raw_user_meta_data->>'module_codes'
  );

  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute procedure public.handle_new_user();

-- Helper: current user's role.
create or replace function public.current_user_role()
returns text
language sql
stable
security definer set search_path = public
as $$
  select role from public.profiles where id = auth.uid();
$$;

-- Helper: verified tutor check.
create or replace function public.is_verified_tutor(uid uuid)
returns boolean
language sql
stable
security definer set search_path = public
as $$
  select exists(
    select 1 from public.profiles
    where id = uid and role = 'tutor' and verification_status = 'verified'
  );
$$;

alter table public.admin_emails enable row level security;
alter table public.profiles enable row level security;
alter table public.tutor_applications enable row level security;
alter table public.sessions enable row level security;
alter table public.messages enable row level security;
alter table public.reviews enable row level security;
alter table public.notifications enable row level security;
alter table public.resources enable row level security;

-- Admin email allow-list is server controlled. No client read access.
revoke all on public.admin_emails from anon, authenticated;

-- Profiles: signed-in users can read profiles needed by the app.
create policy profiles_read_allowed on public.profiles
for select to authenticated using (
  id = auth.uid()
  or public.current_user_role() = 'admin'
  or (role = 'tutor' and verification_status = 'verified')
);

-- A user can create/update only their safe profile fields; role and verification
-- changes are deliberately NOT client-writable.
create policy profiles_insert_self on public.profiles
for insert to authenticated
with check (id = auth.uid());

create policy profiles_update_self_safe on public.profiles
for update to authenticated
using (id = auth.uid())
with check (
  id = auth.uid()
  and role = (select role from public.profiles where id = auth.uid())
  and verification_status = (select verification_status from public.profiles where id = auth.uid())
);

-- Admins can manage profiles.
create policy profiles_admin_update on public.profiles
for update to authenticated
using (public.current_user_role() = 'admin')
with check (public.current_user_role() = 'admin');

-- Defense in depth: role and verification status cannot be changed by a normal client.
create or replace function public.protect_profile_authority_fields()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
  if auth.uid() is not null and public.current_user_role() <> 'admin' then
    if new.role is distinct from old.role or new.verification_status is distinct from old.verification_status then
      raise exception 'Role and verification status are managed by PeerPath administrators';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists protect_profile_authority_fields on public.profiles;
create trigger protect_profile_authority_fields
before update on public.profiles
for each row execute procedure public.protect_profile_authority_fields();

-- Tutor applications.
create policy applications_insert_self on public.tutor_applications
for insert to authenticated
with check (tutor_id = auth.uid() and public.current_user_role() = 'tutor');

create policy applications_read_owner_or_admin on public.tutor_applications
for select to authenticated
using (tutor_id = auth.uid() or public.current_user_role() = 'admin');

create policy applications_admin_update on public.tutor_applications
for update to authenticated
using (public.current_user_role() = 'admin')
with check (public.current_user_role() = 'admin');

-- Sessions.
create policy sessions_read_participant on public.sessions
for select to authenticated
using (learner_id = auth.uid() or tutor_id = auth.uid() or public.current_user_role() = 'admin');

create policy sessions_insert_learner on public.sessions
for insert to authenticated
with check (
  learner_id = auth.uid()
  and public.is_verified_tutor(tutor_id)
);

create policy sessions_update_participant on public.sessions
for update to authenticated
using (learner_id = auth.uid() or tutor_id = auth.uid() or public.current_user_role() = 'admin')
with check (learner_id = auth.uid() or tutor_id = auth.uid() or public.current_user_role() = 'admin');

-- Messages: only session participants can read/write.
create policy messages_read_participant on public.messages
for select to authenticated
using (
  sender_id = auth.uid()
  or receiver_id = auth.uid()
  or exists (
    select 1 from public.sessions s
    where s.id = session_id and (s.learner_id = auth.uid() or s.tutor_id = auth.uid())
  )
);

create policy messages_insert_participant on public.messages
for insert to authenticated
with check (
  sender_id = auth.uid()
  and exists (
    select 1 from public.sessions s
    where s.id = session_id
      and s.status = 'accepted'
      and (s.learner_id = auth.uid() or s.tutor_id = auth.uid())
      and receiver_id in (s.learner_id, s.tutor_id)
  )
);

create policy messages_update_receiver on public.messages
for update to authenticated
using (receiver_id = auth.uid())
with check (receiver_id = auth.uid());

-- Reviews.
create policy reviews_read_authenticated on public.reviews
for select to authenticated using (true);

create policy reviews_insert_learner on public.reviews
for insert to authenticated
with check (
  learner_id = auth.uid()
  and exists (
    select 1 from public.sessions s
    where s.id = session_id
      and s.learner_id = auth.uid()
      and s.tutor_id = tutor_id
      and s.status = 'completed'
  )
);

-- Notifications.
create policy notifications_read_self on public.notifications
for select to authenticated using (user_id = auth.uid());
create policy notifications_update_self on public.notifications
for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Resources.
create policy resources_read_authenticated on public.resources
for select to authenticated using (true);
create policy resources_insert_owner on public.resources
for insert to authenticated with check (owner_id = auth.uid());
create policy resources_update_owner_or_admin on public.resources
for update to authenticated
using (owner_id = auth.uid() or public.current_user_role() = 'admin')
with check (owner_id = auth.uid() or public.current_user_role() = 'admin');

-- Grants: least privilege for the mobile client.
grant select, insert, update on public.profiles to authenticated;
grant select, insert, update on public.tutor_applications to authenticated;
grant select, insert, update on public.sessions to authenticated;
grant select, insert, update on public.messages to authenticated;
grant select, insert on public.reviews to authenticated;
grant select, update on public.notifications to authenticated;
grant select, insert, update on public.resources to authenticated;

-- Storage bucket for tutor evidence/resources.
insert into storage.buckets (id, name, public)
values ('peerpath-files', 'peerpath-files', false)
on conflict (id) do nothing;

create policy storage_read_authenticated on storage.objects
for select to authenticated
using (bucket_id = 'peerpath-files');

create policy storage_upload_own_folder on storage.objects
for insert to authenticated
with check (
  bucket_id = 'peerpath-files'
  and (storage.foldername(name))[1] = (auth.uid())::text
);

create policy storage_update_own_folder on storage.objects
for update to authenticated
using (
  bucket_id = 'peerpath-files'
  and (storage.foldername(name))[1] = (auth.uid())::text
)
with check (
  bucket_id = 'peerpath-files'
  and (storage.foldername(name))[1] = (auth.uid())::text
);

create policy storage_admin_read on storage.objects
for select to authenticated
using (
  bucket_id = 'peerpath-files'
  and public.current_user_role() = 'admin'
);
