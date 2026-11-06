package com.chipsupply.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chipsupply.common.PageResult;
import com.chipsupply.common.Result;
import com.chipsupply.common.enums.ResultCode;
import com.chipsupply.entity.Customer;
import com.chipsupply.service.CustomerService;
import com.chipsupply.audit.Auditable;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 客户管理控制器
 */
@Tag(name = "客户管理")
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Validated
public class CustomerController {

    private final CustomerService customerService;

    /**
     * 创建客户
     */
    @Operation(summary = "创建客户")
    @PostMapping("/create")
    @Auditable(action = "CUSTOMER_CREATE", resourceType = "CUSTOMER")
    public Result<Customer> create(@Valid @RequestBody Customer customer) {
        boolean success = customerService.save(customer);
        if (!success) {
            return Result.error();
        }
        return Result.success(customer);
    }

    /**
     * 更新客户
     */
    @Operation(summary = "更新客户")
    @PutMapping("/update/{id}")
    @Auditable(action = "CUSTOMER_UPDATE", resourceType = "CUSTOMER")
    public Result<Customer> update(
            @Parameter(description = "客户ID") @PathVariable Long id,
            @Valid @RequestBody Customer customer) {
        customer.setId(id);
        boolean success = customerService.updateById(customer);
        if (!success) {
            return Result.error(ResultCode.DATA_NOT_EXISTS);
        }
        return Result.success(customer);
    }

    /**
     * 删除客户（逻辑删除）
     */
    @Operation(summary = "删除客户")
    @DeleteMapping("/delete/{id}")
    @Auditable(action = "CUSTOMER_DELETE", resourceType = "CUSTOMER")
    public Result<Void> delete(@Parameter(description = "客户ID") @PathVariable Long id) {
        boolean success = customerService.removeById(id);
        if (!success) {
            return Result.error(ResultCode.DATA_NOT_EXISTS);
        }
        return Result.success();
    }

    /**
     * 获取单个客户
     */
    @Operation(summary = "获取单个客户")
    @GetMapping("/get/{id}")
    public Result<Customer> getById(@Parameter(description = "客户ID") @PathVariable Long id) {
        Customer customer = customerService.getById(id);
        if (customer == null) {
            return Result.error(ResultCode.DATA_NOT_EXISTS);
        }
        return Result.success(customer);
    }

    /**
     * 分页查询客户
     */
    @Operation(summary = "分页查询客户")
    @GetMapping("/page")
    public Result<PageResult<Customer>> page(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Long current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Long size,
            @Parameter(description = "客户名称") @RequestParam(required = false) String name,
            @Parameter(description = "客户类型") @RequestParam(required = false) Integer customerType,
            @Parameter(description = "折扣等级") @RequestParam(required = false) Integer discountLevel) {
        Page<Customer> page = new Page<>(current, size);
        Page<Customer> result = customerService.pageCustomers(page, name, customerType, discountLevel);
        return Result.success(PageResult.from(result));
    }

    /**
     * 搜索客户
     */
    @Operation(summary = "搜索客户")
    @GetMapping("/search")
    public Result<List<Customer>> search(
            @Parameter(description = "客户名称关键字") @RequestParam String name) {
        List<Customer> list = customerService.searchByName(name);
        return Result.success(list);
    }

    /**
     * 更新客户类型
     */
    @Operation(summary = "更新客户类型")
    @PatchMapping("/update-type/{id}")
    @Auditable(action = "CUSTOMER_TYPE_UPDATE", resourceType = "CUSTOMER")
    public Result<Void> updateCustomerType(
            @Parameter(description = "客户ID") @PathVariable Long id,
            @Parameter(description = "新客户类型") @RequestParam Integer customerType) {
        boolean success = customerService.updateCustomerType(id, customerType);
        if (!success) {
            return Result.error(ResultCode.DATA_NOT_EXISTS);
        }
        return Result.success();
    }

    /**
     * 更新折扣等级
     */
    @Operation(summary = "更新折扣等级")
    @PatchMapping("/update-discount/{id}")
    @Auditable(action = "CUSTOMER_DISCOUNT_UPDATE", resourceType = "CUSTOMER")
    public Result<Void> updateDiscountLevel(
            @Parameter(description = "客户ID") @PathVariable Long id,
            @Parameter(description = "新折扣等级") @RequestParam Integer discountLevel) {
        boolean success = customerService.updateDiscountLevel(id, discountLevel);
        if (!success) {
            return Result.error(ResultCode.DATA_NOT_EXISTS);
        }
        return Result.success();
    }
}
