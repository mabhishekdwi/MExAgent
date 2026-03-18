from fastapi import APIRouter
from app.api.v1.endpoints import agent, logs, health, highlight, connection, configure

router = APIRouter()
router.include_router(agent.router,      tags=["agent"])
router.include_router(logs.router,       tags=["logs"])
router.include_router(health.router,     tags=["health"])
router.include_router(highlight.router,  tags=["highlight"])
router.include_router(connection.router, tags=["connection"])
router.include_router(configure.router,  tags=["configure"])
