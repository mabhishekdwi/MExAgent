import asyncio
from datetime import datetime
from typing import List, Optional
from app.schemas.log import LogMessage

_log_store: List[LogMessage] = []
_log_counter: int = 0
_lock = asyncio.Lock()

# ANSI colour codes for terminal output
_COLORS = {
    "PASS":  "\033[92m",
    "FAIL":  "\033[91m",
    "ACT":   "\033[94m",
    "INFO":  "\033[37m",
    "ERROR": "\033[93m",
}
_RESET = "\033[0m"


async def log(
    level: str,
    message: str,
    screen: Optional[str] = None,
    session_id: Optional[str] = None,
) -> LogMessage:
    global _log_counter
    async with _lock:
        _log_counter += 1
        entry = LogMessage(
            id=_log_counter,
            level=level,
            message=message,
            timestamp=datetime.now().strftime("%H:%M:%S.%f")[:-3],
            screen=screen,
            session_id=session_id,
        )
        _log_store.append(entry)
        _print_log(entry)
        return entry


def _print_log(entry: LogMessage) -> None:
    color = _COLORS.get(entry.level, "\033[37m")
    screen_tag = f" [{entry.screen}]" if entry.screen else ""
    print(f"{color}[{entry.timestamp}] [{entry.level}]{screen_tag} {entry.message}{_RESET}")


async def get_logs(
    since_id: Optional[int] = None,
    limit: int = 100,
    session_id: Optional[str] = None,
) -> List[LogMessage]:
    async with _lock:
        result = list(_log_store)
    if session_id:
        result = [l for l in result if l.session_id == session_id]
    if since_id is not None:
        result = [l for l in result if l.id > since_id]
    return result[-limit:]


async def clear_logs() -> None:
    global _log_store, _log_counter
    async with _lock:
        _log_store = []
        _log_counter = 0
