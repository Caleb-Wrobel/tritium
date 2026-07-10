#!/usr/bin/env python3
"""MCP stdio server bridging Claude Code to a local Open WebUI instance.

Dependency-free: speaks MCP's JSON-RPC over stdio directly and calls
Open WebUI's OpenAI-compatible API with urllib.

Config via environment:
  OPENWEBUI_URL      base URL, e.g. http://192.168.1.50:3000 (required)
  OPENWEBUI_API_KEY  API key from Open WebUI Settings > Account (required)
  LOCAL_MODEL        default model id (default: first model the server lists)
"""

import json
import os
import sys
import urllib.request

BASE_URL = os.environ.get("OPENWEBUI_URL", "").rstrip("/")
API_KEY = os.environ.get("OPENWEBUI_API_KEY", "")
DEFAULT_MODEL = os.environ.get("LOCAL_MODEL", "")

TOOLS = [
    {
        "name": "local_llm",
        "description": (
            "Ask the on-prem local LLM (Open WebUI). Use for mundane, low-stakes "
            "tasks to conserve Claude usage: dictionary/terminology lookups, "
            "summarizing reference documents, boilerplate explanations, quick "
            "sanity checks. Not for code changes or design decisions."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "prompt": {"type": "string", "description": "The question or task."},
                "system": {"type": "string", "description": "Optional system prompt."},
                "model": {"type": "string", "description": "Override the default local model id."},
            },
            "required": ["prompt"],
        },
    },
    {
        "name": "list_local_models",
        "description": "List model ids available on the local Open WebUI instance.",
        "inputSchema": {"type": "object", "properties": {}},
    },
]


def api(path, payload=None):
    req = urllib.request.Request(
        BASE_URL + path,
        data=json.dumps(payload).encode() if payload is not None else None,
        headers={
            "Authorization": f"Bearer {API_KEY}",
            "Content-Type": "application/json",
        },
        method="POST" if payload is not None else "GET",
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.load(resp)


def list_models():
    data = api("/api/models")
    items = data.get("data", data) if isinstance(data, dict) else data
    return [m.get("id", m.get("name", "?")) for m in items]


def chat(prompt, system=None, model=None):
    model = model or DEFAULT_MODEL or list_models()[0]
    messages = ([{"role": "system", "content": system}] if system else []) + [
        {"role": "user", "content": prompt}
    ]
    data = api("/api/chat/completions", {"model": model, "messages": messages})
    return data["choices"][0]["message"]["content"]


def handle(req):
    method = req.get("method")
    params = req.get("params", {})
    if method == "initialize":
        return {
            "protocolVersion": params.get("protocolVersion", "2025-06-18"),
            "capabilities": {"tools": {}},
            "serverInfo": {"name": "local-llm", "version": "0.1.0"},
        }
    if method == "tools/list":
        return {"tools": TOOLS}
    if method == "tools/call":
        name = params["name"]
        args = params.get("arguments", {})
        try:
            if not BASE_URL or not API_KEY:
                raise RuntimeError("OPENWEBUI_URL and OPENWEBUI_API_KEY must be set")
            if name == "local_llm":
                text = chat(args["prompt"], args.get("system"), args.get("model"))
            elif name == "list_local_models":
                text = "\n".join(list_models())
            else:
                raise RuntimeError(f"unknown tool: {name}")
            return {"content": [{"type": "text", "text": text}]}
        except Exception as e:  # report failures as tool errors, keep server alive
            return {"content": [{"type": "text", "text": f"error: {e}"}], "isError": True}
    return None


def main():
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        req = json.loads(line)
        if "id" not in req:  # notification, no response
            continue
        result = handle(req)
        resp = {"jsonrpc": "2.0", "id": req["id"]}
        if result is None:
            resp["error"] = {"code": -32601, "message": f"method not found: {req.get('method')}"}
        else:
            resp["result"] = result
        sys.stdout.write(json.dumps(resp) + "\n")
        sys.stdout.flush()


if __name__ == "__main__":
    main()
