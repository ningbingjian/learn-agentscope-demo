package com.example.agentscope.toolcalling.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;

public class CalculatorTools {

    private static final Logger log = LoggerFactory.getLogger(CalculatorTools.class);

    @Tool(
            name = "calculate",
            description = "Precisely calculates two decimal numbers. "
                    + "The operation must be one of: add, subtract, multiply, divide.",
            strict = true,
            readOnly = true,
            concurrencySafe = true
    )
    public String calculate(
            @ToolParam(name = "left", description = "The left decimal operand") BigDecimal left,
            @ToolParam(
                    name = "operation",
                    description = "One of: add, subtract, multiply, divide"
            ) String operation,
            @ToolParam(name = "right", description = "The right decimal operand") BigDecimal right
    ) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Both operands are required");
        }
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("Operation is required");
        }

        BigDecimal result = switch (operation.toLowerCase(Locale.ROOT)) {
            case "add" -> left.add(right);
            case "subtract" -> left.subtract(right);
            case "multiply" -> left.multiply(right);
            case "divide" -> divide(left, right);
            default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
        };

        String text = result.stripTrailingZeros().toPlainString();
        log.info(
                "Tool calculate called: left={}, operation={}, right={}, result={}",
                left,
                operation,
                right,
                text
        );
        return text;
    }

    private static BigDecimal divide(BigDecimal left, BigDecimal right) {
        if (right.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Divisor must not be zero");
        }
        return left.divide(right, MathContext.DECIMAL128);
    }
}
