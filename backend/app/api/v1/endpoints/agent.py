import uuid
from fastapi import APIRouter, HTTPException, BackgroundTasks
from app.schemas.agent import (
    StartRequest, StartResponse,
    StopRequest, StopResponse,
    StatusResponse, AgentStatus,
)
from app.crawler.explorer import start_exploration, stop_exploration, get_state

router = APIRouter()


@router.post("/start", response_model=StartResponse)
async def start_agent(request: StartRequest, background_tasks: BackgroundTasks):
    state = get_state()
    if state.running:
        raise HTTPException(status_code=409, detail="Agent is already running")

    session_id = str(uuid.uuid4())[:8]
    background_tasks.add_task(
        start_exploration,
        session_id=session_id,
        depth=request.depth,
        ai_mode=request.ai_mode,
        package_name=request.package_name,
    )
    return StartResponse(
        status="started",
        session_id=session_id,
        message=f"Exploration started — depth={request.depth} ai_mode={request.ai_mode}",
    )


@router.post("/stop", response_model=StopResponse)
async def stop_agent(_: StopRequest):
    await stop_exploration()
    return StopResponse(status="stopped", message="Agent stopped")


@router.get("/status", response_model=StatusResponse)
async def get_status():
    state = get_state()
    if state.running:
        status = AgentStatus.running
    elif state.session_id:
        status = AgentStatus.stopped
    else:
        status = AgentStatus.idle

    return StatusResponse(
        status=status,
        session_id=state.session_id or None,
        current_screen=state.current_screen,
        actions_executed=state.actions_executed,
    )
