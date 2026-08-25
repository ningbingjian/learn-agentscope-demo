package com.example.agentscope.permissionhitl.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class RefundTools {

    @Tool(
            name = "issue_refund",
            description = "Simulates issuing a refund for an order after user approval.",
            strict = true,
            readOnly = false,
            concurrencySafe = true
    )
    public String issueRefund(
            @ToolParam(name = "orderId", description = "Order identifier") String orderId,
            @ToolParam(name = "amount", description = "Refund amount in yuan") double amount
    ) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be greater than 0");
        }
        return "SIMULATED_REFUND_OK orderId=" + orderId + ", amount=" + amount;
    }
}
