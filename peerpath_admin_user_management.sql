-- PeerPath admin user-management RPCs.
-- Run in Supabase SQL Editor. These functions edit/delete profile rows only.
-- Full auth.users deletion requires a trusted server-side Edge Function.

CREATE OR REPLACE FUNCTION public.peerpath_admin_list_users()
RETURNS TABLE (
    id uuid,
    email text,
    full_name text,
    student_number text,
    faculty text,
    role text,
    verification_status text,
    tutor_subject text
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    IF NOT private.is_admin() THEN
        RAISE EXCEPTION 'Administrator access required.';
    END IF;

    RETURN QUERY
    SELECT p.id, p.email, p.full_name, p.student_number,
           p.faculty, p.role, p.verification_status, p.tutor_subject
    FROM public.profiles AS p
    ORDER BY p.full_name ASC NULLS LAST;
END;
$$;

CREATE OR REPLACE FUNCTION public.peerpath_admin_update_user(
    p_user_id uuid,
    p_full_name text,
    p_student_number text,
    p_faculty text,
    p_role text,
    p_verification_status text
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_target_role text;
BEGIN
    IF NOT private.is_admin() THEN
        RAISE EXCEPTION 'Administrator access required.';
    END IF;

    IF p_user_id IS NULL OR p_user_id = auth.uid() THEN
        RAISE EXCEPTION 'You cannot edit your own account through this function.';
    END IF;

    IF p_role IS NULL OR lower(trim(p_role)) NOT IN ('learner', 'tutor') THEN
        RAISE EXCEPTION 'Role must be learner or tutor.';
    END IF;

    IF p_full_name IS NULL OR trim(p_full_name) = '' THEN
        RAISE EXCEPTION 'Full name is required.';
    END IF;

    IF p_verification_status IS NULL OR trim(p_verification_status) = '' THEN
        RAISE EXCEPTION 'Verification status is required.';
    END IF;

    SELECT p.role INTO v_target_role
    FROM public.profiles AS p
    WHERE p.id = p_user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'User profile not found.';
    END IF;

    IF lower(coalesce(v_target_role, '')) = 'admin' THEN
        RAISE EXCEPTION 'Administrator accounts cannot be edited here.';
    END IF;

    UPDATE public.profiles AS p
    SET full_name = trim(p_full_name),
        student_number = nullif(trim(p_student_number), ''),
        faculty = nullif(trim(p_faculty), ''),
        role = lower(trim(p_role)),
        verification_status = trim(p_verification_status)
    WHERE p.id = p_user_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.peerpath_admin_delete_user(p_user_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_target_role text;
BEGIN
    IF NOT private.is_admin() THEN
        RAISE EXCEPTION 'Administrator access required.';
    END IF;

    IF p_user_id IS NULL OR p_user_id = auth.uid() THEN
        RAISE EXCEPTION 'You cannot delete your own account.';
    END IF;

    SELECT p.role INTO v_target_role
    FROM public.profiles AS p
    WHERE p.id = p_user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'User profile not found.';
    END IF;

    IF lower(coalesce(v_target_role, '')) = 'admin' THEN
        RAISE EXCEPTION 'Administrator accounts cannot be deleted here.';
    END IF;

    DELETE FROM public.profiles AS p WHERE p.id = p_user_id;
END;
$$;

REVOKE ALL ON FUNCTION public.peerpath_admin_list_users() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.peerpath_admin_update_user(uuid, text, text, text, text, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.peerpath_admin_delete_user(uuid) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.peerpath_admin_list_users() TO authenticated;
GRANT EXECUTE ON FUNCTION public.peerpath_admin_update_user(uuid, text, text, text, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.peerpath_admin_delete_user(uuid) TO authenticated;
