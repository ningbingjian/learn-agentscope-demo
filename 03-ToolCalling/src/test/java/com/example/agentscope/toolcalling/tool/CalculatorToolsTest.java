package com.example.agentscope.toolcalling.tool;

import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculatorToolsTest {

    private final CalculatorTools calculator = new CalculatorTools();

    @Test
    void registersAnnotatedMethodAsAgentTool() {
        Toolkit toolkit = new Toolkit();

        toolkit.registerTool(calculator);

        assertThat(toolkit.getToolNames()).contains("calculate");
    }

    @Test
    void multipliesDecimalNumbersPrecisely() {
        String result = calculator.calculate(
                new BigDecimal("123.45"),
                "multiply",
                new BigDecimal("67.89")
        );

        assertThat(result).isEqualTo("8381.0205");
    }

    @Test
    void rejectsDivisionByZero() {
        assertThatThrownBy(() -> calculator.calculate(
                BigDecimal.ONE,
                "divide",
                BigDecimal.ZERO
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Divisor must not be zero");
    }
}
