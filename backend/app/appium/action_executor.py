"""
Maps LLM-generated TestActions to Appium WebDriver calls.

Supported actions:
  tap        → element.click()
  type       → element.clear() + send_keys()
  scroll     → driver.swipe() (directional)
  back       → driver.back()
  long_press → mobile: longClickGesture
  swipe      → alias for scroll
"""
import asyncio
import re
from typing import Optional, Tuple
from appium import webdriver  # type: ignore
from appium.webdriver.common.appiumby import AppiumBy  # type: ignore
from selenium.common.exceptions import (
    NoSuchElementException, TimeoutException, WebDriverException,
)
from app.schemas.action import TestAction, UIElement, ScreenContext
from app.config.settings import settings


# ── Element finder ────────────────────────────────────────────────────────────

def _resolve_element(
    driver: webdriver.Remote,
    action: TestAction,
    screen_ctx: ScreenContext,
):
    """
    Find the Selenium WebElement that best matches action.target.
    Tries resource-id, accessibility-id, text, then bounds tap.
    """
    target = (action.target or "").strip()

    # Resolve numeric index → UIElement
    matching: Optional[UIElement] = None
    if target.isdigit():
        idx = int(target)
        for e in screen_ctx.elements:
            if e.index == idx:
                matching = e
                break
    else:
        for e in screen_ctx.elements:
            candidates = [
                e.label or "",
                e.text or "",
                e.content_desc or "",
                str(e.index),
            ]
            if any(target.lower() in c.lower() for c in candidates if c):
                matching = e
                break

    if not matching:
        raise NoSuchElementException(
            f"No element matching '{target}' in current screen context"
        )

    # Try locator strategies in order of reliability
    strategies: list = []
    if matching.resource_id:
        strategies.append((AppiumBy.ID, matching.resource_id))
    if matching.content_desc:
        strategies.append((AppiumBy.ACCESSIBILITY_ID, matching.content_desc))
    if matching.text:
        strategies.append((
            AppiumBy.ANDROID_UIAUTOMATOR,
            f'new UiSelector().text("{matching.text}")',
        ))
    if matching.bounds:
        strategies.append(("_bounds_", matching.bounds))

    for strategy, value in strategies:
        try:
            if strategy == "_bounds_":
                coords = _bounds_center(value)
                if coords:
                    driver.tap([coords])
                    return None   # tapped via coordinates — no element object
            else:
                el = driver.find_element(strategy, value)
                if el:
                    return el
        except (NoSuchElementException, WebDriverException):
            continue

    raise NoSuchElementException(
        f"Could not locate '{target}' via any locator strategy"
    )


def _bounds_center(bounds: str) -> Optional[Tuple[int, int]]:
    nums = list(map(int, re.findall(r"\d+", bounds)))
    if len(nums) == 4:
        return ((nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2)
    return None


# ── Action dispatcher ─────────────────────────────────────────────────────────

async def execute_action(
    driver: webdriver.Remote,
    action: TestAction,
    screen_ctx: ScreenContext,
) -> Tuple[bool, str]:
    """
    Execute a single action on the device.
    Returns (success: bool, human_readable_message: str).
    """
    loop  = asyncio.get_event_loop()
    delay = settings.action_delay_ms / 1000.0
    act   = action.action.lower()

    try:
        # ── TAP ──────────────────────────────────────────────────────────────
        if act == "tap":
            def _tap():
                el = _resolve_element(driver, action, screen_ctx)
                if el:
                    el.click()
                return f"Tapped '{action.target}'"
            msg = await loop.run_in_executor(None, _tap)
            await asyncio.sleep(delay)
            return True, msg

        # ── TYPE ─────────────────────────────────────────────────────────────
        elif act == "type":
            def _type():
                el = _resolve_element(driver, action, screen_ctx)
                if el:
                    el.clear()
                    el.send_keys(action.value or "")
                return f"Typed '{action.value}' into '{action.target}'"
            msg = await loop.run_in_executor(None, _type)
            await asyncio.sleep(delay)
            return True, msg

        # ── SCROLL / SWIPE ────────────────────────────────────────────────────
        elif act in ("scroll", "swipe"):
            direction = (action.direction or "down").lower()

            def _scroll():
                size = driver.get_window_size()
                w, h = size["width"], size["height"]
                vectors = {
                    "down":  (w // 2, int(h * 0.70), w // 2, int(h * 0.30)),
                    "up":    (w // 2, int(h * 0.30), w // 2, int(h * 0.70)),
                    "left":  (int(w * 0.80), h // 2, int(w * 0.20), h // 2),
                    "right": (int(w * 0.20), h // 2, int(w * 0.80), h // 2),
                }
                x1, y1, x2, y2 = vectors.get(direction, vectors["down"])
                driver.swipe(x1, y1, x2, y2, 800)
                return f"Scrolled {direction}"
            msg = await loop.run_in_executor(None, _scroll)
            await asyncio.sleep(delay)
            return True, msg

        # ── BACK ──────────────────────────────────────────────────────────────
        elif act == "back":
            await loop.run_in_executor(None, driver.back)
            await asyncio.sleep(delay)
            return True, "Pressed Back"

        # ── LONG PRESS ────────────────────────────────────────────────────────
        elif act == "long_press":
            def _long_press():
                el = _resolve_element(driver, action, screen_ctx)
                if el:
                    driver.execute_script(
                        "mobile: longClickGesture",
                        {"elementId": el.id, "duration": 2000},
                    )
                return f"Long-pressed '{action.target}'"
            msg = await loop.run_in_executor(None, _long_press)
            await asyncio.sleep(delay)
            return True, msg

        else:
            return False, f"Unknown action type: '{act}'"

    except NoSuchElementException:
        return False, f"Element not found: '{action.target}'"
    except TimeoutException:
        return False, f"Timeout waiting for: '{action.target}'"
    except WebDriverException as exc:
        return False, f"WebDriver error: {str(exc)[:120]}"
    except Exception as exc:
        return False, f"Error: {str(exc)[:120]}"
