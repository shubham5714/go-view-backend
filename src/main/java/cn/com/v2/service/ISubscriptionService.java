package cn.com.v2.service;

import cn.com.v2.model.Subscription;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ISubscriptionService extends IService<Subscription> {

    Subscription getActiveSubscription(String accountId);

    void assertCanCreateWorkspace(String accountId);

    void assertCanAddMember(String workspaceId);

    void assertCanCreateProject(String workspaceId);

    /**
     * Whether the given workspace should be treated as locked based on
     * its account plan limits and creation order.
     */
    boolean isWorkspaceLocked(String workspaceId);

    /**
     * Whether the given project should be treated as locked based on
     * its workspace plan limits and creation order.
     */
    boolean isProjectLocked(String projectId);
}

