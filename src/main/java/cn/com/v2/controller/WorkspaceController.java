package cn.com.v2.controller;

import cn.com.v2.common.base.BaseController;
import cn.com.v2.common.domain.AjaxResult;
import cn.com.v2.model.Account;
import cn.com.v2.model.Workspace;
import cn.com.v2.model.WorkspaceMembership;
import cn.com.v2.model.SysUser;
import cn.com.v2.service.IAccountService;
import cn.com.v2.service.ISubscriptionService;
import cn.com.v2.service.IWorkspaceMembershipService;
import cn.com.v2.service.IWorkspaceService;
import cn.com.v2.service.ISysUserService;
import cn.com.v2.util.SaTokenUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.crypto.SecureUtil;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/goview/account")
public class WorkspaceController extends BaseController {

    @Autowired
    private IAccountService accountService;

    @Autowired
    private IWorkspaceService workspaceService;

    @Autowired
    private IWorkspaceMembershipService workspaceMembershipService;

    @Autowired
    private ISubscriptionService subscriptionService;

    @Autowired
    private ISysUserService sysUserService;

    @ApiOperation(value = "获取当前用户的工作空间列表", notes = "获取当前用户的工作空间列表")
    @GetMapping("/workspaces")
    @ResponseBody
    public AjaxResult listWorkspaces() {
        String userId = SaTokenUtil.getUserId();
        List<WorkspaceMembership> memberships = workspaceMembershipService.lambdaQuery()
                .eq(WorkspaceMembership::getUserId, userId)
                .list();
        List<Workspace> workspaces = new ArrayList<Workspace>();
        for (WorkspaceMembership membership : memberships) {
            Workspace ws = workspaceService.getById(membership.getWorkspaceId());
            if (ws != null) {
                workspaces.add(ws);
            }
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Workspace ws : workspaces) {
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("id", ws.getId());
            item.put("name", ws.getName());
            item.put("status", ws.getStatus());
            item.put("accountId", ws.getAccountId());
            item.put("createdTime", ws.getCreatedTime());
            item.put("locked", subscriptionService.isWorkspaceLocked(ws.getId()));
            result.add(item);
        }
        return successData(200, result);
    }

    @ApiOperation(value = "创建工作空间", notes = "创建工作空间")
    @PostMapping("/workspaces")
    @ResponseBody
    public AjaxResult createWorkspace(@RequestBody Workspace workspace) {
        String userId = SaTokenUtil.getUserId();
        Account account = accountService.getOrCreateAccountForUser(userId);
        subscriptionService.assertCanCreateWorkspace(account.getId());

        workspace.setAccountId(account.getId());
        workspace.setStatus("ACTIVE");
        workspace.setCreatedTime(DateUtil.formatLocalDateTime(LocalDateTime.now()));
        workspaceService.save(workspace);

        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setWorkspaceId(workspace.getId());
        membership.setUserId(userId);
        membership.setRole("OWNER");
        membership.setCreatedTime(DateUtil.formatLocalDateTime(LocalDateTime.now()));
        workspaceMembershipService.save(membership);

        return successData(200, workspace);
    }

    @ApiOperation(value = "添加工作空间成员", notes = "添加工作空间成员")
    @PostMapping("/workspaces/{workspaceId}/members")
    @ResponseBody
    public AjaxResult addMember(@PathVariable("workspaceId") String workspaceId, @RequestBody WorkspaceMembership body) {
        String currentUserId = SaTokenUtil.getUserId();
        workspaceMembershipService.assertRoleAtLeast(workspaceId, currentUserId, "ADMIN");

        subscriptionService.assertCanAddMember(workspaceId);

        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setWorkspaceId(workspaceId);
        membership.setUserId(body.getUserId());
        membership.setRole(body.getRole() != null ? body.getRole() : "EDITOR");
        membership.setCreatedTime(DateUtil.formatLocalDateTime(LocalDateTime.now()));
        workspaceMembershipService.save(membership);

        return success();
    }

    @ApiOperation(value = "List workspace members", notes = "Get members for the specified workspace")
    @GetMapping("/workspaces/{workspaceId}/members")
    @ResponseBody
    public AjaxResult listMembers(@PathVariable("workspaceId") String workspaceId) {
        String currentUserId = SaTokenUtil.getUserId();
        // Ensure current user is at least a member of this workspace
        WorkspaceMembership me = workspaceMembershipService.lambdaQuery()
                .eq(WorkspaceMembership::getWorkspaceId, workspaceId)
                .eq(WorkspaceMembership::getUserId, currentUserId)
                .last("LIMIT 1")
                .one();
        if (me == null) {
            return error(403, "You are not a member of this workspace");
        }

        List<WorkspaceMembership> memberships = workspaceMembershipService.lambdaQuery()
                .eq(WorkspaceMembership::getWorkspaceId, workspaceId)
                .list();

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (WorkspaceMembership m : memberships) {
            SysUser user = sysUserService.getById(m.getUserId());
            if (user == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("userId", user.getId());
            item.put("username", user.getUsername());
            item.put("nickname", user.getNickname());
            item.put("role", m.getRole());
            item.put("createdTime", m.getCreatedTime());
            result.add(item);
        }

        return successData(200, result);
    }

    @ApiOperation(value = "Create user and add to workspace", notes = "Create a new user and add to workspace")
    @PostMapping("/workspaces/{workspaceId}/create-user")
    @ResponseBody
    public AjaxResult createUserAndAddMember(@PathVariable("workspaceId") String workspaceId, @RequestBody SysUser body) {
        String currentUserId = SaTokenUtil.getUserId();
        workspaceMembershipService.assertRoleAtLeast(workspaceId, currentUserId, "ADMIN");

        subscriptionService.assertCanAddMember(workspaceId);

        if (body.getUsername() == null || body.getUsername().trim().isEmpty()) {
            return error(400, "Username is required");
        }

        // Check if user already exists
        SysUser existing = sysUserService.lambdaQuery()
                .eq(SysUser::getUsername, body.getUsername().trim())
                .last("LIMIT 1")
                .one();

        SysUser userToUse;
        if (existing != null) {
            userToUse = existing;
        } else {
            SysUser newUser = new SysUser();
            newUser.setUsername(body.getUsername().trim());
            newUser.setNickname(body.getNickname());
            newUser.setDepId(0);
            sysUserService.save(newUser);
            userToUse = newUser;
        }

        // Add membership (idempotent-ish: only create if not already a member)
        WorkspaceMembership existingMembership = workspaceMembershipService.lambdaQuery()
                .eq(WorkspaceMembership::getWorkspaceId, workspaceId)
                .eq(WorkspaceMembership::getUserId, userToUse.getId())
                .last("LIMIT 1")
                .one();
        if (existingMembership == null) {
            WorkspaceMembership membership = new WorkspaceMembership();
            membership.setWorkspaceId(workspaceId);
            membership.setUserId(userToUse.getId());
            membership.setRole("EDITOR");
            membership.setCreatedTime(DateUtil.formatLocalDateTime(LocalDateTime.now()));
            workspaceMembershipService.save(membership);
        }

        return successData(200, userToUse);
    }
}

