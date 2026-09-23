package cn.com.v2.service;

public interface IEmailVerificationService {

    /**
     * Generate and send a signup verification code to the given email.
     */
    void sendSignupCode(String email);

    /**
     * Verify a signup code for the given email. Returns true when valid and marks the code as consumed.
     */
    boolean verifySignupCode(String email, String code);
}

