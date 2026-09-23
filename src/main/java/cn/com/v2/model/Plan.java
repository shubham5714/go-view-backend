package cn.com.v2.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * Subscription plan definition and limits.
 */
@TableName("t_plan")
@Data
public class Plan implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * Internal code, e.g. FREE, PRO.
     */
    private String planCode;

    private String name;

    private Integer maxWorkspaces;

    private Integer maxUsersPerWorkspace;

    private Integer maxProjectsPerWorkspace;
}

