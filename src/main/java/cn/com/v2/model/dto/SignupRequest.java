package cn.com.v2.model.dto;

import lombok.Data;

@Data
public class SignupRequest {

    private String username;

    private String password;

    private String nickname;

    /**
     * Email verification code sent via SendGrid.
     */
    private String code;
}

