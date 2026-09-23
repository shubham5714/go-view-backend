package cn.com.v2.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 
 * </p>
 *
 * @author fc
 * @since 2023-04-30
 */
@TableName("t_sys_user")
@Data
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String username;

    private String password;

    private String nickname;

    private Integer depId;

    private String posId;

    /**
     * Whether MFA (TOTP) is enabled for this user (0/1 in DB).
     */
    private Integer mfaEnabled;

    /**
     * Base32-encoded TOTP secret used by Google Authenticator.
     */
    private String mfaSecret;
}
