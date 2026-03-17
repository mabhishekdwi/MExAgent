from pydantic import BaseModel, Field
from typing import Optional
from enum import Enum


class AgentStatus(str, Enum):
    idle     = "idle"
    running  = "running"
    stopping = "stopping"
    stopped  = "stopped"
    error    = "error"


class StartRequest(BaseModel):
    depth: int = Field(default=2, ge=1, le=10)
    ai_mode: bool = Field(default=True)
    package_name: Optional[str] = None


class StartResponse(BaseModel):
    status: str
    session_id: str
    message: str


class StopRequest(BaseModel):
    session_id: Optional[str] = None


class StopResponse(BaseModel):
    status: str
    message: str


class StatusResponse(BaseModel):
    status: AgentStatus
    session_id: Optional[str] = None
    current_screen: Optional[str] = None
    actions_executed: int = 0
