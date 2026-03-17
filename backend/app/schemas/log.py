from pydantic import BaseModel
from typing import Optional, List


class LogMessage(BaseModel):
    id: int
    level: str           # PASS | FAIL | ACT | INFO | ERROR
    message: str
    timestamp: str
    screen: Optional[str] = None
    session_id: Optional[str] = None


class LogsResponse(BaseModel):
    logs: List[LogMessage]
    total: int
    session_id: Optional[str] = None
