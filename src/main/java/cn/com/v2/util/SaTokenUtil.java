package cn.com.v2.util;

import cn.com.v2.model.SysUser;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.BeanUtils;

/**
 * Sa-Token helpers plus tenant context for AI-SOC SSO sessions.
 */
public class SaTokenUtil {

    private static final String SESSION_USER = "user";
    private static final String SESSION_TENANT_ID = "tenantId";
    private static final String SESSION_EMAIL = "email";

    public static SysUser getUser() {
        Object object = StpUtil.getSession().get(SESSION_USER);
        if (object != null) {
            SysUser tsysUser = new SysUser();
            BeanUtils.copyProperties(object, tsysUser);
            return tsysUser;
        }
        return null;
    }

    public static void setUser(SysUser user) {
        StpUtil.getSession().set(SESSION_USER, user);
    }

    public static void setTenantId(String tenantId) {
        StpUtil.getSession().set(SESSION_TENANT_ID, tenantId);
    }

    public static String getTenantId() {
        Object tenantId = StpUtil.getSession().get(SESSION_TENANT_ID);
        return tenantId == null ? null : String.valueOf(tenantId);
    }

    public static void setEmail(String email) {
        StpUtil.getSession().set(SESSION_EMAIL, email);
    }

    public static String getEmail() {
        Object email = StpUtil.getSession().get(SESSION_EMAIL);
        return email == null ? null : String.valueOf(email);
    }

    /**
     * Require a logged-in session with a bound tenant (SSO exchange).
     */
    public static String requireTenantId() {
        String tenantId = getTenantId();
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new RuntimeException("Tenant context missing. Open GoView from AI-SOC.");
        }
        return tenantId;
    }

    public static String getUserId() {
        return StpUtil.getLoginIdAsString();
    }

    public static String getLoginName() {
        SysUser tsysUser = getUser();
        if (tsysUser == null) {
            throw new RuntimeException("用户不存在！");
        }
        return tsysUser.getUsername();
    }

    public static String getIp() {
        return StpUtil.getTokenSession().getString("login_ip");
    }

    public static boolean isLogin() {
        return StpUtil.isLogin();
    }
}
