package cn.com.v2.controller;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import cn.com.v2.common.base.BaseController;
import cn.com.v2.common.domain.AjaxResult;
import cn.com.v2.mapper.GoviewSsoHandoffMapper;
import cn.com.v2.model.GoviewSsoHandoff;
import cn.com.v2.model.SysUser;
import cn.com.v2.util.SaTokenUtil;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import io.swagger.annotations.ApiOperation;

/**
 * Redeem AI-SOC one-time handoff codes into a Sa-Token session bound to tenant_id.
 */
@RestController
@RequestMapping("/api/goview/sys/sso")
public class SsoController extends BaseController {

	@Autowired
	private GoviewSsoHandoffMapper goviewSsoHandoffMapper;

	@ApiOperation(value = "Exchange SSO handoff code", notes = "AI-SOC → GoView session")
	@PostMapping("/exchange")
	@ResponseBody
	public AjaxResult exchange(@RequestBody Map<String, String> body) {
		String code = body != null ? body.get("code") : null;
		if (StrUtil.isBlank(code)) {
			return error(400, "code is required");
		}

		GoviewSsoHandoff handoff = goviewSsoHandoffMapper.selectById(code.trim());
		if (handoff == null) {
			return error(401, "Invalid or expired handoff code");
		}
		if (handoff.getUsedAt() != null) {
			return error(401, "Handoff code already used");
		}
		if (handoff.getExpiresAt() == null || handoff.getExpiresAt().before(new Date())) {
			return error(401, "Handoff code expired");
		}

		Date now = new Date();
		int updated = goviewSsoHandoffMapper.update(null,
				new LambdaUpdateWrapper<GoviewSsoHandoff>()
						.eq(GoviewSsoHandoff::getCode, handoff.getCode())
						.isNull(GoviewSsoHandoff::getUsedAt)
						.set(GoviewSsoHandoff::getUsedAt, now));
		if (updated != 1) {
			return error(401, "Handoff code already used");
		}

		StpUtil.login(handoff.getUserId());

		SysUser sessionUser = new SysUser();
		sessionUser.setId(handoff.getUserId());
		String username = StrUtil.blankToDefault(handoff.getUsername(),
				StrUtil.blankToDefault(handoff.getEmail(), handoff.getUserId()));
		sessionUser.setUsername(username);
		sessionUser.setNickname(username);
		SaTokenUtil.setUser(sessionUser);
		SaTokenUtil.setTenantId(handoff.getTenantId());
		SaTokenUtil.setEmail(handoff.getEmail());

		Map<String, Object> data = new HashMap<String, Object>();
		data.put("token", StpUtil.getTokenValue());
		data.put("tokenName", StpUtil.getTokenName());
		data.put("tenantId", handoff.getTenantId());
		data.put("userinfo", sessionUser);
		return success().put("data", data);
	}
}
