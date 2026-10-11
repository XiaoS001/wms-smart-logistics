package com.lijunjie.wms.controller;

import com.lijunjie.wms.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统健康检查接口。
 *
 * <p>用于部署后的冒烟测试：一次调用即可确认「应用已启动 + 数据库连通 + 表结构已导入」，
 * 因此在答辩演示前也可以用它快速自检。</p>
 *
 * <p>注意：本类使用 {@link JdbcTemplate} 而非 MyBatis-Plus，目的是让健康检查
 * 不依赖任何 Mapper，即便业务代码尚未完成也能正常返回。</p>
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${spring.application.name:wms}")
    private String appName;

    @Value("${server.port:8080}")
    private String port;

    @Autowired
    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 健康检查。
     *
     * @return 应用信息、数据库版本与已导入的业务表数量
     */
    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("application", appName);
        data.put("port", port);
        data.put("status", "UP");

        try {
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            data.put("databaseVersion", version);
            data.put("databaseConnected", true);

            Integer tableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()",
                    Integer.class);
            data.put("businessTableCount", tableCount);

            Integer locationCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM base_location", Integer.class);
            data.put("locationCount", locationCount);

            Integer productCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM base_product", Integer.class);
            data.put("productCount", productCount);
        } catch (Exception e) {
            log.warn("health check: database access failed: {}", e.getMessage());
            data.put("databaseConnected", false);
            data.put("databaseError", e.getMessage());
        }

        return Result.success("系统运行正常", data);
    }

    /**
     * 最简单的存活探针，不访问数据库。
     */
    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("pong");
    }
}
