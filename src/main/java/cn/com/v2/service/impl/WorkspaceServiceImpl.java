package cn.com.v2.service.impl;

import cn.com.v2.mapper.WorkspaceMapper;
import cn.com.v2.model.Workspace;
import cn.com.v2.service.IWorkspaceService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceServiceImpl extends ServiceImpl<WorkspaceMapper, Workspace> implements IWorkspaceService {
}

