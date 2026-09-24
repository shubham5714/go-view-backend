package cn.com.v2.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.com.v2.common.base.BaseController;
import cn.com.v2.common.config.V2Config;
import cn.com.v2.common.domain.AjaxResult;
import cn.com.v2.common.domain.ResultTable;
import cn.com.v2.common.domain.Tablepar;
import cn.com.v2.model.GoviewProject;
import cn.com.v2.model.GoviewProjectData;
import cn.com.v2.model.SysFile;
import cn.com.v2.model.vo.GoviewProjectVo;
import cn.com.v2.model.vo.SysFileVo;
import cn.com.v2.service.IGoviewProjectDataService;
import cn.com.v2.service.IGoviewProjectService;
import cn.com.v2.service.ISysFileService;
import cn.com.v2.util.ConvertUtil;
import cn.com.v2.util.SaTokenUtil;
import cn.com.v2.util.SnowflakeIdWorker;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import io.swagger.annotations.ApiOperation;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.ModelMap;

/**
 * Tenant-scoped project APIs. Tenant comes from SSO session, not client input.
 */
@RestController
@RequestMapping("/api/goview/project")
public class GoviewProjectController extends BaseController {
	@Autowired
	private ISysFileService iSysFileService;
	@Autowired
	private V2Config v2Config;
	@Autowired
	private IGoviewProjectService iGoviewProjectService;
	@Autowired
	private IGoviewProjectDataService iGoviewProjectDataService;

	private void assertProjectInTenant(GoviewProject project) {
		if (project == null) {
			throw new RuntimeException("Project not found");
		}
		String tenantId = SaTokenUtil.requireTenantId();
		if (project.getTenantId() == null || !tenantId.equals(project.getTenantId())) {
			throw new RuntimeException("Project not in current tenant");
		}
	}

	@ApiOperation(value = "分页跳转", notes = "分页跳转")
	@GetMapping("/list")
	@ResponseBody
	public ResultTable list(Tablepar tablepar) {
		String tenantId = SaTokenUtil.requireTenantId();
		Page<GoviewProject> page = new Page<GoviewProject>(tablepar.getPage(), tablepar.getLimit());
		LambdaQueryWrapper<GoviewProject> queryWrapper = new LambdaQueryWrapper<GoviewProject>()
				.eq(GoviewProject::getTenantId, tenantId)
				.and(q -> q.isNull(GoviewProject::getIsDelete).or().eq(GoviewProject::getIsDelete, 0))
				.and(q -> q.isNull(GoviewProject::getIsTemplate).or().ne(GoviewProject::getIsTemplate, 1));
		IPage<GoviewProject> iPages = iGoviewProjectService.page(page, queryWrapper);
		ResultTable resultTable = new ResultTable();
		List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
		for (GoviewProject p : iPages.getRecords()) {
			Map<String, Object> item = new HashMap<String, Object>();
			item.put("id", p.getId());
			item.put("projectName", p.getProjectName());
			item.put("state", p.getState());
			item.put("createTime", p.getCreateTime());
			item.put("tenantId", p.getTenantId());
			// Back-compat for Vue clients still reading workspaceId
			item.put("workspaceId", p.getTenantId());
			item.put("createUserId", p.getCreateUserId());
			item.put("isDelete", p.getIsDelete());
			item.put("indexImage", p.getIndexImage());
			item.put("remarks", p.getRemarks());
			item.put("isTemplate", p.getIsTemplate());
			item.put("locked", false);
			data.add(item);
		}
		resultTable.setData(data);
		resultTable.setCode(200);
		resultTable.setCount(iPages.getTotal());
		resultTable.setMsg("获取成功");
		return resultTable;
	}

	@ApiOperation(value = "List template projects", notes = "List all projects marked as templates")
	@GetMapping("/templates")
	@ResponseBody
	public AjaxResult listTemplates() {
		SaTokenUtil.requireTenantId();
		LambdaQueryWrapper<GoviewProject> queryWrapper = new LambdaQueryWrapper<GoviewProject>()
				.eq(GoviewProject::getIsTemplate, 1)
				.and(q -> q.isNull(GoviewProject::getIsDelete).or().eq(GoviewProject::getIsDelete, 0));
		List<GoviewProject> templates = iGoviewProjectService.list(queryWrapper);

		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		for (GoviewProject p : templates) {
			Map<String, Object> item = new HashMap<String, Object>();
			item.put("id", p.getId());
			item.put("name", p.getProjectName());
			item.put("indexImage", p.getIndexImage());
			item.put("remarks", p.getRemarks());
			result.add(item);
		}

		return successData(200, result);
	}

	@ApiOperation(value = "新增", notes = "新增")
	@PostMapping("/create")
	@ResponseBody
	public AjaxResult add(@RequestBody GoviewProject goviewProject) {
		String userId = SaTokenUtil.getUserId();
		String tenantId = SaTokenUtil.requireTenantId();
		goviewProject.setTenantId(tenantId);
		goviewProject.setCreateTime(DateUtil.now());
		goviewProject.setState(-1);
		goviewProject.setCreateUserId(userId);
		if (goviewProject.getIsDelete() == null) {
			goviewProject.setIsDelete(0);
		}
		if (goviewProject.getIsTemplate() == null) {
			goviewProject.setIsTemplate(0);
		}
		boolean b = iGoviewProjectService.save(goviewProject);
		if (b) {
			return successData(200, goviewProject).put("msg", "创建成功");
		}
		return error();
	}

	@ApiOperation(value = "删除", notes = "删除")
	@DeleteMapping("/delete")
	@ResponseBody
	public AjaxResult remove(String ids) {
		List<String> lista = ConvertUtil.toListStrArray(ids);
		for (String id : lista) {
			GoviewProject project = iGoviewProjectService.getById(id);
			assertProjectInTenant(project);
		}
		Boolean b = iGoviewProjectService.removeByIds(lista);
		if (b) {
			return success();
		}
		return error();
	}

	@ApiOperation(value = "修改保存", notes = "修改保存")
	@PostMapping("/edit")
	@ResponseBody
	public AjaxResult editSave(@RequestBody GoviewProject goviewProject) {
		GoviewProject existing = iGoviewProjectService.getById(goviewProject.getId());
		assertProjectInTenant(existing);
		// Never allow client to move project across tenants
		goviewProject.setTenantId(existing.getTenantId());
		Boolean b = iGoviewProjectService.updateById(goviewProject);
		if (b) {
			return success();
		}
		return error();
	}

	@ApiOperation(value = "Create project from template", notes = "Create a new project in current tenant from a template")
	@PostMapping("/templates/{templateId}/create")
	@ResponseBody
	public AjaxResult createFromTemplate(@PathVariable("templateId") String templateId, @RequestBody Map<String, String> body) {
		String projectName = body != null ? body.get("projectName") : null;
		String userId = SaTokenUtil.getUserId();
		String tenantId = SaTokenUtil.requireTenantId();

		GoviewProject template = iGoviewProjectService.getById(templateId);
		if (template == null || template.getIsTemplate() == null || template.getIsTemplate() != 1) {
			return error(404, "Template project not found");
		}

		GoviewProjectData templateData = iGoviewProjectDataService.getProjectid(templateId);

		GoviewProject newProject = new GoviewProject();
		newProject.setProjectName((projectName != null && !projectName.trim().isEmpty()) ? projectName.trim() : template.getProjectName());
		newProject.setTenantId(tenantId);
		newProject.setCreateUserId(userId);
		newProject.setCreateTime(DateUtil.now());
		newProject.setState(-1);
		newProject.setIsDelete(0);
		newProject.setIndexImage(template.getIndexImage());
		newProject.setRemarks(template.getRemarks());
		newProject.setIsTemplate(0);

		boolean saved = iGoviewProjectService.save(newProject);
		if (!saved) {
			return error(500, "Failed to create project from template");
		}

		if (templateData != null) {
			GoviewProjectData newData = new GoviewProjectData();
			newData.setProjectId(newProject.getId());
			newData.setCreateTime(DateUtil.now());
			newData.setCreateUserId(userId);
			newData.setContent(templateData.getContent());
			iGoviewProjectDataService.save(newData);
		}

		return successData(200, newProject);
	}

	@ApiOperation(value = "项目重命名", notes = "项目重命名")
	@PostMapping("/rename")
	@ResponseBody
	public AjaxResult rename(@RequestBody GoviewProject goviewProject) {
		GoviewProject existing = iGoviewProjectService.getById(goviewProject.getId());
		assertProjectInTenant(existing);
		LambdaUpdateWrapper<GoviewProject> updateWrapper = new LambdaUpdateWrapper<GoviewProject>();
		updateWrapper.eq(GoviewProject::getId, goviewProject.getId());
		updateWrapper.set(GoviewProject::getProjectName, goviewProject.getProjectName());
		Boolean b = iGoviewProjectService.update(updateWrapper);
		if (b) {
			return success();
		}
		return error();
	}

	@PutMapping("/publish")
	@ResponseBody
	public AjaxResult updateVisible(@RequestBody GoviewProject goviewProject) {
		if (goviewProject.getState() == -1 || goviewProject.getState() == 1) {
			GoviewProject existing = iGoviewProjectService.getById(goviewProject.getId());
			assertProjectInTenant(existing);
			LambdaUpdateWrapper<GoviewProject> updateWrapper = new LambdaUpdateWrapper<GoviewProject>();
			updateWrapper.eq(GoviewProject::getId, goviewProject.getId());
			updateWrapper.set(GoviewProject::getState, goviewProject.getState());
			Boolean b = iGoviewProjectService.update(updateWrapper);
			if (b) {
				return success();
			}
			return error();
		}
		return error("警告非法字段");
	}

	@ApiOperation(value = "获取项目存储数据", notes = "获取项目存储数据")
	@GetMapping("/getData")
	@ResponseBody
	public AjaxResult getData(String projectId, ModelMap map) {
		GoviewProject goviewProject = iGoviewProjectService.getById(projectId);
		assertProjectInTenant(goviewProject);

		GoviewProjectData blogText = iGoviewProjectDataService.getProjectid(projectId);
		if (blogText != null) {
			GoviewProjectVo goviewProjectVo = new GoviewProjectVo();
			BeanUtils.copyProperties(goviewProject, goviewProjectVo);
			goviewProjectVo.setContent(blogText.getContent());
			return AjaxResult.successData(200, goviewProjectVo).put("msg", "获取成功");
		}
		return AjaxResult.successData(200, null).put("msg", "无数据");
	}

	@ApiOperation(value = "保存项目数据", notes = "保存项目数据")
	@PostMapping("/save/data")
	@ResponseBody
	public AjaxResult saveData(GoviewProjectData data) {
		GoviewProject goviewProject = iGoviewProjectService.getById(data.getProjectId());
		if (goviewProject == null) {
			return error("没有该项目ID");
		}
		assertProjectInTenant(goviewProject);
		GoviewProjectData goviewProjectData = iGoviewProjectDataService.getOne(
				new LambdaQueryWrapper<GoviewProjectData>().eq(GoviewProjectData::getProjectId, goviewProject.getId()));
		if (goviewProjectData != null) {
			data.setId(goviewProjectData.getId());
			iGoviewProjectDataService.updateById(data);
			return success("数据保存成功");
		}
		iGoviewProjectDataService.save(data);
		return success("数据保存成功");
	}

	@PostMapping("/upload")
	public AjaxResult upload(@RequestParam("object") MultipartFile object) throws IOException {
		String tenantId = SaTokenUtil.requireTenantId();
		String userId = SaTokenUtil.getUserId();
		String fileName = object.getOriginalFilename();
		String suffixName = v2Config.getDefaultFormat();
		String mediaKey = "";
		Long filesize = object.getSize();
		String fileSuffixName = "";
		if (fileName != null && fileName.lastIndexOf(".") != -1) {
			suffixName = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
			mediaKey = SnowflakeIdWorker.getUUID();
			fileSuffixName = mediaKey + suffixName;
		} else {
			mediaKey = SnowflakeIdWorker.getUUID();
			fileSuffixName = mediaKey + suffixName;
		}
		String virtualKey = FileController.getFirstNotNull(v2Config.getXnljmap());
		String absolutePath = v2Config.getXnljmap().get(FileController.getFirstNotNull(v2Config.getXnljmap()));
		SysFile sysFile = new SysFile();
		sysFile.setId(SnowflakeIdWorker.getUUID());
		sysFile.setTenantId(tenantId);
		sysFile.setCreateUserId(userId);
		sysFile.setFileName(fileSuffixName);
		sysFile.setFileSize(Integer.parseInt(filesize + ""));
		sysFile.setFileSuffix(suffixName);
		sysFile.setCreateTime(DateUtil.formatLocalDateTime(LocalDateTime.now()));
		String filepath = DateUtil.formatDate(new Date());
		sysFile.setRelativePath(filepath);
		sysFile.setVirtualKey(virtualKey);
		sysFile.setAbsolutePath(absolutePath.replace("file:", ""));
		iSysFileService.saveOrUpdate(sysFile);
		File desc = FileController.getAbsoluteFile(v2Config.getFileurl() + File.separator + filepath, fileSuffixName);
		FileController.writeMultipartFile(object, desc);
		SysFileVo sysFileVo = BeanUtil.copyProperties(sysFile, SysFileVo.class);
		sysFileVo.setFileurl(v2Config.getHttpurl() + sysFile.getVirtualKey() + "/" + sysFile.getRelativePath() + "/" + sysFile.getFileName());
		return successData(200, sysFileVo);
	}
}
