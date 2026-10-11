package com.lijunjie.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijunjie.wms.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户数据访问接口。
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper} 后自动获得常用 CRUD 能力，
 * 这里额外声明两个业务需要的自定义查询。</p>
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 查询用户的角色编码列表（用于登录后组装角色信息）。
     *
     * @param userId 用户 ID
     * @return 角色编码列表，如 [ADMIN]
     */
    @Select("SELECT r.role_code FROM sys_role r "
            + "INNER JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的权限编码列表（用于前端按钮级权限控制）。
     *
     * <p>三表关联：用户角色 → 角色权限 → 权限定义，并在 SQL 层去重。</p>
     *
     * @param userId 用户 ID
     * @return 权限编码列表，如 [product:view, product:add]
     */
    @Select("SELECT DISTINCT p.perm_code FROM sys_permission p "
            + "INNER JOIN sys_role_permission rp ON rp.perm_id = p.id "
            + "INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id "
            + "WHERE ur.user_id = #{userId} "
            + "ORDER BY p.perm_code")
    List<String> selectPermCodesByUserId(@Param("userId") Long userId);
}
