package cn.com.v2.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@TableName("t_email_verification")
@Data
public class EmailVerification implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String email;

    private String code;

    private String purpose;

    /**
     * Expiration time in ISO-8601 string (e.g. yyyy-MM-dd HH:mm:ss).
     */
    private String expiresAt;

    /**
     * 0 = not used, 1 = used.
     */
    private Integer consumed;

    private String createdTime;
}

