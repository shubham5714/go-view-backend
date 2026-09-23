package cn.com.v2.service.impl;

import cn.com.v2.model.Subscription;
import cn.com.v2.service.ISubscriptionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.com.v2.mapper.SubscriptionMapper;
import org.springframework.stereotype.Service;

/**
 * Plan/subscription limits are disabled for the AI-SOC tenant model.
 * Methods remain as no-ops so legacy WorkspaceController still compiles.
 */
@Service
public class SubscriptionServiceImpl extends ServiceImpl<SubscriptionMapper, Subscription> implements ISubscriptionService {

    @Override
    public Subscription getActiveSubscription(String accountId) {
        return null;
    }

    @Override
    public void assertCanCreateWorkspace(String accountId) {
        // no plan limits
    }

    @Override
    public void assertCanAddMember(String workspaceId) {
        // no plan limits
    }

    @Override
    public void assertCanCreateProject(String workspaceId) {
        // no plan limits
    }

    @Override
    public boolean isWorkspaceLocked(String workspaceId) {
        return false;
    }

    @Override
    public boolean isProjectLocked(String projectId) {
        return false;
    }
}
