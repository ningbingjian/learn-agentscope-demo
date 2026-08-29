package com.example.agentscope.evaluation.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class RecordingWeatherTools {
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> lastCity = new AtomicReference<>();

    @Tool(name = "get_weather", description = "Get deterministic weather for evaluation", readOnly = true)
    public String getWeather(@ToolParam(name = "city", description = "City name") String city) {
        calls.incrementAndGet();
        lastCity.set(city);
        return city + ":sunny";
    }

    public void reset() { calls.set(0); lastCity.set(null); }
    public int calls() { return calls.get(); }
    public String lastCity() { return lastCity.get(); }
}
