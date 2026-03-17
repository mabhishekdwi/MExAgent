"""
Core exploration loop.

Flow per screen:
  1. Capture XML page source via Appium
  2. Parse into ScreenContext (human-readable elements only)
  3. Log visible elements (PASS)
  4. Ask LLM (or heuristic) to generate a test plan
  5. Execute each action → log PASS / FAIL
  6. If navigation occurred and depth budget remains → recurse
  7. Press Back, continue remaining actions on parent screen
"""
import asyncio
from typing import Optional, Set
from app.appium.driver_manager import driver_manager
from app.appium.action_executor import execute_action
from app.parser.xml_parser import parse_xml_to_screen_context, screen_context_to_dict
from app.llm.llm_client import generate_test_plan
from app.schemas.action import TestAction
from app.utils import logger
from app.utils import highlight_state


# ── Shared mutable state (one exploration at a time) ─────────────────────────

class ExplorerState:
    def __init__(self):
        self.session_id: str = ""
        self.running: bool = False
        self.current_screen: str = "Unknown"
        self.actions_executed: int = 0
        self.visited_screens: Set[str] = set()

_state = ExplorerState()


def get_state() -> ExplorerState:
    return _state


# ── Helpers ───────────────────────────────────────────────────────────────────

async def _page_source(driver) -> str:
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, lambda: driver.page_source)

async def _current_activity(driver) -> str:
    loop = asyncio.get_event_loop()
    try:
        return await loop.run_in_executor(None, lambda: driver.current_activity)
    except Exception:
        return ""

async def _current_package(driver) -> str:
    loop = asyncio.get_event_loop()
    try:
        return await loop.run_in_executor(None, lambda: driver.current_package)
    except Exception:
        return ""

async def _press_back(driver) -> None:
    loop = asyncio.get_event_loop()
    await loop.run_in_executor(None, driver.back)
    await asyncio.sleep(1.2)


# ── Recursive screen explorer ─────────────────────────────────────────────────

async def _explore_screen(
    driver,
    session_id: str,
    current_depth: int,
    max_depth: int,
    ai_mode: bool,
) -> None:
    if current_depth > max_depth or not _state.running:
        return

    # ── 1. Capture UI state ────────────────────────────────────────────────
    xml      = await _page_source(driver)
    activity = await _current_activity(driver)
    package  = await _current_package(driver)

    screen_ctx = parse_xml_to_screen_context(xml, package=package, activity=activity)
    screen_name = screen_ctx.screen
    _state.current_screen = screen_name

    # ── 2. Loop-detection via activity fingerprint ─────────────────────────
    screen_key = activity or screen_name
    if screen_key in _state.visited_screens:
        await logger.log("INFO", f"Already visited '{screen_name}', skipping",
                         session_id=session_id)
        return
    _state.visited_screens.add(screen_key)

    await logger.log("INFO",
                     f"[Depth {current_depth}/{max_depth}] Exploring: {screen_name}",
                     screen=screen_name, session_id=session_id)

    # ── 3. Log visible elements ────────────────────────────────────────────
    elements = screen_ctx.elements
    if not elements:
        await logger.log("INFO", "No interactable elements found",
                         screen=screen_name, session_id=session_id)
        return

    for elem in elements:
        if elem.label:
            highlight_state.set_highlight(
                elem.bounds, elem.label or "", elem.type, "PASS", screen_name
            )
            await asyncio.sleep(0.8)
            await logger.log("PASS",
                             f"{elem.type.capitalize()} visible: \"{elem.label}\"",
                             screen=screen_name, session_id=session_id)

    # ── 4. Generate test plan ──────────────────────────────────────────────
    screen_dict = screen_context_to_dict(screen_ctx)

    if ai_mode:
        await logger.log("INFO", "Generating AI test plan...",
                         screen=screen_name, session_id=session_id)
        plan = await generate_test_plan(screen_dict)
        await logger.log("INFO",
                         f"Goal: {plan.goal}  ({len(plan.test_plan)} actions)",
                         screen=screen_name, session_id=session_id)
        actions = plan.test_plan
    else:
        actions = [
            TestAction(action="tap", target=e.label or str(e.index))
            for e in elements if e.clickable and e.enabled
        ]

    # ── 5. Execute actions ─────────────────────────────────────────────────
    for action in actions:
        if not _state.running:
            break

        val_tag = f" = \"{action.value}\"" if action.value else ""
        await logger.log("ACT",
                         f"{action.action.upper()} \"{action.target}\"{val_tag}",
                         screen=screen_name, session_id=session_id)

        # Find element bounds for visual highlight
        target_elem = next(
            (e for e in elements if e.label == action.target or str(e.index) == str(action.target)),
            None
        )
        highlight_state.set_highlight(
            target_elem.bounds if target_elem else None,
            action.target or "",
            target_elem.type if target_elem else "button",
            "ACT",
            screen_name,
        )

        pre_activity = await _current_activity(driver)
        success, msg = await execute_action(driver, action, screen_ctx)
        _state.actions_executed += 1

        if success:
            if target_elem:
                highlight_state.set_highlight(
                    target_elem.bounds, action.target or "", target_elem.type, "PASS", screen_name
                )
            await logger.log("PASS", msg, screen=screen_name, session_id=session_id)

            # Check for navigation — wait for page to load first
            if action.action.lower() in ("tap", "long_press"):
                await asyncio.sleep(1.5)  # wait for new screen/elements to settle
                new_activity = await _current_activity(driver)
                if new_activity and new_activity != pre_activity:
                    await logger.log("PASS",
                                     f"Navigated to new screen: {new_activity}",
                                     screen=screen_name, session_id=session_id)
                    if current_depth < max_depth:
                        await _explore_screen(
                            driver, session_id,
                            current_depth + 1, max_depth, ai_mode
                        )
                    # Return to parent screen
                    await logger.log("ACT", "Navigate back",
                                     screen=screen_name, session_id=session_id)
                    await _press_back(driver)
        else:
            if target_elem:
                highlight_state.set_highlight(
                    target_elem.bounds, action.target or "", target_elem.type, "FAIL", screen_name
                )
            await logger.log("FAIL", msg, screen=screen_name, session_id=session_id)


# ── Public entry points ───────────────────────────────────────────────────────

async def start_exploration(
    session_id: str,
    depth: int = 2,
    ai_mode: bool = True,
    package_name: Optional[str] = None,
    action_delay_ms: Optional[int] = None,
) -> None:
    from app.config.settings import settings
    if action_delay_ms is not None:
        settings.action_delay_ms = action_delay_ms

    _state.session_id       = session_id
    _state.running          = True
    _state.current_screen   = "Unknown"
    _state.actions_executed = 0
    _state.visited_screens  = set()

    await logger.log(
        "INFO",
        f"=== MExAgent started | session={session_id} | depth={depth} | ai={ai_mode} ===",
        session_id=session_id,
    )

    try:
        driver = await driver_manager.get_driver(package_name)
        await _explore_screen(driver, session_id, 1, depth, ai_mode)
    except Exception as exc:
        await logger.log("ERROR", f"Exploration crashed: {exc}", session_id=session_id)
        raise
    finally:
        _state.running = False
        highlight_state.clear_highlight()
        await logger.log(
            "INFO",
            f"=== Exploration complete | {_state.actions_executed} actions ===",
            session_id=session_id,
        )


async def stop_exploration() -> None:
    _state.running = False
    await driver_manager.quit()
    await logger.log("INFO", "Agent stopped by user", session_id=_state.session_id)
