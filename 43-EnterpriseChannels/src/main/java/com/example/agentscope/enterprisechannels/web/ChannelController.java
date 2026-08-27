package com.example.agentscope.enterprisechannels.web;

import com.example.agentscope.enterprisechannels.service.ChannelCatalogService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    private final ChannelCatalogService service;

    public ChannelController(ChannelCatalogService service) {
        this.service = service;
    }

    @GetMapping("/providers")
    public List<Map<String, Object>> providers() {
        return service.providers();
    }

    @PostMapping("/guard")
    public Map<String, Object> guard(
            @RequestParam String peer,
            @RequestParam String messageId) {
        return service.inspectInboundGuard(peer, messageId);
    }
}
