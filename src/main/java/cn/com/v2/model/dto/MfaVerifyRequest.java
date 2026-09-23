package cn.com.v2.model.dto;

import lombok.Data;

@Data
public class MfaVerifyRequest {

    /**
     * Username (email) used to login.
     */
    private String username;

    /**
     * One-time TOTP code from Google Authenticator.
     */
    private String code;
}

