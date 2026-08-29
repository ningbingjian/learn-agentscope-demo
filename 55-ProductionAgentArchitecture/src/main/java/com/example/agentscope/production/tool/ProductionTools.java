package com.example.agentscope.production.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ProductionTools {
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> lastOrderId = new AtomicReference<>();

    @Tool(name = "get_order_status", description = "Read an order status", readOnly = true)
    public String getOrderStatus(
            @ToolParam(name = "orderId", description = "Order id such as A1001") String orderId) {
        calls.incrementAndGet();
        lastOrderId.set(orderId);
        String status = "A1001".equalsIgnoreCase(orderId) ? "SHIPPED" : "PROCESSING";
        return "orderId=" + orderId + ",status=" + status;
    }

    public int calls() { return calls.get(); }
    public String lastOrderId() { return lastOrderId.get(); }
    public void reset() { calls.set(0); lastOrderId.set(null); }
}
