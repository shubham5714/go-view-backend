package cn.com.v2.service;

import cn.com.v2.model.WorkspaceMembership;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IWorkspaceMembershipService extends IService<WorkspaceMembership> {

    void assertMember(String workspaceId, String userId);

    void assertRoleAtLeast(String workspaceId, String userId, String minRole);
}

