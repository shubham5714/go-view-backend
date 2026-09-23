package cn.com.v2.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * Billing/account owner.
 */
@TableName("t_account")
@Data
public class Account implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * Owner sys user id.
     */
    private String ownerUserId;

    private String name;

    /**
     * Account status, e.g. ACTIVE, SUSPENDED.
     */
    private String status;

    private String createdTime;
}

