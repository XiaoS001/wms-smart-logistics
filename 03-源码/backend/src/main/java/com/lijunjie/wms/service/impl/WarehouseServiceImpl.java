package com.lijunjie.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lijunjie.wms.common.exception.BusinessException;
import com.lijunjie.wms.dto.GenerateLocationsRequest;
import com.lijunjie.wms.dto.GenerateLocationsResult;
import com.lijunjie.wms.entity.BaseLocation;
import com.lijunjie.wms.entity.BaseWarehouse;
import com.lijunjie.wms.entity.BaseZone;
import com.lijunjie.wms.mapper.BaseLocationMapper;
import com.lijunjie.wms.mapper.BaseWarehouseMapper;
import com.lijunjie.wms.mapper.BaseZoneMapper;
import com.lijunjie.wms.service.WarehouseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 仓库与库位业务实现。
 *
 * <p>本模块负责维护仓库布局与货位数据。货位坐标与到出入口距离是货位分配算法的输入，
 * 因此批量生成时的距离计算规则必须与算法保持一致（曼哈顿度量）。</p>
 */
@Service
public class WarehouseServiceImpl implements WarehouseService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseServiceImpl.class);

    /** 单次批量插入的分片大小，避免拼接出超长 SQL */
    private static final int BATCH_SIZE = 500;

    /** 默认 A 区距离阈值（米） */
    private static final BigDecimal DEFAULT_ZONE_A = new BigDecimal("15");

    /** 默认 B 区距离阈值（米） */
    private static final BigDecimal DEFAULT_ZONE_B = new BigDecimal("30");

    /** 默认货位容量 */
    private static final BigDecimal DEFAULT_CAPACITY = new BigDecimal("100");

    private final BaseWarehouseMapper warehouseMapper;
    private final BaseZoneMapper zoneMapper;
    private final BaseLocationMapper locationMapper;

    public WarehouseServiceImpl(BaseWarehouseMapper warehouseMapper,
                                BaseZoneMapper zoneMapper,
                                BaseLocationMapper locationMapper) {
        this.warehouseMapper = warehouseMapper;
        this.zoneMapper = zoneMapper;
        this.locationMapper = locationMapper;
    }

    @Override
    public List<BaseWarehouse> listWarehouses(String keyword) {
        LambdaQueryWrapper<BaseWarehouse> q = new LambdaQueryWrapper<BaseWarehouse>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String k = keyword.trim();
            q.and(w -> w.like(BaseWarehouse::getWarehouseCode, k)
                        .or().like(BaseWarehouse::getWarehouseName, k));
        }
        q.orderByAsc(BaseWarehouse::getId);
        return warehouseMapper.selectList(q);
    }

    @Override
    public BaseWarehouse getWarehouse(Long id) {
        return warehouseMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseWarehouse createWarehouse(BaseWarehouse warehouse) {
        validateWarehouse(warehouse, null);
        warehouse.setId(null);
        if (warehouse.getStatus() == null) {
            warehouse.setStatus(1);
        }
        warehouseMapper.insert(warehouse);
        log.info("warehouse created: id={}, code={}", warehouse.getId(), warehouse.getWarehouseCode());
        return warehouseMapper.selectById(warehouse.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseWarehouse updateWarehouse(Long id, BaseWarehouse warehouse) {
        BaseWarehouse existing = warehouseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "仓库不存在");
        }
        validateWarehouse(warehouse, id);

        // 布局参数变更会影响已生成货位的距离，需明确警告
        boolean layoutChanged = !eq(existing.getAisleCount(), warehouse.getAisleCount())
                || !eq(existing.getRowCount(), warehouse.getRowCount())
                || !eq(existing.getAisleGap(), warehouse.getAisleGap())
                || !eq(existing.getRowGap(), warehouse.getRowGap());
        long locationCount = countLocations(id);
        if (layoutChanged && locationCount > 0) {
            log.warn("warehouse {} layout changed while {} locations exist; "
                    + "existing distances are NOT recalculated automatically",
                    id, locationCount);
        }

        warehouse.setId(id);
        warehouseMapper.updateById(warehouse);
        BaseWarehouse updated = warehouseMapper.selectById(id);
        log.info("warehouse updated: id={}", id);
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWarehouse(Long id) {
        BaseWarehouse existing = warehouseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "仓库不存在");
        }
        long locationCount = countLocations(id);
        if (locationCount > 0) {
            throw new BusinessException(409,
                    "该仓库下仍有 " + locationCount + " 个货位，请先删除货位后再删除仓库");
        }
        warehouseMapper.deleteById(id);
        log.info("warehouse deleted: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenerateLocationsResult generateLocations(Long warehouseId, GenerateLocationsRequest request) {
        BaseWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new BusinessException(404, "仓库不存在");
        }
        if (request == null) {
            request = new GenerateLocationsRequest();
        }

        // 1. 解析参数：优先请求值，其次仓库布局参数
        int aisleCount = request.getAisleCount() != null ? request.getAisleCount()
                : (warehouse.getAisleCount() != null ? warehouse.getAisleCount() : 0);
        int rowCount = request.getRowCount() != null ? request.getRowCount()
                : (warehouse.getRowCount() != null ? warehouse.getRowCount() : 0);
        if (aisleCount <= 0 || rowCount <= 0) {
            throw new BusinessException(400, "巷道数与纵深数必须大于 0，请先在仓库上配置布局参数");
        }
        if (aisleCount > 200 || rowCount > 200) {
            throw new BusinessException(400, "巷道数与纵深数均不得超过 200（单次最多生成 40000 个货位）");
        }
        BigDecimal aisleGap = nz(warehouse.getAisleGap(), new BigDecimal("2.50"));
        BigDecimal rowGap = nz(warehouse.getRowGap(), new BigDecimal("1.20"));
        BigDecimal zoneA = nz(request.getAisleDistance(), DEFAULT_ZONE_A);
        BigDecimal zoneB = nz(request.getRowDistance(), DEFAULT_ZONE_B);
        if (zoneA.compareTo(zoneB) >= 0) {
            throw new BusinessException(400, "A 区距离阈值必须小于 B 区距离阈值");
        }
        BigDecimal capacity = nz(request.getCapacity(), DEFAULT_CAPACITY);
        boolean overwrite = Boolean.TRUE.equals(request.getOverwrite());

        GenerateLocationsResult result = new GenerateLocationsResult();
        result.setWarehouseId(warehouseId);

        // 2. 覆盖模式：先清空该仓库的货位
        if (overwrite) {
            int removed = locationMapper.deleteAllByWarehouse(warehouseId);
            result.setRemoved(removed);
            log.info("warehouse {}: {} existing locations removed before regeneration", warehouseId, removed);
        }

        // 3. 确保 A/B/C 三个库区存在，建立“类型 -> 库区 ID”映射
        Map<String, Long> zoneIds = ensureZones(warehouseId);
        Long zoneIdA = zoneIds.get("A");
        Long zoneIdB = zoneIds.get("B");
        Long zoneIdC = zoneIds.get("C");

        // 4. 跳过已存在的坐标（非覆盖模式）
        Set<String> existing = new HashSet<String>();
        if (!overwrite) {
            for (Map<String, Object> row : locationMapper.selectExistingPositions(warehouseId)) {
                existing.add(String.valueOf(row.get("aisleNo")) + "-" + String.valueOf(row.get("rowNo")));
            }
        }

        // 5. 生成并批量插入
        List<BaseLocation> batch = new ArrayList<BaseLocation>(BATCH_SIZE);
        int created = 0;
        int skipped = 0;
        BigDecimal min = null;
        BigDecimal max = null;
        Map<String, Integer> zoneCounts = new LinkedHashMap<String, Integer>();
        zoneCounts.put("A", 0);
        zoneCounts.put("B", 0);
        zoneCounts.put("C", 0);

        for (int x = 1; x <= aisleCount; x++) {
            for (int y = 1; y <= rowCount; y++) {
                if (existing.contains(x + "-" + y)) {
                    skipped++;
                    continue;
                }
                // 曼哈顿距离：横向按巷道间距、纵向按货位间距累加，与算法内核保持一致
                BigDecimal distance = aisleGap.multiply(BigDecimal.valueOf(x))
                        .add(rowGap.multiply(BigDecimal.valueOf(y)))
                        .setScale(2, RoundingMode.HALF_UP);

                String zoneType;
                Long zoneId;
                if (distance.compareTo(zoneA) <= 0) {
                    zoneType = "A"; zoneId = zoneIdA;
                } else if (distance.compareTo(zoneB) <= 0) {
                    zoneType = "B"; zoneId = zoneIdB;
                } else {
                    zoneType = "C"; zoneId = zoneIdC;
                }

                BaseLocation loc = new BaseLocation();
                loc.setWarehouseId(warehouseId);
                loc.setZoneId(zoneId);
                loc.setLocationCode(String.format("A%02d-%02d", x, y));
                loc.setAisleNo(x);
                loc.setRowNo(y);
                loc.setDistance(distance);
                loc.setCapacity(capacity);
                loc.setStatus(1);
                loc.setCreateTime(new Date());
                batch.add(loc);

                zoneCounts.put(zoneType, zoneCounts.get(zoneType) + 1);
                if (min == null || distance.compareTo(min) < 0) { min = distance; }
                if (max == null || distance.compareTo(max) > 0) { max = distance; }

                if (batch.size() >= BATCH_SIZE) {
                    created += insertBatch(batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            created += insertBatch(batch);
            batch.clear();
        }

        result.setCreated(created);
        result.setSkipped(skipped);
        result.setTotal(countLocations(warehouseId));
        result.setZoneCounts(zoneCounts);
        result.setMinDistance(min);
        result.setMaxDistance(max);

        log.info("warehouse {} locations generated: created={}, skipped={}, total={}, zones={}",
                warehouseId, created, skipped, result.getTotal(), zoneCounts);
        return result;
    }

    @Override
    public List<BaseLocation> listLocations(Long warehouseId, Long zoneId, Integer status, int limit) {
        LambdaQueryWrapper<BaseLocation> q = new LambdaQueryWrapper<BaseLocation>();
        if (warehouseId != null) {
            q.eq(BaseLocation::getWarehouseId, warehouseId);
        }
        if (zoneId != null) {
            q.eq(BaseLocation::getZoneId, zoneId);
        }
        if (status != null) {
            q.eq(BaseLocation::getStatus, status);
        }
        q.orderByAsc(BaseLocation::getDistance).orderByAsc(BaseLocation::getId);
        q.last("LIMIT " + Math.max(1, Math.min(limit, 5000)));
        return locationMapper.selectList(q);
    }

    @Override
    public List<Map<String, Object>> locationSummary(Long warehouseId) {
        return locationMapper.summaryByZone(warehouseId);
    }

    @Override
    public List<BaseLocation> nearestFreeLocations(Long warehouseId, int limit) {
        return locationMapper.selectNearestFree(warehouseId, Math.max(1, Math.min(limit, 5000)));
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /** 校验仓库必填字段与编码唯一性 */
    private void validateWarehouse(BaseWarehouse w, Long excludeId) {
        if (w == null || w.getWarehouseCode() == null || w.getWarehouseCode().trim().isEmpty()) {
            throw new BusinessException(400, "仓库编码不能为空");
        }
        if (w.getWarehouseName() == null || w.getWarehouseName().trim().isEmpty()) {
            throw new BusinessException(400, "仓库名称不能为空");
        }
        if (w.getAisleCount() != null && w.getAisleCount() <= 0) {
            throw new BusinessException(400, "巷道数必须大于 0");
        }
        if (w.getRowCount() != null && w.getRowCount() <= 0) {
            throw new BusinessException(400, "纵深数必须大于 0");
        }
        if (w.getAisleGap() != null && w.getAisleGap().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "巷道间距必须大于 0");
        }
        if (w.getRowGap() != null && w.getRowGap().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "货位纵深间距必须大于 0");
        }

        Long dup = warehouseMapper.selectCount(new LambdaQueryWrapper<BaseWarehouse>()
                .eq(BaseWarehouse::getWarehouseCode, w.getWarehouseCode().trim())
                .ne(excludeId != null, BaseWarehouse::getId, excludeId));
        if (dup != null && dup > 0) {
            throw new BusinessException(409, "仓库编码已存在：" + w.getWarehouseCode());
        }
    }

    /** 确保 A/B/C 库区存在，返回“库区类型 -> ID”映射 */
    private Map<String, Long> ensureZones(Long warehouseId) {
        Map<String, Long> map = new LinkedHashMap<String, Long>();
        String[][] defs = {
                {"A", "Z-A", "A 类近端区"},
                {"B", "Z-B", "B 类中区"},
                {"C", "Z-C", "C 类远端区"}
        };
        for (String[] def : defs) {
            String type = def[0];
            BaseZone zone = zoneMapper.selectOne(new LambdaQueryWrapper<BaseZone>()
                    .eq(BaseZone::getWarehouseId, warehouseId)
                    .eq(BaseZone::getZoneType, type)
                    .last("LIMIT 1"));
            if (zone == null) {
                zone = new BaseZone();
                zone.setWarehouseId(warehouseId);
                zone.setZoneCode(def[1]);
                zone.setZoneName(def[2]);
                zone.setZoneType(type);
                zone.setStatus(1);
                zone.setCreateTime(new Date());
                zoneMapper.insert(zone);
                log.info("zone created for warehouse {}: {} ({})", warehouseId, def[1], type);
            }
            map.put(type, zone.getId());
        }
        return map;
    }

    /** 分片批量插入；失败时抛出异常由事务回滚 */
    private int insertBatch(List<BaseLocation> batch) {
        int n = 0;
        for (BaseLocation loc : batch) {
            n += locationMapper.insert(loc);
        }
        return n;
    }

    private long countLocations(Long warehouseId) {
        Long c = locationMapper.selectCount(new LambdaQueryWrapper<BaseLocation>()
                .eq(BaseLocation::getWarehouseId, warehouseId));
        return c == null ? 0L : c;
    }

    private static BigDecimal nz(BigDecimal v, BigDecimal fallback) {
        return v == null ? fallback : v;
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
