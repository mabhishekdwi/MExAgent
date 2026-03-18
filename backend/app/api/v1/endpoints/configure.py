"""
POST /configure
Called by the PC startup script after ngrok starts.
Updates APPIUM_URL + device info at runtime without restarting the server.
"""
from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel
from typing import Optional
from app.config.settings import settings

router = APIRouter()


class ConfigureRequest(BaseModel):
    appium_url: str                        # e.g. https://abc123.ngrok-free.app
    device_udid: Optional[str] = None
    device_name: Optional[str] = None
    platform_version: Optional[str] = None
    secret: Optional[str] = None          # optional shared secret for basic auth


@router.post("/configure")
async def configure(req: ConfigureRequest):
    # Optional: protect with a shared secret set via CONFIGURE_SECRET env var
    expected = getattr(settings, "configure_secret", None)
    if expected and req.secret != expected:
        raise HTTPException(status_code=401, detail="Invalid secret")

    settings.appium_url = req.appium_url
    if req.device_udid:
        settings.device_udid = req.device_udid
    if req.device_name:
        settings.device_name = req.device_name
    if req.platform_version:
        settings.platform_version = req.platform_version

    return {
        "status": "ok",
        "appium_url": settings.appium_url,
        "device_udid": settings.device_udid,
        "device_name": settings.device_name,
        "platform_version": settings.platform_version,
    }


@router.get("/configure")
async def get_config():
    """Returns current runtime configuration (for debugging)."""
    return {
        "appium_url": settings.appium_url,
        "device_udid": settings.device_udid,
        "device_name": settings.device_name,
        "platform_version": settings.platform_version,
        "llm_provider": settings.llm_provider,
    }
