package com.hmdp.mcp.config;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.AiSkillRunRequest;
import com.hmdp.dto.ReservationDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.Reservation;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IAiSkillService;
import com.hmdp.service.IReservationService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class McpToolsConfig {

    private final IShopService shopService;
    private final IShopTypeService shopTypeService;
    private final IVoucherService voucherService;
    private final IVoucherOrderService voucherOrderService;
    private final IReservationService reservationService;
    private final IAiSkillService aiSkillService;

    @Bean
    public McpStatelessSyncServer mcpStatelessSyncServer(HttpServletStatelessServerTransport transport) {
        McpStatelessSyncServer server = McpServer.sync(transport)
                .serverInfo("ai-dianping-mcp", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .build();

        registerShopTools(server);
        registerVoucherTools(server);
        registerOrderTools(server);
        registerReservationTools(server);
        registerAiSkillTools(server);

        return server;
    }

    private void registerShopTools(McpStatelessSyncServer server) {
        server.addTool(new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("queryShopById",
                        "根据店铺ID查询店铺详细信息，包括名称、地址、类型、评分、营业状态、人均消费等",
                        """
                                { "type": "object",
                                  "properties": {
                                    "shopId": { "type": "integer", "description": "店铺ID" }
                                  },
                                  "required": ["shopId"] }
                                """),
                (exchange, request) -> {
                    Number shopId = (Number) request.arguments().get("shopId");
                    Result result = shopService.queryById(shopId == null ? null : shopId.longValue());
                    return new McpSchema.CallToolResult(JSONUtil.toJsonStr(result), false);
                }));

        server.addTool(new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("queryShopTypes",
                        "查询所有店铺类型分类，例如美食、娱乐、酒店等",
                        """
                                { "type": "object", "properties": {} }
                                """),
                (exchange, request) -> {
                    List<ShopType> list = shopTypeService.query().orderByAsc("sort").list();
                    return new McpSchema.CallToolResult(JSONUtil.toJsonStr(Result.ok(list)), false);
                }));

        server.addTool(new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("queryNearbyShops",
                        "根据当前用户位置（经纬度）和店铺类型ID，分页查询附近的店铺，按距离由近到远排序",
                        """
                                { "type": "object",
                                  "properties": {
                                    "typeId": { "type": "integer", "description": "店铺类型ID" },
                                    "current": { "type": "integer", "description": "页码，从1开始，默认1" },
                                    "x": { "type": "number", "description": "经度" },
                                    "y": { "type": "number", "description": "纬度" }
                                  },
                                  "required": ["typeId", "x", "y"] }
                                """),
                (exchange, request) -> {
                    Map<String, Object> args = request.arguments();
                    Number typeId = (Number) args.get("typeId");
                    Number current = (Number) args.get("current");
                    Number x = (Number) args.get("x");
                    Number y = (Number) args.get("y");
                    int page = current == null || current.intValue() < 1 ? 1 : current.intValue();
                    Result result = shopService.queryShopByType(typeId == null ? null : typeId.intValue(),
                            page, x == null ? null : x.doubleValue(), y == null ? null : y.doubleValue(), "distance");
                    return new McpSchema.CallToolResult(JSONUtil.toJsonStr(result), false);
                }));

        server.addTool(new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("searchShopByName",
                        "根据关键词模糊搜索店铺名称，返回匹配的店铺列表",
                        """
                                { "type": "object",
                                  "properties": {
                                    "keyword": { "type": "string", "description": "店铺名称关键词" }
                                  },
                                  "required": ["keyword"] }
                                """),
                (exchange, request) -> {
                    String keyword = (String) request.arguments().get("keyword");
                    List<Shop> list = shopService.query()
                            .like("name", keyword)
                            .last("limit 20")
                            .list();
                    List<Shop> dtoList = list.stream()
                            .map(shop -> {
                                Shop s = new Shop();
                                s.setId(shop.getId());
                                s.setName(shop.getName());
                                s.setTypeId(shop.getTypeId());
                                s.setArea(shop.getArea());
                                s.setAddress(shop.getAddress());
                                s.setScore(shop.getScore());
                                s.setAvgPrice(shop.getAvgPrice());
                                s.setOpenHours(shop.getOpenHours());
                                return s;
                            })
                            .collect(Collectors.toList());
                    return new McpSchema.CallToolResult(JSONUtil.toJsonStr(Result.ok(dtoList)), false);
                }));
    }

    private void registerVoucherTools(McpStatelessSyncServer server) {
        server.addTool(new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("queryVouchersByShopId",
                        "查询指定店铺ID下的所有优惠券/代金券列表，包含普通券和秒杀券",
                        """
                                { "type": "object",
                                  "properties": {
                                    "shopId": { "type": "integer", "description": "店铺ID" }
                                  },
                                  "required": ["shopId"] }
                                """),
                (exchange, request) -> {
                    Number shopId = (Number) request.arguments().get("shopId");
                    Result result = voucherService.queryVoucherOfShop(shopId == null ? null : shopId.longValue());
                    return new McpSchema.CallToolResult(JSONUtil.toJsonStr(result), false);
                }));
    }

    private void registerOrderTools(McpStatelessSyncServer server) {
        server.addTool(new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("queryMyOrders",
                        "查询指定用户的优惠券订单列表，支持按状态筛选：1-未使用，2-已使用，3-已退款",
                        """
                                { "type": "object",
                                  "properties": {
                                    "userId": { "type": "integer", "description": "用户ID" },
                                    "status": { "type": "integer", "description": "订单状态，可选" }
                                  },
                                  "required": ["userId"] }
                                """),
                (exchange, request) -> {
                    Map<String, Object> args = request.arguments();
                    Number userId = (Number) args.get("userId");
                    Number status = (Number) args.get("status");
                    if (userId == null || userId.longValue() <= 0) {
                        return new McpSchema.CallToolResult(JSONUtil.toJsonStr(Result.fail("userId不能为空")), false);
                    }
                    var query = voucherOrderService.lambdaQuery()
                            .eq(VoucherOrder::getUserId, userId.longValue())
                            .orderByDesc(VoucherOrder::getCreateTime);
                    if (status != null) {
                        query.eq(VoucherOrder::getStatus, status.intValue());
                    }
                    List<VoucherOrder> list = query.list();
                    return new McpSchema.CallToolResult(JSONUtil.toJsonStr(Result.ok(list, (long) list.size())), false);
                }));
    }

    private void registerReservationTools(McpStatelessSyncServer server) {
        server.addTool(new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("queryReservationById",
                        "根据预约ID查询预约详情",
                        """
                                { "type": "object",
                                  "properties": {
                                    "reservationId": { "type": "integer", "description": "预约ID" }
                                  },
                                  "required": ["reservationId"] }
                                """),
                (exchange, request) -> {
                    Number reservationId = (Number) request.arguments().get("reservationId");
                    Reservation reservation = reservationService.getById(reservationId == null ? null : reservationId.longValue());
                    return new McpSchema.CallToolResult(JSONUtil.toJsonStr(
                            reservation == null ? Result.fail("预约不存在") : Result.ok(reservation)), false);
                }));

        server.addTool(new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("queryMyReservations",
                        "查询指定用户的预约列表，按创建时间倒序返回",
                        """
                                { "type": "object",
                                  "properties": {
                                    "userId": { "type": "integer", "description": "用户ID" }
                                  },
                                  "required": ["userId"] }
                                """),
                (exchange, request) -> {
                    Number userId = (Number) request.arguments().get("userId");
                    if (userId == null || userId.longValue() <= 0) {
                        return new McpSchema.CallToolResult(JSONUtil.toJsonStr(Result.fail("userId不能为空")), false);
                    }
                    List<Reservation> list = reservationService.lambdaQuery()
                            .eq(Reservation::getUserId, userId.longValue())
                            .orderByDesc(Reservation::getCreateTime)
                            .list();
                    return new McpSchema.CallToolResult(JSONUtil.toJsonStr(Result.ok(list, (long) list.size())), false);
                }));

        server.addTool(new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("createReservation",
                        "为用户创建一条店铺预约",
                        """
                                { "type": "object",
                                  "properties": {
                                    "userId": { "type": "integer", "description": "用户ID" },
                                    "shopId": { "type": "integer", "description": "店铺ID" },
                                    "reservationTime": { "type": "string", "description": "预约时间，格式 yyyy-MM-dd HH:mm" },
                                    "numPeople": { "type": "integer", "description": "预约人数" }
                                  },
                                  "required": ["userId", "shopId", "reservationTime", "numPeople"] }
                                """),
                (exchange, request) -> {
                    Map<String, Object> args = request.arguments();
                    Number userId = (Number) args.get("userId");
                    Number shopId = (Number) args.get("shopId");
                    String reservationTime = (String) args.get("reservationTime");
                    Number numPeople = (Number) args.get("numPeople");
                    if (userId == null || userId.longValue() <= 0 || shopId == null || shopId.longValue() <= 0
                            || numPeople == null || numPeople.intValue() <= 0
                            || reservationTime == null || reservationTime.trim().isEmpty()) {
                        return new McpSchema.CallToolResult(JSONUtil.toJsonStr(Result.fail("用户、店铺、预约时间和人数不能为空且必须有效")), false);
                    }
                    LocalDateTime time;
                    try {
                        time = LocalDateTime.parse(reservationTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    } catch (Exception e) {
                        return new McpSchema.CallToolResult(JSONUtil.toJsonStr(Result.fail("预约时间格式错误，请使用 yyyy-MM-dd HH:mm")), false);
                    }
                    if (time.isBefore(LocalDateTime.now())) {
                        return new McpSchema.CallToolResult(JSONUtil.toJsonStr(Result.fail("预约时间不能早于当前时间")), false);
                    }
                    Long count = reservationService.lambdaQuery()
                            .eq(Reservation::getUserId, userId.longValue())
                            .eq(Reservation::getShopId, shopId == null ? null : shopId.longValue())
                            .eq(Reservation::getReservationTime, time)
                            .count();
                    if (count != null && count > 0) {
                        return new McpSchema.CallToolResult(JSONUtil.toJsonStr(Result.fail("该时间段已存在预约，请勿重复提交")), false);
                    }
                    Reservation reservation = new Reservation();
                    reservation.setUserId(userId.longValue());
                    reservation.setShopId(shopId == null ? null : shopId.longValue());
                    reservation.setReservationTime(time);
                    reservation.setNumPeople(numPeople == null ? 1 : numPeople.intValue());
                    reservation.setStatus(0);
                    reservation.setCreateTime(LocalDateTime.now());
                    reservation.setUpdateTime(LocalDateTime.now());
                    boolean saved = reservationService.save(reservation);
                    return new McpSchema.CallToolResult(JSONUtil.toJsonStr(
                            saved ? Result.ok(reservation.getId()) : Result.fail("预约失败")), false);
                }));
    }

    private void registerAiSkillTools(McpStatelessSyncServer server) {
        server.addTool(new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("queryEnabledAiSkills",
                        "查询当前系统已启用的 AI 技能列表",
                        """
                                { "type": "object", "properties": {} }
                                """),
                (exchange, request) -> {
                    Result result = aiSkillService.queryEnabledSkills();
                    return new McpSchema.CallToolResult(JSONUtil.toJsonStr(result), false);
                }));

        server.addTool(new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("invokeAiSkill",
                        "根据技能编码调用 AI 技能并返回生成结果",
                        """
                                { "type": "object",
                                  "properties": {
                                    "skillCode": { "type": "string", "description": "技能编码，如 review_summary、review_reply、blog_draft、coupon_copy、short_video_script" },
                                    "userInput": { "type": "string", "description": "用户输入内容" },
                                    "userId": { "type": "integer", "description": "当前用户ID，用于保存执行记录" }
                                  },
                                  "required": ["skillCode", "userInput", "userId"] }
                                """),
                (exchange, request) -> {
                    Map<String, Object> args = request.arguments();
                    String skillCode = (String) args.get("skillCode");
                    String userInput = (String) args.get("userInput");
                    Number userId = (Number) args.get("userId");
                    if (skillCode == null || skillCode.trim().isEmpty()) {
                        return new McpSchema.CallToolResult(JSONUtil.toJsonStr(Result.fail("技能编码不能为空")), false);
                    }
                    if (userInput == null || userInput.trim().isEmpty() || userId == null || userId.longValue() <= 0) {
                        return new McpSchema.CallToolResult(JSONUtil.toJsonStr(Result.fail("userId和用户输入不能为空")), false);
                    }
                    Map<String, Object> input = new HashMap<>();
                    input.put("input", userInput);
                    input.put("content", userInput);
                    input.put("keywords", userInput);
                    input.put("review", userInput);
                    AiSkillRunRequest req = new AiSkillRunRequest();
                    req.setUserId(userId.longValue());
                    req.setInput(input);
                    Result result = aiSkillService.runSkill(skillCode.trim(), req);
                    return new McpSchema.CallToolResult(JSONUtil.toJsonStr(result), false);
                }));
    }
}
