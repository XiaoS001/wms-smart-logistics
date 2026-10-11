package com.lijunjie.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijunjie.wms.entity.BaseWarehouse;
import org.apache.ibatis.annotations.Mapper;

/**
 * 仓库数据访问接口。
 */
@Mapper
public interface BaseWarehouseMapper extends BaseMapper<BaseWarehouse> {
}
