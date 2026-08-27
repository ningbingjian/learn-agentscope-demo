package com.example.agentscope.agentteams.web;

import com.example.agentscope.agentteams.service.TeamBoardService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams")
public class TeamController {
    private final TeamBoardService service;

    public TeamController(TeamBoardService service) { this.service = service; }

    @GetMapping("/version-boundary")
    public Map<String, Object> versionBoundary() { return service.versionBoundary(); }

    @GetMapping("/tasks")
    public List<TeamBoardService.TeamTask> tasks() { return service.tasks(); }

    @PostMapping("/tasks")
    public TeamBoardService.TeamTask create(@RequestBody CreateTask request) {
        return service.createTask(request.subject());
    }

    @PostMapping("/claim")
    public TeamBoardService.TeamTask claim(@RequestBody ClaimTask request) {
        return service.claim(request.taskId(), request.member(), request.expectedVersion());
    }

    @PostMapping("/complete")
    public TeamBoardService.TeamTask complete(@RequestBody CompleteTask request) {
        return service.complete(request.taskId(), request.member(), request.expectedVersion(), request.result());
    }

    @PostMapping("/messages")
    public TeamBoardService.TeamMessage message(@RequestBody SendMessage request) {
        return service.sendMessage(request.from(), request.to(), request.content());
    }

    public record CreateTask(String subject) {}
    public record ClaimTask(String taskId, String member, long expectedVersion) {}
    public record CompleteTask(String taskId, String member, long expectedVersion, String result) {}
    public record SendMessage(String from, String to, String content) {}
}
