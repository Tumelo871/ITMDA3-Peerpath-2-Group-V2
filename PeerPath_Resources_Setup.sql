-- PeerPath resource repository setup. Run in Supabase SQL Editor.
-- This creates the resources table if it does not exist and applies RLS policies.

create table if not exists public.resources (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    title text not null,
    subject text not null default '',
    url text not null default '',
    description text not null default '',
    created_at timestamptz not null default now()
);

alter table public.resources add column if not exists owner_id uuid references auth.users(id) on delete cascade;
alter table public.resources add column if not exists title text not null default '';
alter table public.resources add column if not exists subject text not null default '';
alter table public.resources add column if not exists url text not null default '';
alter table public.resources add column if not exists description text not null default '';
alter table public.resources add column if not exists created_at timestamptz not null default now();

-- Used by the admin setup to block suspended users from writing resources.
alter table public.profiles add column if not exists is_suspended boolean not null default false;

alter table public.resources enable row level security;

drop policy if exists "Authenticated users can read resources" on public.resources;
create policy "Authenticated users can read resources"
on public.resources for select to authenticated
using (true);

drop policy if exists "Tutors and admins can add resources" on public.resources;
create policy "Tutors and admins can add resources"
on public.resources for insert to authenticated
with check (
    owner_id = (select auth.uid())
    and exists (
        select 1 from public.profiles p
        where p.id = (select auth.uid())
          and lower(p.role::text) in ('tutor', 'admin')
          and coalesce(p.is_suspended, false) = false
    )
);

drop policy if exists "Owners and admins can delete resources" on public.resources;
create policy "Owners and admins can delete resources"
on public.resources for delete to authenticated
using (
    owner_id = (select auth.uid())
    or exists (
        select 1 from public.profiles p
        where p.id = (select auth.uid())
          and lower(p.role::text) = 'admin'
          and coalesce(p.is_suspended, false) = false
    )
);

grant select, insert, delete on public.resources to authenticated;
