package com.lijunjie.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijunjie.wms.entity.BaseZone;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 库区数据访问接口。
 */
@Mapper
public interface BaseZoneMapper extends BaseMapper<BaseZone> {

    /**
     * 统计各库区下的货位数量，用于库区列表展示。
     *
     * @param warehouseId 仓库 ID
     * @return 每行含 zoneId 与 locationCount
     */
    @Select("SELECT z.id AS zoneId, COUNT(l.id) AS locationCount "
            + "FROM base_zone z LEFT JOIN base_location l "
            + "ON l.zone_id = z.id AND l.deleted = 0 "
            + "WHERE z.warehouse_id = #{warehouseId} AND z.deleted = 0 "
            + "GROUP BY z.id")
    List<Map<String, Object>> countLocationsByZone(@Param("warehouseId") Long warehouseId);
}
