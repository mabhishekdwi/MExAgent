import httpx
from fastapi import APIRouter

router = APIRouter()


@router.get("/connection-check")
async def connection_check():
    """
    Called by the Android app's Settings screen.
    Returns status of the backend itself and the Appium server.
    """
    from app.config.settings import settings

    appium_ok = False
    appium_version = None
    appium_error = None

    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            r = await client.get(
                f"{settings.appium_url}/status",
                headers={"Bypass-Tunnel-Reminder": "1"},
            )
            if r.status_code == 200:
                data = r.json()
                appium_ok = data.get("value", {}).get("ready", False)
                appium_version = data.get("value", {}).get("build", {}).get("version")
    except Exception as e:
        appium_error = str(e)

    return {
        "backend": "ok",
        "appium": {
            "connected": appium_ok,
            "url": settings.appium_url,
            "version": appium_version,
            "error": appium_error,
        },
        "device": {
            "udid": settings.device_udid,
            "name": settings.device_name,
            "platform_version": settings.platform_version,
        }
    }
