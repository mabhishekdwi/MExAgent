from fastapi import APIRouter
from app.appium.driver_manager import driver_manager

router = APIRouter()


@router.get("/health")
async def health():
    return {
        "status": "ok",
        "appium_connected": driver_manager.is_connected(),
    }
