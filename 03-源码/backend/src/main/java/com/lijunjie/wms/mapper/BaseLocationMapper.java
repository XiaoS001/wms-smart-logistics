package com.lijunjie.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijunjie.wms.entity.BaseLocation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 货位数据访问接口。
 */
@Mapper
public interface BaseLocationMapper extends BaseMapper<BaseLocation> {

    /**
     * 统计某仓库下各库区的货位数量，用于页面概览。
     *
     * @param warehouseId 仓库 ID
     * @return 每行含 zoneId、locationCount、minDistance、maxDistance
     */
    @Select("SELECT zone_id AS zoneId, COUNT(*) AS locationCount, "
            + "MIN(distance) AS minDistance, MAX(distance) AS maxDistance "
            + "FROM base_location WHERE warehouse_id = #{warehouseId} AND deleted = 0 "
            + "GROUP BY zone_id ORDER BY zone_id")
    List<Map<String, Object>> summaryByZone(@Param("warehouseId") Long warehouseId);

    /**
     * 取某仓库距离出入口最近的若干个空闲货位。
     *
     * <p>这是货位分配算法（频次贪心 / ABC 分区）最典型的查询：按 distance 升序取前 N 条。
     * 表上建有 {@code idx_distance} 索引，该查询可直接命中索引而无需排序整表。</p>
     *
     * @param warehouseId 仓库 ID
     * @param limit       取前多少条
     * @return 货位列表
     */
    @Select("SELECT * FROM base_location "
            + "WHERE warehouse_id = #{warehouseId} AND status = 1 AND deleted = 0 "
            + "ORDER BY distance ASC, id ASC LIMIT #{limit}")
    List<BaseLocation> selectNearestFree(@Param("warehouseId") Long warehouseId,
                                         @Param("limit") int limit);

    /**
     * 查询某仓库已存在的货位坐标，用于批量生成时跳过重复坐标。
     *
     * @param warehouseId 仓库 ID
     * @return 每行含 aisleNo 与 rowNo
     */
    @Select("SELECT aisle_no AS aisleNo, row_no AS rowNo FROM base_location "
            + "WHERE warehouse_id = #{warehouseId} AND deleted = 0")
    List<Map<String, Object>> selectExistingPositions(@Param("warehouseId") Long warehouseId);

    /**
     * 物理删除某仓库的全部货位（仅用于“重新生成”场景；调用方需先确认无库存关联）。
     *
     * @param warehouseId 仓库 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM base_location WHERE warehouse_id = #{warehouseId}")
    int deleteAllByWarehouse(@Param("warehouseId") Long warehouseId);
}
