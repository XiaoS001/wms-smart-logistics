package com.lijunjie.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijunjie.wms.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限数据访问接口。
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 查询某个角色的全部权限编码。
     *
     * @param roleId 角色 ID
     * @return 权限编码列表
     */
    @Select("SELECT p.perm_code FROM sys_permission p "
            + "INNER JOIN sys_role_permission rp ON rp.perm_id = p.id "
            + "WHERE rp.role_id = #{roleId} ORDER BY p.perm_code")
    List<String> selectPermCodesByRoleId(@Param("roleId") Long roleId);
}
