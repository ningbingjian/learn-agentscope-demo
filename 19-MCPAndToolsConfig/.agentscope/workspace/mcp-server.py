#!/usr/bin/env python3
"""A tiny MCP stdio server using only the Python standard library.

It implements just enough MCP for this lesson:
- initialize
- notifications/initialized
- ping
- tools/list
- tools/call

Protocol messages are JSON-RPC objects, one JSON object per line on stdin/stdout.
All diagnostic logs go to stderr so stdout stays protocol-only.
"""

import json
import sys


def send(payload):
    sys.stdout.write(json.dumps(payload, ensure_ascii=False) + "\n")
    sys.stdout.flush()


def result(request_id, value):
    send({"jsonrpc": "2.0", "id": request_id, "result": value})


def error(request_id, code, message):
    send({"jsonrpc": "2.0", "id": request_id, "error": {"code": code, "message": message}})


def tools():
    return [
        {
            "name": "echo_text",
            "description": "Echo the provided text with an MCP prefix.",
            "inputSchema": {
                "type": "object",
                "properties": {"text": {"type": "string"}},
                "required": ["text"],
                "additionalProperties": False,
            },
        },
        {
            "name": "add_numbers",
            "description": "Add two numbers and return the result.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "a": {"type": "number"},
                    "b": {"type": "number"},
                },
                "required": ["a", "b"],
                "additionalProperties": False,
            },
        },
    ]


def call_tool(name, arguments):
    if name == "echo_text":
        text = str(arguments.get("text", ""))
        return {"content": [{"type": "text", "text": f"MCP echo: {text}"}], "isError": False}
    if name == "add_numbers":
        a = arguments.get("a", 0)
        b = arguments.get("b", 0)
        return {"content": [{"type": "text", "text": str(a + b)}], "isError": False}
    return {"content": [{"type": "text", "text": f"Unknown tool: {name}"}], "isError": True}


def handle(message):
    method = message.get("method")
    request_id = message.get("id")
    params = message.get("params") or {}

    if method == "initialize":
        requested = params.get("protocolVersion", "2024-11-05")
        result(
            request_id,
            {
                "protocolVersion": requested,
                "capabilities": {"tools": {}},
                "serverInfo": {"name": "learn-agentscope-mcp", "version": "1.0.0"},
            },
        )
    elif method == "notifications/initialized":
        return
    elif method == "ping":
        result(request_id, {})
    elif method == "tools/list":
        result(request_id, {"tools": tools()})
    elif method == "tools/call":
        result(request_id, call_tool(params.get("name"), params.get("arguments") or {}))
    elif request_id is not None:
        error(request_id, -32601, f"Method not found: {method}")


def main():
    print("learning MCP server started", file=sys.stderr, flush=True)
    for raw in sys.stdin:
        raw = raw.strip()
        if not raw:
            continue
        try:
            handle(json.loads(raw))
        except Exception as exc:
            print(f"MCP server error: {exc}", file=sys.stderr, flush=True)


if __name__ == "__main__":
    main()
