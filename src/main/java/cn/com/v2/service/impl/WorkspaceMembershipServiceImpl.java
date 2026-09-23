package cn.com.v2.service.impl;

import cn.com.v2.mapper.WorkspaceMembershipMapper;
import cn.com.v2.model.WorkspaceMembership;
import cn.com.v2.service.IWorkspaceMembershipService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceMembershipServiceImpl extends ServiceImpl<WorkspaceMembershipMapper, WorkspaceMembership>
        implements IWorkspaceMembershipService {

    @Override
    public void assertMember(String workspaceId, String userId) {
        LambdaQueryWrapper<WorkspaceMembership> wrapper = new LambdaQueryWrapper<WorkspaceMembership>()
                .eq(WorkspaceMembership::getWorkspaceId, workspaceId)
                .eq(WorkspaceMembership::getUserId, userId)
                .last("LIMIT 1");
        WorkspaceMembership membership = getOne(wrapper);
        if (membership == null) {
            throw new RuntimeException("当前用户不在该工作空间中");
        }
    }

    @Override
    public void assertRoleAtLeast(String workspaceId, String userId, String minRole) {
        LambdaQueryWrapper<WorkspaceMembership> wrapper = new LambdaQueryWrapper<WorkspaceMembership>()
                .eq(WorkspaceMembership::getWorkspaceId, workspaceId)
                .eq(WorkspaceMembership::getUserId, userId)
                .last("LIMIT 1");
        WorkspaceMembership membership = getOne(wrapper);
        if (membership == null) {
            throw new RuntimeException("当前用户不在该工作空间中");
        }
        // Simple ordering: OWNER > ADMIN > EDITOR > VIEWER
        int required = roleWeight(minRole);
        int actual = roleWeight(membership.getRole());
        if (actual < required) {
            throw new RuntimeException("当前用户无权限执行该操作");
        }
    }

    private int roleWeight(String role) {
        if ("OWNER".equalsIgnoreCase(role)) return 4;
        if ("ADMIN".equalsIgnoreCase(role)) return 3;
        if ("EDITOR".equalsIgnoreCase(role)) return 2;
        if ("VIEWER".equalsIgnoreCase(role)) return 1;
        return 0;
    }
}

