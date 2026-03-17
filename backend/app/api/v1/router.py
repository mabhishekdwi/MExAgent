from fastapi import APIRouter
from app.api.v1.endpoints import agent, logs, health

router = APIRouter()
router.include_router(agent.router,  tags=["agent"])
router.include_router(logs.router,   tags=["logs"])
router.include_router(health.router, tags=["health"])
