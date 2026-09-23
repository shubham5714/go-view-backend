package cn.com.v2.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@TableName("t_workspace_membership")
@Data
public class WorkspaceMembership implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String workspaceId;

    private String userId;

    /**
     * Role such as OWNER, ADMIN, EDITOR, VIEWER.
     */
    private String role;

    private String createdTime;
}

