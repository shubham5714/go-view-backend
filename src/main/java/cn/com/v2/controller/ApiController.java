package cn.com.v2.controller;

import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import cn.com.v2.common.base.BaseController;
import cn.com.v2.common.domain.AjaxResult;
import cn.com.v2.model.Account;
import cn.com.v2.model.Subscription;
import cn.com.v2.model.SysUser;
import cn.com.v2.model.Workspace;
import cn.com.v2.model.WorkspaceMembership;
import cn.com.v2.model.dto.MfaVerifyRequest;
import cn.com.v2.model.dto.SignupRequest;
import cn.com.v2.service.IAccountService;
import cn.com.v2.service.ISubscriptionService;
import cn.com.v2.service.ISysUserService;
import cn.com.v2.service.IWorkspaceMembershipService;
import cn.com.v2.service.IWorkspaceService;
import cn.com.v2.service.IEmailVerificationService;
import cn.com.v2.util.SaTokenUtil;
import cn.com.v2.util.TotpUtil;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/goview/sys")
public class ApiController  extends BaseController {
	@Autowired
	private ISysUserService iSysUserService;
	@Autowired
	private IAccountService iAccountService;
	@Autowired
	private ISubscriptionService iSubscriptionService;
	@Autowired
	private IWorkspaceService iWorkspaceService;
	@Autowired
	private IWorkspaceMembershipService iWorkspaceMembershipService;
	@Autowired
	private IEmailVerificationService emailVerificationService;

	@ApiOperation(value = "登陆", notes = "登陆")
	@PostMapping("/login")
	@ResponseBody
	public AjaxResult APIlogin(@RequestBody SysUser user, HttpServletRequest request) {

		// 判断是否登陆
		if (StpUtil.isLogin()) {

			Map<String, Object> map = new HashMap<String, Object>();
			map.put("userinfo", SaTokenUtil.getUser());
			map.put("token", StpUtil.getTokenInfo());
			return success().put("data", map);
		} else {
			if (StrUtil.isNotBlank(user.getUsername()) && StrUtil.isNotBlank(user.getPassword())) {
				SysUser sysUser = iSysUserService.getOne(new LambdaQueryWrapper<SysUser>()
						.eq(SysUser::getUsername, user.getUsername())
						.eq(SysUser::getPassword, SecureUtil.md5(user.getPassword()))
						.last("LIMIT 1"));
				if (sysUser != null) {
					// If user has no MFA secret yet, automatically start enrollment flow
					if (StrUtil.isBlank(sysUser.getMfaSecret())) {
						String secret = TotpUtil.generateSecret();
						sysUser.setMfaSecret(secret);
						sysUser.setMfaEnabled(0);
						iSysUserService.updateById(sysUser);

						String issuer = "GoView";
						String otpauthUrl = String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
								issuer, sysUser.getUsername(), secret, issuer);

						Map<String, Object> map = new HashMap<String, Object>();
						map.put("enrollmentRequired", true);
						map.put("username", sysUser.getUsername());
						map.put("secret", secret);
						map.put("otpauthUrl", otpauthUrl);
						return success().put("data", map);
					}

					// If MFA is already enabled, require verification
					if (sysUser.getMfaEnabled() != null && sysUser.getMfaEnabled() == 1) {
						Map<String, Object> map = new HashMap<String, Object>();
						map.put("mfaRequired", true);
						map.put("username", sysUser.getUsername());
						return success().put("data", map);
					}

					// Secret exists but MFA not yet enabled -> treat as enrollment in-progress
					Map<String, Object> map = new HashMap<String, Object>();
					String issuer = "GoView";
					String otpauthUrl = String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
							issuer, sysUser.getUsername(), sysUser.getMfaSecret(), issuer);
					map.put("enrollmentRequired", true);
					map.put("username", sysUser.getUsername());
					map.put("secret", sysUser.getMfaSecret());
					map.put("otpauthUrl", otpauthUrl);
					return success().put("data", map);
				} else {
					return error(500, "账户或者密码错误");
				}
			} else {
				return error(500, "账户密码不能为空");
			}
		}

	}

	@ApiOperation(value = "MFA 验证", notes = "通过 Google Authenticator 校验 TOTP 后完成登录")
	@PostMapping("/login/mfa-verify")
	@ResponseBody
	public AjaxResult verifyMfa(@RequestBody MfaVerifyRequest body) {
		if (body == null || StrUtil.isBlank(body.getUsername()) || StrUtil.isBlank(body.getCode())) {
			return error(400, "用户名和验证码不能为空");
		}

		SysUser sysUser = iSysUserService.getOne(new LambdaQueryWrapper<SysUser>()
				.eq(SysUser::getUsername, body.getUsername())
				.last("LIMIT 1"));
		if (sysUser == null) {
			return error(400, "用户不存在");
		}
		if (StrUtil.isBlank(sysUser.getMfaSecret())) {
			return error(400, "尚未生成 MFA Secret");
		}

		int code;
		try {
			code = Integer.parseInt(body.getCode());
		} catch (NumberFormatException e) {
			return error(400, "验证码格式不正确");
		}

		boolean ok = TotpUtil.verifyCode(sysUser.getMfaSecret(), code);
		if (!ok) {
			return error(400, "验证码错误");
		}

		// First successful verification automatically enables MFA for the user
		if (sysUser.getMfaEnabled() == null || sysUser.getMfaEnabled() != 1) {
			sysUser.setMfaEnabled(1);
			iSysUserService.updateById(sysUser);
		}

		// MFA passed, perform full login and return same data structure as原来的 /login
		StpUtil.login(sysUser.getId());
		SaTokenUtil.setUser(sysUser);

		Account account = iAccountService.getOrCreateAccountForUser(sysUser.getId());
		Integer workspaceCount = iWorkspaceService.lambdaQuery()
				.eq(Workspace::getAccountId, account.getId())
				.count();
		if (workspaceCount == 0) {
			Workspace workspace = new Workspace();
			workspace.setAccountId(account.getId());
			workspace.setName("Default workspace");
			workspace.setStatus("ACTIVE");
			workspace.setCreatedTime(cn.hutool.core.date.DateUtil.now());
			iWorkspaceService.save(workspace);

			WorkspaceMembership membership = new WorkspaceMembership();
			membership.setWorkspaceId(workspace.getId());
			membership.setUserId(sysUser.getId());
			membership.setRole("OWNER");
			membership.setCreatedTime(cn.hutool.core.date.DateUtil.now());
			iWorkspaceMembershipService.save(membership);
		}

		Map<String, Object> map = new HashMap<String, Object>();
		map.put("userinfo", sysUser);
		map.put("token", StpUtil.getTokenInfo());

		return success().put("data", map);
	}


	@ApiOperation(value = "发送注册邮箱验证码", notes = "为注册发送邮箱验证码")
	@PostMapping("/signup/request-email-code")
	@ResponseBody
	public AjaxResult requestSignupEmailCode(@RequestBody Map<String, String> body) {
		if (body == null || StrUtil.isBlank(body.get("email"))) {
			return error(400, "Email is required");
		}
		String email = body.get("email").trim();
		try {
			emailVerificationService.sendSignupCode(email);
			return success();
		} catch (Exception e) {
			return error(500, "Failed to send verification email");
		}
	}

	@ApiOperation(value = "注册", notes = "注册新用户并为其账号分配 FREE 套餐")
	@PostMapping("/signup")
	@ResponseBody
	public AjaxResult signup(@RequestBody SignupRequest body) {
		if (body == null || StrUtil.isBlank(body.getUsername()) || StrUtil.isBlank(body.getPassword())) {
			return error(400, "Username and password are required");
		}
		if (StrUtil.isBlank(body.getCode())) {
			return error(400, "Verification code is required");
		}

		String username = body.getUsername().trim();

		// Verify email code before creating account
		boolean ok = emailVerificationService.verifySignupCode(username, body.getCode().trim());
		if (!ok) {
			return error(400, "Invalid or expired verification code");
		}

		// Look up existing user by username
		SysUser existing = iSysUserService.getOne(new LambdaQueryWrapper<SysUser>()
				.eq(SysUser::getUsername, username)
				.last("LIMIT 1"));

		SysUser user;
		if (existing == null) {
			// Brand new user: create record
			SysUser newUser = new SysUser();
			newUser.setUsername(username);
			newUser.setPassword(SecureUtil.md5(body.getPassword().trim()));
			newUser.setNickname(body.getNickname());
			newUser.setDepId(0);
			iSysUserService.save(newUser);
			user = newUser;
		} else {
			// Pre-created user (e.g. invited to workspace) completes registration:
			// only allow this path if no password has been set yet
			if (StrUtil.isNotBlank(existing.getPassword())) {
				return error(400, "User already exists");
			}
			existing.setPassword(SecureUtil.md5(body.getPassword().trim()));
			existing.setNickname(body.getNickname());
			if (existing.getDepId() == null) {
				existing.setDepId(0);
			}
			iSysUserService.updateById(existing);
			user = existing;
		}

		// Initialize MFA secret for the user so we can start enrollment immediately
		if (StrUtil.isBlank(user.getMfaSecret())) {
			String secret = TotpUtil.generateSecret();
			user.setMfaSecret(secret);
			user.setMfaEnabled(0);
			iSysUserService.updateById(user);
		}

		// Create or get account for this user
		Account account = iAccountService.getOrCreateAccountForUser(user.getId());

		// If account has no active subscription yet, auto-assign FREE plan
		Subscription active = iSubscriptionService.getActiveSubscription(account.getId());
		if (active == null) {
			Subscription subscription = new Subscription();
			subscription.setAccountId(account.getId());
			// Hard-code FREE plan id so it always exists even if plan table is misconfigured
			subscription.setPlanId("plan_free");
			subscription.setStatus("ACTIVE");
			String now = DateUtil.now();
			subscription.setCurrentPeriodStart(now);
			subscription.setCurrentPeriodEnd(null);
			iSubscriptionService.save(subscription);
		}

		// Build MFA enrollment payload (same shape as /login enrollment response)
		String issuer = "GoView";
		String otpauthUrl = String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
				issuer, user.getUsername(), user.getMfaSecret(), issuer);

		Map<String, Object> map = new HashMap<String, Object>();
		map.put("enrollmentRequired", true);
		map.put("username", user.getUsername());
		map.put("secret", user.getMfaSecret());
		map.put("otpauthUrl", otpauthUrl);

		return success().put("data", map);
	}


	@ApiOperation(value = "登陆", notes = "登陆")
	@GetMapping("/logout")
	@ResponseBody
	public AjaxResult logout() {

		// 判断是否登陆
		StpUtil.logout();

		return success();

	}
	
	
	@ApiOperation(value = "获取oss地址", notes = "获取oss地址")
	@GetMapping("/getOssInfo")
	@ResponseBody
	public AjaxResult getOssInfo() {

		return success();

	}

}

