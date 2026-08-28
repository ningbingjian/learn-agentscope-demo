package com.example.agentscope.agentteams.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class TeamBoardService {

    private final ConcurrentHashMap<String, TeamTask> tasks = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<TeamMessage> messages = new CopyOnWriteArrayList<>();

    public Map<String, Object> versionBoundary() {
        return Map.of(
                "lockedAgentScopeVersion", "2.0.1",
                "officialAgentTeamsAvailable", classExists("io.agentscope.harness.agent.middleware.TeamsMiddleware"),
                "officialTeamToolAvailable", classExists("io.agentscope.harness.agent.tool.TeamTool"),
                "lessonMode", "2.0.1-compatible application-layer coordination model");
    }

    public TeamTask createTask(String subject) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        TeamTask task = new TeamTask(id, subject, TaskState.PENDING, null, 0L, null);
        tasks.put(id, task);
        return task;
    }

    public TeamTask claim(String taskId, String member, long expectedVersion) {
        return tasks.compute(taskId, (id, current) -> {
            if (current == null) {
                throw new IllegalArgumentException("task not found: " + taskId);
            }
            requireVersion(current, expectedVersion);
            if (current.state() != TaskState.PENDING) {
                throw new TeamConflictException("task is not pending: " + current.state());
            }
            return new TeamTask(current.id(), current.subject(), TaskState.IN_PROGRESS,
                    member, current.version() + 1, current.result());
        });
    }

    public TeamTask complete(String taskId, String member, long expectedVersion, String result) {
        return tasks.compute(taskId, (id, current) -> {
            if (current == null) {
                throw new IllegalArgumentException("task not found: " + taskId);
            }
            requireVersion(current, expectedVersion);
            if (current.state() != TaskState.IN_PROGRESS || !member.equals(current.owner())) {
                throw new TeamConflictException("only current owner can complete the task");
            }
            return new TeamTask(current.id(), current.subject(), TaskState.COMPLETED,
                    member, current.version() + 1, result);
        });
    }

    public TeamMessage sendMessage(String from, String to, String content) {
        TeamMessage message = new TeamMessage(
                UUID.randomUUID().toString().substring(0, 8), from, to, content, Instant.now());
        messages.add(message);
        return message;
    }

    public List<TeamTask> tasks() {
        return tasks.values().stream().sorted(Comparator.comparing(TeamTask::id)).toList();
    }

    public List<TeamMessage> messagesFor(String member) {
        List<TeamMessage> result = new ArrayList<>();
        for (TeamMessage message : messages) {
            if (member.equals(message.to()) || "*".equals(message.to())) {
                result.add(message);
            }
        }
        return List.copyOf(result);
    }

    public void clear() {
        tasks.clear();
        messages.clear();
    }

    private static void requireVersion(TeamTask current, long expectedVersion) {
        if (current.version() != expectedVersion) {
            throw new TeamConflictException(
                    "stale task version: expected=" + expectedVersion + ", actual=" + current.version());
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public enum TaskState { PENDING, IN_PROGRESS, COMPLETED }

    public record TeamTask(String id, String subject, TaskState state, String owner, long version, String result) {}

    public record TeamMessage(String id, String from, String to, String content, Instant createdAt) {}

    public static class TeamConflictException extends RuntimeException {
        public TeamConflictException(String message) { super(message); }
    }
}
