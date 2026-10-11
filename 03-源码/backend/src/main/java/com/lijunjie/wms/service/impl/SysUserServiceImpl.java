package com.lijunjie.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lijunjie.wms.common.exception.BusinessException;
import com.lijunjie.wms.common.util.JwtUtil;
import com.lijunjie.wms.common.util.PasswordEncoder;
import com.lijunjie.wms.dto.LoginRequest;
import com.lijunjie.wms.dto.LoginResponse;
import com.lijunjie.wms.entity.SysUser;
import com.lijunjie.wms.mapper.SysUserMapper;
import com.lijunjie.wms.service.SysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 用户与权限业务实现。
 */
@Service
public class SysUserServiceImpl implements SysUserService {

    private static final Logger log = LoggerFactory.getLogger(SysUserServiceImpl.class);

    /** 登录失败时的统一提示，避免暴露"用户名是否存在" */
    private static final String LOGIN_FAILED_MESSAGE = "登录名或密码错误";

    private final SysUserMapper sysUserMapper;

    private final JwtUtil jwtUtil;

    public SysUserServiceImpl(SysUserMapper sysUserMapper, JwtUtil jwtUtil) {
        this.sysUserMapper = sysUserMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String rawPassword = request.getPassword();

        // 1. 按登录名查询。注意：实体上的 password 字段标记了 @TableField(select = false)，
        //    普通查询不会带出密码，因此这里用 select(...) 显式列出需要的列（含 password）。
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .select(SysUser::getId, SysUser::getUsername, SysUser::getPassword,
                        SysUser::getRealName, SysUser::getStatus));

        // 2. 校验用户存在性与密码（两步都返回同一提示）
        if (user == null || !PasswordEncoder.matches(rawPassword, user.getPassword())) {
            log.warn("login failed for username={}", username);
            throw new BusinessException(401, LOGIN_FAILED_MESSAGE);
        }

        // 3. 校验账号状态
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(403, "账号已被停用，请联系管理员");
        }

        // 4. 装配角色与权限
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissions = sysUserMapper.selectPermCodesByUserId(user.getId());

        // 5. 签发令牌
        String token = jwtUtil.generate(user.getId(), user.getUsername(), roles);

        // 6. 记录最后登录时间（失败不影响登录结果）
        try {
            SysUser update = new SysUser();
            update.setId(user.getId());
            update.setLastLoginTime(new Date());
            sysUserMapper.updateById(update);
        } catch (Exception e) {
            log.warn("update last_login_time failed for user {}: {}", user.getId(), e.getMessage());
        }

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpiresInHours(jwtUtil.getExpireHours());
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        response.setRoles(roles);
        response.setPermissions(permissions);

        log.info("login success: username={}, roles={}, permissionCount={}",
                user.getUsername(), roles, permissions == null ? 0 : permissions.size());
        return response;
    }

    @Override
    public List<SysUser> listUsers() {
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .orderByAsc(SysUser::getId));
    }

    @Override
    public SysUser getById(Long id) {
        return sysUserMapper.selectById(id);
    }
}
