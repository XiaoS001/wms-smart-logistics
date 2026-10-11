package com.lijunjie.wms.service;

import com.lijunjie.wms.dto.LoginRequest;
import com.lijunjie.wms.dto.LoginResponse;
import com.lijunjie.wms.entity.SysUser;

import java.util.List;

/**
 * 用户与权限业务接口。
 */
public interface SysUserService {

    /**
     * 登录：校验用户名密码，签发 JWT 令牌，并返回角色与权限。
     *
     * @param request 登录参数
     * @return 登录结果（含令牌）
     */
    LoginResponse login(LoginRequest request);

    /**
     * 查询全部启用用户（不含密码字段）。
     *
     * @return 用户列表
     */
    List<SysUser> listUsers();

    /**
     * 按 ID 查询用户。
     *
     * @param id 用户 ID
     * @return 用户；不存在时返回 null
     */
    SysUser getById(Long id);
}
