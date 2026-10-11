package com.lijunjie.wms.common.interceptor;

import com.lijunjie.wms.common.context.UserContext;
import com.lijunjie.wms.common.exception.BusinessException;
import com.lijunjie.wms.common.util.JwtUtil;
import com.lijunjie.wms.entity.SysUser;
import com.lijunjie.wms.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 认证与权限校验拦截器。
 *
 * <p>这是本项目唯一的访问控制入口，承担两件事：</p>
 * <ol>
 *   <li><b>认证</b>：解析 {@code Authorization: Bearer <token>}，
 *       校验令牌有效性与用户状态，把身份写入 {@link UserContext}；</li>
 *   <li><b>授权</b>：按「请求方法与路径」查出所需权限编码，
 *       与用户实际权限比对，缺少权限时返回 403。</li>
 * </ol>
 *
 * <p>白名单只有登录接口与健康检查：登录本身不能要求令牌，
 * 健康检查供启动探活与运维监控使用。</p>
 *
 * <p>注意这里必须真正校验权限。项目早期只在数据库与登录响应中维护了权限数据，
 * 接口侧没有做任何校验，导致任意登录用户都能调用全部接口
 * （例如拣货员可以新增商品），属于越权缺陷。</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    /** 无需登录即可访问的路径 */
    private static final String[] WHITE_LIST = {
            "/api/auth/login",
            "/api/health",
            "/api/health/ping"
    };

    /**
     * 路径与方法到所需权限的映射。
     *
     * <p>顺序有意义：自上而下取第一个匹配项。因此把更具体的路径
     * （如 {@code /plans/preview}）放在更宽泛的路径（如 {@code /plans}）之前，
     * 否则宽泛规则会抢先匹配。</p>
     *
     * <p>约定：查询类需要模块的 {@code :view} 权限，
     * 写操作需要对应的动作权限（{@code :add} / {@code :edit} / {@code :delete} / {@code :run}）。
     * 这样「只读角色」不会因为拥有 {@code :view} 就能执行写操作。</p>
     */
    private static final List<Rule> RULES = new ArrayList<Rule>();

    private static final class Rule {
        final String method;
        final Pattern pattern;
        final String permission;

        Rule(String method, String regex, String permission) {
            this.method = method;
            this.pattern = Pattern.compile(regex);
            this.permission = permission;
        }

        boolean matches(String m, String path) {
            return (method == null || method.equals(m)) && pattern.matcher(path).matches();
        }
    }

    private static void rule(String method, String regex, String permission) {
        RULES.add(new Rule(method, regex, permission));
    }

    static {
        // ---------------- 用户管理 ----------------
        rule("GET", "/api/users(/.*)?", "system:view");

        // ---------------- 仓库与库位 ----------------
        rule("GET", "/api/warehouses(/.*)?", "warehouse:view");
        rule("POST", "/api/warehouses", "warehouse:view");
        rule("PUT", "/api/warehouses/\\d+", "warehouse:view");
        rule("DELETE", "/api/warehouses/\\d+", "warehouse:view");
        rule("POST", "/api/warehouses/\\d+/locations/generate", "location:view");
        rule("GET", "/api/locations(/.*)?", "location:view");

        // ---------------- 商品与分类 ----------------
        // 具体动作放在前面，避免被宽泛的 GET 规则覆盖
        rule("POST", "/api/products/abc-classify", "product:edit");
        rule("POST", "/api/products/\\d+/pick-freq", "product:edit");
        rule("GET", "/api/products/abc-summary", "product:view");
        rule("GET", "/api/products(/.*)?", "product:view");
        rule("POST", "/api/products", "product:add");
        rule("PUT", "/api/products/\\d+", "product:edit");
        rule("DELETE", "/api/products/\\d+", "product:delete");
        rule("GET", "/api/categories(/.*)?", "product:view");
        rule("POST", "/api/categories", "product:add");
        rule("PUT", "/api/categories/\\d+", "product:edit");
        rule("DELETE", "/api/categories/\\d+", "product:delete");

        // ---------------- 入库 ----------------
        rule("GET", "/api/inbound(/.*)?", "inbound:view");
        rule("POST", "/api/inbound/orders", "inbound:add");
        rule("POST", "/api/inbound/orders/\\d+/confirm", "inbound:add");
        rule("POST", "/api/inbound/orders/\\d+/cancel", "inbound:add");

        // ---------------- 出库与拣货 ----------------
        rule("POST", "/api/outbound/orders/\\d+/plan-route", "picking:run");
        rule("GET", "/api/outbound/strategies", "picking:view");
        rule("GET", "/api/outbound(/.*)?", "outbound:view");
        rule("POST", "/api/outbound/orders", "outbound:add");
        rule("POST", "/api/outbound/orders/\\d+/confirm", "outbound:add");
        rule("POST", "/api/outbound/orders/\\d+/cancel", "outbound:add");

        // ---------------- 库存与盘点 ----------------
        rule("POST", "/api/stock/stocktake/generate", "stock:view");
        rule("POST", "/api/stock/stocktake/submit", "stock:view");
        rule("DELETE", "/api/stock/stocktake(/.*)?", "stock:view");
        rule("GET", "/api/stock(/.*)?", "stock:view");

        // ---------------- 货位优化 ----------------
        rule("POST", "/api/slotting/plans/preview", "slotting:run");
        rule("POST", "/api/slotting/plans/\\d+/apply", "slotting:run");
        rule("GET", "/api/slotting/strategies", "slotting:view");
        rule("GET", "/api/slotting/plans(/.*)?", "slotting:view");
        rule("POST", "/api/slotting/plans", "slotting:run");
        rule("DELETE", "/api/slotting/plans/\\d+", "slotting:run");
        rule("POST", "/api/slotting(/.*)?", "slotting:run");

        // ---------------- 报表 ----------------
        rule("GET", "/api/report(/.*)?", "report:view");
    }

    private final JwtUtil jwtUtil;
    private final SysUserMapper sysUserMapper;

    public AuthInterceptor(JwtUtil jwtUtil, SysUserMapper sysUserMapper) {
        this.jwtUtil = jwtUtil;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // 跨域预检请求不带令牌，直接放行
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        for (String allow : WHITE_LIST) {
            if (path.equals(allow) || path.startsWith(allow + "/")) {
                return true;
            }
        }

        String token = extractToken(request.getHeader("Authorization"));
        if (token == null) {
            throw new BusinessException(401, "未提供访问令牌");
        }

        Long userId;
        try {
            Claims claims = jwtUtil.parse(token);
            userId = jwtUtil.getUserId(token);
            if (userId == null && claims != null && claims.get("uid") != null) {
                userId = Long.valueOf(String.valueOf(claims.get("uid")));
            }
        } catch (Exception e) {
            throw new BusinessException(401, "令牌无效或已过期");
        }
        if (userId == null) {
            throw new BusinessException(401, "令牌无效或已过期");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BusinessException(401, "账号已停用");
        }

        // 每次请求都从数据库取权限：角色权限调整后立即生效，无需重新登录
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(userId);
        List<String> permList = sysUserMapper.selectPermCodesByUserId(userId);
        Set<String> permissions = new HashSet<String>(permList == null ? new ArrayList<String>() : permList);
        UserContext.set(new UserContext.CurrentUser(userId, user.getUsername(),
                roles == null ? new ArrayList<String>() : roles, permissions));

        String required = requiredPermission(method, path);
        if (required != null && !permissions.contains(required)) {
            log.warn("permission denied: user={}, {} {}, required={}, owned={}",
                    user.getUsername(), method, path, required, permissions.size());
            throw new BusinessException(403, "缺少访问权限：" + required);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 必须清除，避免线程复用时把上一个请求的身份带进来
        UserContext.clear();
    }

    /** 该请求需要的权限；未在映射表中出现时返回 null 表示不限制 */
    private String requiredPermission(String method, String path) {
        for (Rule r : RULES) {
            if (r.matches(method, path)) {
                return r.permission;
            }
        }
        return null;
    }

    private String extractToken(String authorization) {
        if (authorization == null || authorization.trim().isEmpty()) {
            return null;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            value = value.substring(7).trim();
        }
        return value.isEmpty() ? null : value;
    }
}
