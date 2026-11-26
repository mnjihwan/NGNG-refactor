package com.ngng.api.user.user.service.signup;

public final class SignupFailure extends RuntimeException {
    private final int status;
    private final String code;
    private final long retryAfter;

    public SignupFailure(int status, String code) { this(status, code, 0); }
    public SignupFailure(int status, String code, long retryAfter) {
        super(code);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
    }
    public int status() { return status; }
    public String code() { return code; }
    public long retryAfter() { return retryAfter; }
    public static SignupFailure required() { return new SignupFailure(400, "SIGNUP_VERIFICATION_REQUIRED"); }
    public static SignupFailure unavailable() { return new SignupFailure(503, "SIGNUP_UNAVAILABLE"); }
}
