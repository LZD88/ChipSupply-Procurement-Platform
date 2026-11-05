-- 芯链教学系统：业务库结构。唯一运行时 schema，生成导入文件时复用。
-- 1. 用户/员工表 (user)
-- 注意：user是MySQL关键字，需要用反引号括起来
CREATE TABLE IF NOT EXISTS `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码(加密)',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `role` varchar(50) DEFAULT 'purchase' COMMENT '角色(admin-管理员, purchase-采购员, warehouse-仓管员, sales-销售员)',
  `department` varchar(50) DEFAULT '采购部' COMMENT '部门(采购部/仓储部/销售部/财务部)',
  `phone` varchar(20) DEFAULT NULL COMMENT '电话',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `status` tinyint DEFAULT '1' COMMENT '状态(1-正常, 2-禁用)',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标志(0-未删除, 1-已删除)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_role` (`role`),
  KEY `idx_department` (`department`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户/员工表';

-- 2. 供应商表 (supplier)
CREATE TABLE IF NOT EXISTS `supplier` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '供应商ID',
  `supplier_code` varchar(50) NOT NULL COMMENT '供应商编码',
  `name` varchar(100) NOT NULL COMMENT '供应商名称',
  `contact_person` varchar(50) DEFAULT NULL COMMENT '联系人',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `address` varchar(200) DEFAULT NULL COMMENT '地址',
  `credit_rating` char(1) DEFAULT 'B' COMMENT '信用评级(A-优秀, B-良好, C-一般, D-较差)',
  `status` tinyint DEFAULT '1' COMMENT '合作状态(1-合作中, 2-已终止, 3-审核中)',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标志(0-未删除, 1-已删除)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supplier_code` (`supplier_code`),
  KEY `idx_status` (`status`),
  KEY `idx_credit_rating` (`credit_rating`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商表';

-- 3. 产品/芯片物料表 (part)
CREATE TABLE IF NOT EXISTS `part` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '芯片物料ID',
  `part_code` varchar(50) NOT NULL COMMENT '物料编码',
  `name` varchar(100) NOT NULL COMMENT '物料名称',
  `model` varchar(100) DEFAULT NULL COMMENT '型号',
  `manufacturer` varchar(100) DEFAULT NULL COMMENT '制造商（教学虚构）',
  `manufacturer_part_number` varchar(100) DEFAULT NULL COMMENT '制造商料号 MPN',
  `package_type` varchar(50) DEFAULT NULL COMMENT '封装',
  `temperature_grade` varchar(50) DEFAULT NULL COMMENT '温度等级',
  `min_order_quantity` int NOT NULL DEFAULT 1 COMMENT '最小起订量（片）',
  `lead_time_days` int NOT NULL DEFAULT 7 COMMENT '标准交期（天）',
  `specification` varchar(200) DEFAULT NULL COMMENT '规格',
  `unit` varchar(20) DEFAULT '片' COMMENT '单位（片）',
  `purchase_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '采购单价',
  `suggested_retail_price` decimal(10,2) DEFAULT '0.00' COMMENT '建议零售价',
  `stock_warning_value` int DEFAULT '10' COMMENT '库存预警值',
  `supplier_id` bigint DEFAULT NULL COMMENT '供应商ID',
  `category` varchar(50) DEFAULT NULL COMMENT '分类(微控制器/存储芯片/电源管理/模拟芯片/接口芯片/传感芯片)',
  `description` text COMMENT '物料描述',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标志(0-未删除, 1-已删除)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_part_code` (`part_code`),
  KEY `idx_supplier_id` (`supplier_id`),
  KEY `idx_category` (`category`),
  KEY `idx_create_time` (`create_time`),
  CONSTRAINT `fk_part_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品/芯片物料表';

-- 4. 采购订单表 (purchase_order)
CREATE TABLE IF NOT EXISTS `purchase_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_number` varchar(50) NOT NULL COMMENT '订单编号',
  `total_amount` decimal(12,2) DEFAULT '0.00' COMMENT '订单总金额',
  `status` tinyint DEFAULT '1' COMMENT '订单状态(1-待审核, 2-已审核, 3-采购中, 4-已入库, 5-已取消)',
  `order_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `expected_delivery_date` date DEFAULT NULL COMMENT '预计交货日期',
  `actual_delivery_date` date DEFAULT NULL COMMENT '实际交货日期',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标志(0-未删除, 1-已删除)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_number` (`order_number`),
  KEY `idx_status` (`status`),
  KEY `idx_order_time` (`order_time`),
  KEY `idx_created_by` (`created_by`),
  CONSTRAINT `fk_order_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购订单表';

-- 5. 订单明细表 (order_detail)
CREATE TABLE IF NOT EXISTS `order_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `part_id` bigint NOT NULL COMMENT '芯片物料ID',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '采购数量',
  `unit_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '采购单价',
  `subtotal` decimal(12,2) GENERATED ALWAYS AS (quantity * unit_price) STORED COMMENT '小计金额',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标志(0-未删除, 1-已删除)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_part_id` (`part_id`),
  UNIQUE KEY `uk_order_part` (`order_id`, `part_id`),
  CONSTRAINT `fk_detail_order` FOREIGN KEY (`order_id`) REFERENCES `purchase_order` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_detail_part` FOREIGN KEY (`part_id`) REFERENCES `part` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单明细表';

-- 6. 库存表 (inventory)
CREATE TABLE IF NOT EXISTS `inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '库存ID',
  `part_id` bigint NOT NULL COMMENT '芯片物料ID',
  `current_quantity` int NOT NULL DEFAULT '0' COMMENT '当前库存数量',
  `safety_stock` int DEFAULT '10' COMMENT '安全库存量',
  `last_inbound_time` datetime DEFAULT NULL COMMENT '最近入库时间',
  `last_outbound_time` datetime DEFAULT NULL COMMENT '最近出库时间',
  `warehouse_location` varchar(100) DEFAULT 'ESD-A / 待分配库位' COMMENT '仓库位置',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标志(0-未删除, 1-已删除)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_part_id` (`part_id`),
  KEY `idx_current_quantity` (`current_quantity`),
  KEY `idx_warehouse_location` (`warehouse_location`),
  CONSTRAINT `fk_inventory_part` FOREIGN KEY (`part_id`) REFERENCES `part` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存表';

-- 7. 客户表 (customer)
CREATE TABLE IF NOT EXISTS `customer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '客户ID',
  `customer_code` varchar(50) NOT NULL COMMENT '客户编码',
  `name` varchar(100) NOT NULL COMMENT '客户名称',
  `contact_person` varchar(50) DEFAULT NULL COMMENT '联系人',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `address` varchar(200) DEFAULT NULL COMMENT '地址',
  `customer_type` tinyint DEFAULT '1' COMMENT '客户类型(1-方案商, 2-电子制造商, 3-教学实验室)',
  `discount_level` tinyint DEFAULT '1' COMMENT '折扣等级(1-无折扣, 2-银牌, 3-金牌, 4-钻石)',
  `registered_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标志(0-未删除, 1-已删除)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_code` (`customer_code`),
  KEY `idx_customer_type` (`customer_type`),
  KEY `idx_discount_level` (`discount_level`),
  KEY `idx_registered_time` (`registered_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户表';

-- 8. 物流信息表 (logistics)
CREATE TABLE IF NOT EXISTS `logistics` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物流ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `logistics_company` varchar(100) DEFAULT NULL COMMENT '物流公司',
  `tracking_number` varchar(100) DEFAULT NULL COMMENT '运单号',
  `ship_time` datetime DEFAULT NULL COMMENT '发货时间',
  `estimated_arrival_time` datetime DEFAULT NULL COMMENT '预计到达时间',
  `actual_arrival_time` datetime DEFAULT NULL COMMENT '实际到达时间',
  `status` tinyint DEFAULT '1' COMMENT '物流状态(1-待发货, 2-运输中, 3-已签收, 4-异常)',
  `receiver` varchar(100) DEFAULT NULL COMMENT '收货人',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标志(0-未删除, 1-已删除)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_tracking_number` (`tracking_number`),
  KEY `idx_status` (`status`),
  KEY `idx_ship_time` (`ship_time`),
  CONSTRAINT `fk_logistics_order` FOREIGN KEY (`order_id`) REFERENCES `purchase_order` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流信息表';

-- ==================== 视图定义 ====================

-- 库存预警视图
CREATE OR REPLACE VIEW `inventory_warning_view` AS
SELECT
    i.id,
    i.part_id,
    p.part_code,
    p.name AS part_name,
    i.current_quantity,
    i.safety_stock,
    p.stock_warning_value,
    i.warehouse_location,
    CASE
        WHEN i.current_quantity <= p.stock_warning_value THEN '严重不足'
        WHEN i.current_quantity <= i.safety_stock THEN '不足'
        ELSE '充足'
    END AS stock_status,
    i.last_inbound_time,
    i.last_outbound_time
FROM inventory i
JOIN part p ON i.part_id = p.id
WHERE i.deleted = 0 AND p.deleted = 0;

-- 订单统计视图
CREATE OR REPLACE VIEW `order_statistics_view` AS
SELECT
    DATE(o.order_time) AS order_date,
    COUNT(*) AS order_count,
    SUM(o.total_amount) AS total_amount,
    AVG(o.total_amount) AS avg_amount
FROM purchase_order o
WHERE o.deleted = 0
GROUP BY DATE(o.order_time);

-- 订单明细视图（含芯片物料和供应商信息）
CREATE OR REPLACE VIEW `order_detail_full_view` AS
SELECT
    od.id AS detail_id,
    od.order_id,
    o.order_number,
    od.part_id,
    p.part_code,
    p.name AS part_name,
    p.model,
    p.specification,
    od.quantity,
    od.unit_price,
    od.subtotal,
    p.supplier_id,
    s.name AS supplier_name,
    s.supplier_code,
    od.create_time AS detail_create_time
FROM order_detail od
JOIN purchase_order o ON od.order_id = o.id
JOIN part p ON od.part_id = p.id
LEFT JOIN supplier s ON p.supplier_id = s.id
WHERE od.deleted = 0 AND o.deleted = 0 AND p.deleted = 0;


CREATE TABLE IF NOT EXISTS chip_dataset (version varchar(50) PRIMARY KEY, seeded_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
