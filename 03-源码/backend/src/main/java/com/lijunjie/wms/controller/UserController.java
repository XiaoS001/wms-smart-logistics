package com.lijunjie.wms.controller;

import com.lijunjie.wms.common.Result;
import com.lijunjie.wms.entity.SysUser;
import com.lijunjie.wms.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理接口。
 *
 * <p>当前提供查询能力，用于验证权限模块的数据链路；新增、修改、角色分配等写操作
 * 将随"用户与权限管理"模块的完整实现一并补充。</p>
 */
@Tag(name = "用户管理", description = "用户的查询与维护")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final SysUserService sysUserService;

    public UserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /** 查询全部用户（响应中不含密码字段） */
    @Operation(summary = "用户列表", description = "返回全部未删除用户，密码字段不会被查询与返回")
    @GetMapping
    public Result<List<SysUser>> list() {
        return Result.success(sysUserService.listUsers());
    }

    /** 按 ID 查询用户 */
    @Operation(summary = "用户详情", description = "按用户 ID 查询")
    @GetMapping("/{id}")
    public Result<SysUser> detail(@PathVariable("id") Long id) {
        return Result.success(sysUserService.getById(id));
    }
}
