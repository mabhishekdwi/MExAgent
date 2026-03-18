from fastapi import APIRouter

router = APIRouter()


@router.get("/connection-check")
async def connection_check():
    from app.config.settings import settings
    from app.api.v1.endpoints.appium_ws import proxy_manager

    appium_ok = proxy_manager.connected

    return {
        "backend": "ok",
        "appium": {
            "connected": appium_ok,
            "url": settings.appium_url,
            "version": None,
            "error": None if appium_ok else "PC agent not connected — run mexagent-pc.exe",
        },
        "device": {
            "udid": settings.device_udid,
            "name": settings.device_name,
            "platform_version": settings.platform_version,
        }
    }
