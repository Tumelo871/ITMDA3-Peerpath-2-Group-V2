-- PeerPath admin foundation
-- Run in Supabase SQL Editor after confirming these table/column names match your project.
-- This migration does NOT put a service-role key in Android and does NOT disable RLS.

begin;

-- Admin email allowlist; compatible with the existing handle_new_user() function.
insert into public.admin_emails (email)
select lower(v.email)
from (values
  ('EDUV4861632@vossie.net'),
  ('EDUV4941257@vossie.net'),
  ('CON-1595878-M6L0@vossie.net'),
  ('EDUV4773941@vossie.net')
) as v(email)
where not exists (
  select 1 from public.admin_emails a
  where lower(a.email) = lower(v.email)
);

-- Add a suspension flag for safe temporary access removal.
alter table public.profiles
  add column if not exists is_suspended boolean not null default false;

-- Existing Auth accounts for the allowlisted emails become admins.
insert into public.profiles (id, email, full_name, role, verification_status)
select u.id, lower(u.email),
       coalesce(u.raw_user_meta_data ->> 'full_name', ''),
       'admin', 'verified'
from auth.users u
join public.admin_emails a on lower(a.email) = lower(u.email)
where lower(a.email) in (
  'eduv4861632@vossie.net',
  'eduv4941257@vossie.net',
  'con-1595878-m6l0@vossie.net',
  'eduv4773941@vossie.net'
)
on conflict (id) do update
set email = excluded.email,
    role = 'admin',
    verification_status = 'verified';

-- Signup trigger: use signup metadata for learner/tutor, never allow self-selected admin.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path to ''
as $function$
declare
  is_admin boolean;
  selected_role text;
begin
  select exists (
    select 1 from public.admin_emails a
    where lower(a.email) = lower(new.email)
  ) into is_admin;

  selected_role := lower(coalesce(new.raw_user_meta_data ->> 'role', 'learner'));
  if selected_role not in ('learner', 'tutor') then
    selected_role := 'learner';
  end if;

  insert into public.profiles (
    id, email, full_name, role, verification_status, is_suspended
  )
  values (
    new.id,
    lower(new.email),
    coalesce(new.raw_user_meta_data ->> 'full_name', ''),
    case when is_admin then 'admin' else selected_role end,
    case when is_admin then 'verified' else 'not_applied' end,
    false
  )
  on conflict (id) do update
  set email = excluded.email,
      role = case when is_admin then 'admin' else excluded.role end,
      verification_status = case
        when is_admin then 'verified'
        else public.profiles.verification_status
      end;

  return new;
end;
$function$;

-- Shared admin check. A profile role alone is insufficient; email must be allowlisted too.
create or replace function public.peerpath_is_admin()
returns boolean
language sql
stable
security definer
set search_path to ''
as $function$
  select exists (
    select 1
    from auth.users u
    join public.admin_emails a on lower(a.email) = lower(u.email)
    join public.profiles p on p.id = u.id
    where u.id = auth.uid()
      and p.role = 'admin'
      and coalesce(p.is_suspended, false) = false
  );
$function$;

revoke all on function public.peerpath_is_admin() from public;
grant execute on function public.peerpath_is_admin() to authenticated;

-- Admin-only user list. Returns profile fields, not auth secrets.
create or replace function public.peerpath_admin_list_users()
returns table (
  id uuid,
  email text,
  full_name text,
  role text,
  verification_status text,
  is_suspended boolean,
  created_at timestamptz
)
language plpgsql
stable
security definer
set search_path to ''
as $function$
begin
  if not public.peerpath_is_admin() then
    raise exception 'Administrator access required' using errcode = '42501';
  end if;

  return query
    select p.id, p.email, p.full_name, p.role::text,
           p.verification_status::text, coalesce(p.is_suspended, false),
           p.created_at
    from public.profiles p
    order by p.created_at desc;
end;
$function$;

revoke all on function public.peerpath_admin_list_users() from public;
grant execute on function public.peerpath_admin_list_users() to authenticated;

-- Admin-only pending tutor applications, including basic profile details.
-- This avoids requiring the Android app to query tutor_applications directly.
create or replace function public.peerpath_admin_pending_applications()
returns table (
  id uuid,
  tutor_id uuid,
  module_codes text,
  evidence_path text,
  status text,
  created_at timestamptz,
  tutor_name text,
  tutor_email text
)
language plpgsql
stable
security definer
set search_path to ''
as $function$
begin
  if not public.peerpath_is_admin() then
    raise exception 'Administrator access required' using errcode = '42501';
  end if;

  return query
    select ta.id, ta.tutor_id, ta.module_codes, ta.evidence_path,
           ta.status::text, ta.created_at, p.full_name, p.email
    from public.tutor_applications ta
    left join public.profiles p on p.id = ta.tutor_id
    where ta.status = 'pending'
    order by ta.created_at asc;
end;
$function$;

revoke all on function public.peerpath_admin_pending_applications() from public;
grant execute on function public.peerpath_admin_pending_applications() to authenticated;

-- Atomic tutor review: application and profile change together.
-- Application statuses follow the existing Java code: pending, verified, rejected.
create or replace function public.peerpath_admin_review_tutor_application(
  p_application_id uuid,
  p_tutor_id uuid,
  p_approve boolean,
  p_note text default ''
)
returns void
language plpgsql
security definer
set search_path to ''
as $function$
declare
  changed_count integer;
begin
  if not public.peerpath_is_admin() then
    raise exception 'Administrator access required' using errcode = '42501';
  end if;

  update public.tutor_applications
  set status = case when p_approve then 'verified' else 'rejected' end,
      reviewer_id = auth.uid(),
      reviewer_note = coalesce(p_note, '')
  where id = p_application_id
    and tutor_id = p_tutor_id
    and status = 'pending';

  get diagnostics changed_count = row_count;
  if changed_count <> 1 then
    raise exception 'Pending tutor application not found or already reviewed';
  end if;

  update public.profiles
  set role = 'tutor',
      verification_status = case when p_approve then 'verified' else 'rejected' end
  where id = p_tutor_id
    and role <> 'admin';

  if not found then
    raise exception 'Tutor profile not found or cannot be changed';
  end if;
end;
$function$;

revoke all on function public.peerpath_admin_review_tutor_application(uuid, uuid, boolean, text) from public;
grant execute on function public.peerpath_admin_review_tutor_application(uuid, uuid, boolean, text) to authenticated;

-- Admin-only temporary suspension. Does not delete data or Auth identity.
create or replace function public.peerpath_admin_set_suspended(
  p_user_id uuid,
  p_suspended boolean
)
returns void
language plpgsql
security definer
set search_path to ''
as $function$
begin
  if not public.peerpath_is_admin() then
    raise exception 'Administrator access required' using errcode = '42501';
  end if;

  if exists (
    select 1 from public.profiles
    where id = p_user_id and role = 'admin'
  ) then
    raise exception 'Admin accounts cannot be suspended from this screen';
  end if;

  update public.profiles
  set is_suspended = p_suspended
  where id = p_user_id;

  if not found then
    raise exception 'User profile not found';
  end if;
end;
$function$;

revoke all on function public.peerpath_admin_set_suspended(uuid, boolean) from public;
grant execute on function public.peerpath_admin_set_suspended(uuid, boolean) to authenticated;

commit;

-- Verify allowlist and registered profile roles:
select a.email as authorised_email, u.id, p.role, p.verification_status,
       coalesce(p.is_suspended, false) as is_suspended
from public.admin_emails a
left join auth.users u on lower(u.email) = lower(a.email)
left join public.profiles p on p.id = u.id
where lower(a.email) in (
  'eduv4861632@vossie.net',
  'eduv4941257@vossie.net',
  'con-1595878-m6l0@vossie.net',
  'eduv4773941@vossie.net'
)
order by a.email;
