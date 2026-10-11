package com.lijunjie.wms.controller;

import com.lijunjie.wms.common.Result;
import com.lijunjie.wms.common.context.UserContext;
import com.lijunjie.wms.common.exception.BusinessException;
import com.lijunjie.wms.dto.LoginRequest;
import com.lijunjie.wms.dto.LoginResponse;
import com.lijunjie.wms.entity.SysUser;
import com.lijunjie.wms.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口：登录、获取当前用户信息。
 */
@Tag(name = "认证管理", description = "登录与当前用户信息")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysUserService sysUserService;

    public AuthController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /**
     * 登录。
     *
     * @param request 登录名与密码
     * @return 令牌、用户信息、角色与权限
     */
    @Operation(summary = "登录", description = "校验用户名密码，返回 JWT 令牌与权限列表")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success("登录成功", sysUserService.login(request));
    }

    /**
     * 获取当前登录用户信息。
     *
     * <p>前端用它恢复登录态：携带令牌请求，服务端返回用户基本信息、角色与权限。</p>
     *
     * <p>令牌校验与身份装载已由 {@code AuthInterceptor} 完成，这里直接读取
     * {@link UserContext}，不再重复解析令牌，避免两处逻辑不一致。</p>
     *
     * <p>响应必须包含 {@code roles} 与 {@code permissions}：前端的个人信息页
     * 依赖这两个字段展示角色标签与权限清单，缺少时页面会显示为空。</p>
     */
    @Operation(summary = "当前用户信息", description = "返回当前用户的资料、角色与权限清单")
    @GetMapping("/me")
    public Result<Map<String, Object>> currentUser() {

        UserContext.CurrentUser current = UserContext.get();
        if (current == null) {
            throw new BusinessException(401, "未提供访问令牌");
        }
        SysUser user = sysUserService.getById(current.getUserId());
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("phone", user.getPhone());
        data.put("email", user.getEmail());
        data.put("status", user.getStatus());
        data.put("roles", current.getRoles());
        data.put("permissions", new ArrayList<String>(current.getPermissions()));
        return Result.success(data);
    }

    /**
     * 退出登录。
     *
     * <p>JWT 为无状态令牌，服务端不保存会话，因此这里只做形式上的回应；
     * 真正的登出由前端清除本地令牌完成。</p>
     */
    @Operation(summary = "退出登录", description = "前端清除本地令牌即可，服务端为无状态实现")
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success("已退出登录", null);
    }
}
