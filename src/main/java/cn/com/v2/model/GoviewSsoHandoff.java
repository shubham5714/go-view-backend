package cn.com.v2.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * One-time AI-SOC → GoView SSO handoff code.
 */
@TableName("goview_sso_handoff")
@Data
public class GoviewSsoHandoff implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private String code;

    private String userId;

    private String tenantId;

    private String email;

    private String username;

    private Date expiresAt;

    private Date usedAt;

    private Date createdAt;
}
