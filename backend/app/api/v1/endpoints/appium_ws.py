"""
WebSocket-based Appium proxy.

PC agent connects here via WebSocket (outbound from PC, always works).
Explorer calls /appium-proxy/... HTTP endpoints.
Backend forwards those HTTP requests through the WebSocket to the PC agent,
which calls local Appium and sends the response back.

No tunnel needed. No firewall issues.
"""
import asyncio
import json
import uuid
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Request, HTTPException
from fastapi.responses import Response

router = APIRouter()


class AppiumProxyManager:
    def __init__(self):
        self._ws: WebSocket | None = None
        self._pending: dict[str, asyncio.Future] = {}

    @property
    def connected(self) -> bool:
        return self._ws is not None

    async def accept(self, ws: WebSocket):
        await ws.accept()
        self._ws = ws
        from app.config.settings import settings
        import os
        port = os.environ.get("PORT", str(settings.port))
        settings.appium_url = f"http://localhost:{port}/appium-proxy"
        print(f"[proxy] PC agent connected — appium_url set to {settings.appium_url}")

    async def disconnect(self):
        self._ws = None
        for fut in self._pending.values():
            if not fut.done():
                fut.set_exception(ConnectionError("PC agent disconnected"))
        self._pending.clear()
        print("[proxy] PC agent disconnected")

    async def forward(self, method: str, path: str, body: bytes) -> tuple[int, str]:
        if not self._ws:
            raise HTTPException(status_code=503, detail="PC agent not connected — run mexagent-pc.exe first")
        req_id = str(uuid.uuid4())
        loop = asyncio.get_event_loop()
        future = loop.create_future()
        self._pending[req_id] = future
        try:
            await self._ws.send_text(json.dumps({
                "id": req_id,
                "method": method,
                "path": path,
                "body": body.decode("utf-8", errors="replace") if body else "",
            }))
            result = await asyncio.wait_for(future, timeout=120)
            return result.get("status", 200), result.get("body", "")
        except asyncio.TimeoutError:
            raise HTTPException(status_code=504, detail="Appium request timed out")
        finally:
            self._pending.pop(req_id, None)

    async def handle_message(self, data: str):
        try:
            msg = json.loads(data)
            req_id = msg.get("id")
            if req_id and req_id in self._pending:
                fut = self._pending.pop(req_id)
                if not fut.done():
                    fut.set_result(msg)
        except Exception:
            pass


proxy_manager = AppiumProxyManager()


@router.websocket("/ws/appium")
async def appium_websocket(ws: WebSocket):
    await proxy_manager.accept(ws)
    try:
        while True:
            text = await ws.receive_text()
            await proxy_manager.handle_message(text)
    except WebSocketDisconnect:
        await proxy_manager.disconnect()


@router.api_route("/appium-proxy/{path:path}", methods=["GET", "POST", "DELETE", "PUT"])
async def appium_proxy(request: Request, path: str):
    body = await request.body()
    status, response_body = await proxy_manager.forward(
        method=request.method,
        path=f"/{path}",
        body=body,
    )
    return Response(content=response_body, status_code=status, media_type="application/json")
