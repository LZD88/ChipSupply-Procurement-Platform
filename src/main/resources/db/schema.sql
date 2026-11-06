-- 芯链采购协同平台：业务库结构。唯一运行时 schema，生成导入文件时复用。
-- 1. 用户/员工表 (user)
-- 注意：user是MySQL关键字，需要用反引号括起来
CREATE TABLE IF NOT EXISTS `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码(加密)',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `role` varchar(50) DEFAULT 'purchase' COMMENT '角色(admin-管理员, purchase-采购员, warehouse-仓管员, sales-销售员, auditor-审计员, agent-采购智能体)',
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
  `manufacturer` varchar(100) DEFAULT NULL COMMENT '制造商',
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
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标志(0-未删除, 1-已删除)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_part_id` (`part_id`),
  KEY `idx_current_quantity` (`current_quantity`),
  KEY `idx_warehouse_location` (`warehouse_location`),
  CONSTRAINT `fk_inventory_part` FOREIGN KEY (`part_id`) REFERENCES `part` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存表';

-- 7. 安全与业务审计日志（只允许追加和查询，不提供修改/删除接口）
CREATE TABLE IF NOT EXISTS `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '审计ID',
  `actor_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `actor_username` varchar(50) DEFAULT NULL COMMENT '操作账号',
  `actor_role` varchar(50) DEFAULT NULL COMMENT '操作角色',
  `action` varchar(80) NOT NULL COMMENT '操作动作',
  `resource_type` varchar(80) NOT NULL COMMENT '资源类型',
  `resource_id` varchar(100) DEFAULT NULL COMMENT '资源标识',
  `http_method` varchar(10) DEFAULT NULL COMMENT 'HTTP方法',
  `request_path` varchar(500) DEFAULT NULL COMMENT '请求路径',
  `request_id` varchar(64) DEFAULT NULL COMMENT '请求追踪ID',
  `result` varchar(20) NOT NULL COMMENT 'SUCCESS/FAILURE',
  `detail_json` text COMMENT '脱敏后的操作摘要',
  `ip_address` varchar(64) DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '客户端标识',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_actor` (`actor_username`),
  KEY `idx_audit_resource` (`resource_type`, `resource_id`),
  KEY `idx_audit_result` (`result`),
  KEY `idx_audit_created_at` (`created_at`),
  CONSTRAINT `fk_audit_actor` FOREIGN KEY (`actor_id`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全与业务审计日志';

-- 8. 客户表 (customer)
CREATE TABLE IF NOT EXISTS `customer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '客户ID',
  `customer_code` varchar(50) NOT NULL COMMENT '客户编码',
  `name` varchar(100) NOT NULL COMMENT '客户名称',
  `contact_person` varchar(50) DEFAULT NULL COMMENT '联系人',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `address` varchar(200) DEFAULT NULL COMMENT '地址',
  `customer_type` tinyint DEFAULT '1' COMMENT '客户类型(1-方案商, 2-电子制造商, 3-研发机构)',
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

-- 9. 物流信息表 (logistics)
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

-- 10. 收货单：三单匹配中的实收依据
CREATE TABLE IF NOT EXISTS `goods_receipt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '收货单ID',
  `receipt_number` varchar(50) NOT NULL COMMENT '收货单编号',
  `order_id` bigint NOT NULL COMMENT '采购订单ID',
  `received_quantity` int NOT NULL COMMENT '实收数量',
  `received_amount` decimal(12,2) NOT NULL COMMENT '实收金额',
  `received_at` datetime NOT NULL COMMENT '收货时间',
  `warehouse_location` varchar(100) DEFAULT NULL COMMENT '收货库位',
  `receiver` varchar(100) DEFAULT NULL COMMENT '收货人',
  `status` varchar(20) NOT NULL DEFAULT 'CONFIRMED' COMMENT 'DRAFT/CONFIRMED',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_receipt_number` (`receipt_number`),
  UNIQUE KEY `uk_receipt_order` (`order_id`),
  CONSTRAINT `fk_receipt_order` FOREIGN KEY (`order_id`) REFERENCES `purchase_order` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购收货单';

-- 11. 供应商发票：三单匹配中的应付依据
CREATE TABLE IF NOT EXISTS `supplier_invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '发票ID',
  `invoice_number` varchar(50) NOT NULL COMMENT '发票号码',
  `order_id` bigint NOT NULL COMMENT '采购订单ID',
  `supplier_id` bigint DEFAULT NULL COMMENT '供应商ID',
  `invoice_quantity` int NOT NULL COMMENT '开票数量',
  `invoice_amount` decimal(12,2) NOT NULL COMMENT '含税发票金额',
  `invoice_date` date NOT NULL COMMENT '开票日期',
  `tax_rate` decimal(5,2) NOT NULL DEFAULT 13.00 COMMENT '税率百分比',
  `status` varchar(20) NOT NULL DEFAULT 'RECEIVED' COMMENT 'RECEIVED/VERIFIED/REJECTED',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_number` (`invoice_number`),
  UNIQUE KEY `uk_invoice_order` (`order_id`),
  KEY `idx_invoice_supplier` (`supplier_id`),
  CONSTRAINT `fk_invoice_order` FOREIGN KEY (`order_id`) REFERENCES `purchase_order` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_invoice_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商发票';

-- 12. 业务事件流：同一 trace_id 串联请求、领域事件和资源变化
CREATE TABLE IF NOT EXISTS `business_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '事件ID',
  `event_id` varchar(64) NOT NULL COMMENT '全局事件ID',
  `trace_id` varchar(64) NOT NULL COMMENT '端到端追踪ID',
  `event_type` varchar(80) NOT NULL COMMENT '事件类型',
  `aggregate_type` varchar(50) NOT NULL COMMENT '聚合类型',
  `aggregate_id` varchar(100) NOT NULL COMMENT '聚合ID',
  `order_id` bigint DEFAULT NULL COMMENT '关联采购订单',
  `actor` varchar(100) DEFAULT NULL COMMENT '事件发起者',
  `source` varchar(100) NOT NULL COMMENT '事件来源',
  `status` varchar(20) NOT NULL DEFAULT 'COMPLETED' COMMENT 'PENDING/COMPLETED/FAILED',
  `summary` varchar(500) NOT NULL COMMENT '业务摘要',
  `payload_json` text COMMENT '脱敏事件负载',
  `occurred_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_business_event_id` (`event_id`),
  KEY `idx_event_trace` (`trace_id`, `occurred_at`),
  KEY `idx_event_order` (`order_id`, `occurred_at`),
  KEY `idx_event_type` (`event_type`),
  CONSTRAINT `fk_event_order` FOREIGN KEY (`order_id`) REFERENCES `purchase_order` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='领域业务事件流';

-- 13. API 幂等记录：同一业务意图只能生成一个资源
CREATE TABLE IF NOT EXISTS `api_idempotency` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `idempotency_key` varchar(128) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  `resource_type` varchar(50) NOT NULL,
  `resource_id` bigint DEFAULT NULL,
  `response_json` longtext,
  `status` varchar(20) NOT NULL DEFAULT 'PROCESSING',
  `expires_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idempotency_key` (`idempotency_key`),
  KEY `idx_idempotency_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='接口幂等记录';

-- 14. 事务消息表：和业务写入共享 MySQL 事务，RabbitMQ 暂停时消息仍不会丢
CREATE TABLE IF NOT EXISTS `outbox_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` varchar(64) NOT NULL,
  `aggregate_type` varchar(50) NOT NULL,
  `aggregate_id` varchar(100) NOT NULL,
  `event_type` varchar(80) NOT NULL,
  `trace_id` varchar(64) NOT NULL,
  `payload_json` longtext,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT 0,
  `next_attempt_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_error` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `published_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_pending` (`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事务消息发件箱';

CREATE TABLE IF NOT EXISTS `event_consumption` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `consumer_name` varchar(100) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `consumed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_consumer` (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息消费去重日志';

-- 为演示环境补齐脱敏收货与发票数据；幂等执行，不影响用户新增数据。
INSERT IGNORE INTO `goods_receipt`
(`receipt_number`,`order_id`,`received_quantity`,`received_amount`,`received_at`,`warehouse_location`,`receiver`,`status`,`remark`)
SELECT CONCAT('GR-', LPAD(o.id, 6, '0')), o.id,
       SUM(od.quantity) - IF(MOD(o.id, 11)=0, GREATEST(1,FLOOR(SUM(od.quantity)*0.03)), 0),
       ROUND(o.total_amount - IF(MOD(o.id,11)=0,o.total_amount*0.03,0),2),
       COALESCE(CONCAT(o.actual_delivery_date,' 10:00:00'), DATE_ADD(o.order_time, INTERVAL 7 DAY)),
       'ESD-A / 验收区', '仓储专员', 'CONFIRMED',
       IF(MOD(o.id,11)=0,'到货短装，等待供应商补发','数量与外观验收完成')
FROM purchase_order o JOIN order_detail od ON od.order_id=o.id
WHERE o.status=4 AND o.deleted=0 AND od.deleted=0
GROUP BY o.id;

INSERT IGNORE INTO `supplier_invoice`
(`invoice_number`,`order_id`,`supplier_id`,`invoice_quantity`,`invoice_amount`,`invoice_date`,`tax_rate`,`status`,`remark`)
SELECT CONCAT('INV-', LPAD(o.id, 8, '0')), o.id, MIN(p.supplier_id), SUM(od.quantity),
       ROUND(o.total_amount + IF(MOD(o.id,13)=0,88.00,0),2),
       COALESCE(o.actual_delivery_date, DATE(o.order_time)), 13.00, 'RECEIVED',
       IF(MOD(o.id,13)=0,'发票金额包含额外费用，待采购复核','电子专票已接收')
FROM purchase_order o JOIN order_detail od ON od.order_id=o.id JOIN part p ON p.id=od.part_id
WHERE o.status=4 AND o.deleted=0 AND od.deleted=0
GROUP BY o.id;

INSERT IGNORE INTO `business_event`
(`event_id`,`trace_id`,`event_type`,`aggregate_type`,`aggregate_id`,`order_id`,`actor`,`source`,`status`,`summary`,`payload_json`,`occurred_at`)
SELECT CONCAT('EVT-SEED-CREATE-',o.id),CONCAT('TRACE-PO-',LPAD(o.id,6,'0')),'PURCHASE_ORDER_CREATED',
       'PURCHASE_ORDER',CAST(o.id AS CHAR),o.id,'procurement_agent','procurement-service','COMPLETED',
       CONCAT('采购订单 ',o.order_number,' 已创建'),'{}',o.order_time
FROM purchase_order o WHERE o.deleted=0;

INSERT IGNORE INTO `business_event`
(`event_id`,`trace_id`,`event_type`,`aggregate_type`,`aggregate_id`,`order_id`,`actor`,`source`,`status`,`summary`,`payload_json`,`occurred_at`)
SELECT CONCAT('EVT-SEED-RECEIPT-',o.id),CONCAT('TRACE-PO-',LPAD(o.id,6,'0')),'GOODS_RECEIPT_CONFIRMED',
       'PURCHASE_ORDER',CAST(o.id AS CHAR),o.id,'ops_user_3','warehouse-service','COMPLETED',
       CONCAT('订单 ',o.order_number,' 完成到货验收'),'{}',COALESCE(gr.received_at,DATE_ADD(o.order_time,INTERVAL 7 DAY))
FROM purchase_order o JOIN goods_receipt gr ON gr.order_id=o.id WHERE o.status=4 AND o.deleted=0;

INSERT IGNORE INTO `business_event`
(`event_id`,`trace_id`,`event_type`,`aggregate_type`,`aggregate_id`,`order_id`,`actor`,`source`,`status`,`summary`,`payload_json`,`occurred_at`)
SELECT CONCAT('EVT-SEED-INVOICE-',o.id),CONCAT('TRACE-PO-',LPAD(o.id,6,'0')),'SUPPLIER_INVOICE_RECEIVED',
       'PURCHASE_ORDER',CAST(o.id AS CHAR),o.id,'ops_user_1','finance-service','COMPLETED',
       CONCAT('订单 ',o.order_number,' 已接收供应商发票'),'{}',CONCAT(si.invoice_date,' 14:00:00')
FROM purchase_order o JOIN supplier_invoice si ON si.order_id=o.id WHERE o.status=4 AND o.deleted=0;

INSERT IGNORE INTO `business_event`
(`event_id`,`trace_id`,`event_type`,`aggregate_type`,`aggregate_id`,`order_id`,`actor`,`source`,`status`,`summary`,`payload_json`,`occurred_at`)
SELECT CONCAT('EVT-SEED-WORKFLOW-',o.id),CONCAT('TRACE-PO-',LPAD(o.id,6,'0')),'ORDER_STATUS_CHANGED',
       'PURCHASE_ORDER',CAST(o.id AS CHAR),o.id,'ops_user_2','workflow-service','COMPLETED',
       CONCAT('订单 ',o.order_number,' 已完成审批并进入采购执行'),'{}',DATE_ADD(o.order_time,INTERVAL 1 DAY)
FROM purchase_order o WHERE o.status=4 AND o.deleted=0;

INSERT IGNORE INTO `business_event`
(`event_id`,`trace_id`,`event_type`,`aggregate_type`,`aggregate_id`,`order_id`,`actor`,`source`,`status`,`summary`,`payload_json`,`occurred_at`)
SELECT CONCAT('EVT-SEED-INVENTORY-',o.id),CONCAT('TRACE-PO-',LPAD(o.id,6,'0')),'INVENTORY_INBOUND',
       'PURCHASE_ORDER',CAST(o.id AS CHAR),o.id,'ops_user_3','inventory-service','COMPLETED',
       CONCAT('订单 ',o.order_number,' 对应物料已更新可用库存'),'{}',DATE_ADD(gr.received_at,INTERVAL 5 MINUTE)
FROM purchase_order o JOIN goods_receipt gr ON gr.order_id=o.id WHERE o.status=4 AND o.deleted=0;

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
