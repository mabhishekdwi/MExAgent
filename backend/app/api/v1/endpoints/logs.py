from fastapi import APIRouter, Query
from typing import Optional
from app.schemas.log import LogsResponse
from app.utils.logger import get_logs, clear_logs

router = APIRouter()


@router.get("/logs", response_model=LogsResponse)
async def fetch_logs(
    session_id: Optional[str] = Query(default=None),
    since_id: Optional[int]   = Query(default=None),
    limit: int                 = Query(default=50, ge=1, le=500),
):
    logs = await get_logs(since_id=since_id, limit=limit, session_id=session_id)
    return LogsResponse(logs=logs, total=len(logs), session_id=session_id)


@router.delete("/logs", status_code=204)
async def delete_logs():
    await clear_logs()
