package com.lijunjie.wms;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 仓储管理系统启动类。
 *
 * <p>课题：面向智慧物流的仓储管理系统设计与实现——以货位分配与拣货路径优化为核心</p>
 *
 * <p>本系统在常规仓储业务功能之上，嵌入两类决策优化能力：</p>
 * <ol>
 *   <li>货位分配优化：依据商品历史出库频次，把高频商品分配到靠近出入口的货位；</li>
 *   <li>拣货路径规划：对给定订单的多个拣货点，规划行走距离最短的拣货顺序。</li>
 * </ol>
 *
 * <p>启动方式：{@code mvn spring-boot:run -Dspring-boot.run.profiles=local}</p>
 */
@SpringBootApplication
@MapperScan("com.lijunjie.wms.mapper")
public class WmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
    }
}
