package com.example.peerpath;

/** App-wide switches and account policy. */
public final class Config {

    /** PeerPath is strictly for Vossie accounts. */
    public static final boolean REQUIRE_CAMPUS_EMAIL = true;
    public static final String CAMPUS_EMAIL_DOMAIN = "@vossie.net";

    /**
     * Validates the institutional email used by PeerPath.
     * Example: eduv45738339@vossie.net
     */
    public static boolean isVossieEmail(String email) {
        if (email == null) return false;
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.endsWith(CAMPUS_EMAIL_DOMAIN)
                && android.util.Patterns.EMAIL_ADDRESS.matcher(normalized).matches();
    }

    /** Sessions cannot be completed before their scheduled start time. */
    public static final boolean ENFORCE_SESSION_TIME = true;

    private Config() {
    }
}
