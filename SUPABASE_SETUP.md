# PeerPath Supabase setup

This version moves the **core tutoring workflow** from Firebase to Supabase while keeping the PeerPath UI and `com.example.peerpath` package.

## 1. Create a Supabase project

Create a project in the Supabase Dashboard.

Supabase's Android guidance uses the project URL and publishable key with the Data API; enable Row Level Security for exposed tables. See the official docs: https://supabase.com/docs/guides/getting-started/quickstarts/kotlin

## 2. Run the database schema

Open **SQL Editor** in Supabase and run the complete:

`supabase_schema.sql`

Do this before running the Android app.

## 3. Add the administrator email allow-list

The Android app **does not decide who is an admin**.

The database does.

In the SQL Editor, add the exact Vossie addresses that must be administrators:

```sql
insert into public.admin_emails(email)
values
  ('your.first.admin@vossie.net'),
  ('your.second.admin@vossie.net');
```

Replace those examples with the real addresses.

When one of those addresses registers, the database trigger assigns:

`role = 'admin'`

and the Android app cannot promote itself to admin.

## 4. Configure the Android app

Open:

`app/src/main/java/com/example/peerpath/SupabaseConfig.java`

and replace:

```java
public static final String URL = "https://YOUR_PROJECT_REF.supabase.co";
public static final String PUBLISHABLE_KEY = "YOUR_SUPABASE_PUBLISHABLE_KEY";
```

with the values from **Supabase Dashboard → Connect / API**.

**Never put the `service_role`/secret key in the Android application.**

## 5. Authentication

PeerPath only accepts addresses ending in:

`@vossie.net`

The app checks this before sign-up/sign-in, and the Supabase database trigger checks it again server-side. Therefore the domain restriction is not just a UI rule.

Supabase email/password authentication is used; Google, Apple and Facebook are not used.

If **Confirm email** is enabled, users must verify their Vossie email before they can sign in. Supabase documents this behavior here:
https://supabase.com/docs/reference/kotlin/auth-signup

## 6. Role model

There are exactly three application roles:

- `learner`
- `tutor`
- `admin`

A tutor is not discoverable until:

`role = tutor` AND `verification_status = verified`

A learner can request a session only with a verified tutor.

A tutor can accept/reject their own requests.

An administrator can review tutor applications and verify/reject tutors.

## 7. Main live workflow

```text
Vossie registration
      ↓
Supabase Auth
      ↓
profiles row created by database trigger
      ↓
Learner dashboard
      ↓
Find Tutor
      ↓
Verified tutors from Supabase
      ↓
Tutor profile
      ↓
Request session
      ↓
Tutor accepts
      ↓
Messages
      ↓
Chat stored in Supabase
```

Tutor verification:

```text
Tutor registration
      ↓
Become a Tutor
      ↓
Upload evidence to Supabase Storage
      ↓
tutor_applications = pending
      ↓
Admin dashboard
      ↓
Verify / Reject
      ↓
profiles.verification_status
      ↓
Find Tutor
```

## 8. Important security rule

Do not solve admin authorization by hardcoding admin emails only in Java. The exact allow-list belongs in the database so a modified APK cannot simply change `role` to `admin`.

Supabase RLS policies and grants protect database rows. See:
https://supabase.com/docs/guides/database/postgres/row-level-security

## 9. Current implementation note

The core authentication, dashboard, tutor discovery, session requests, tutor requests, sessions, messages/chat, tutor applications and admin verification paths have been switched to the Supabase REST API.

Some legacy secondary screens from the original Firebase project remain in the source tree for UI compatibility and can be migrated next (resources/materials/leaderboard/notifications/profile editing). They should not be considered the final production backend until their Supabase implementations are enabled.
