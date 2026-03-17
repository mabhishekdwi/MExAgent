"""
LLM client — supports Groq (cloud) and Ollama (local).
Falls back to heuristic exploration when LLM is unavailable.
"""
import json
import re
from typing import Dict, Any
from app.config.settings import settings
from app.schemas.action import TestPlan, TestAction
from app.llm.prompt_builder import SYSTEM_PROMPT, build_screen_prompt


# ── Raw LLM calls ────────────────────────────────────────────────────────────

async def _call_groq(user_prompt: str) -> str:
    from groq import Groq  # type: ignore
    client = Groq(api_key=settings.groq_api_key)
    response = client.chat.completions.create(
        model=settings.groq_model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user",   "content": user_prompt},
        ],
        temperature=0.15,
        max_tokens=800,
    )
    return response.choices[0].message.content


async def _call_ollama(user_prompt: str) -> str:
    import httpx
    async with httpx.AsyncClient(timeout=60) as client:
        resp = await client.post(
            f"{settings.ollama_url}/api/chat",
            json={
                "model": settings.ollama_model,
                "messages": [
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user",   "content": user_prompt},
                ],
                "stream": False,
                "options": {"temperature": 0.15},
            },
        )
        resp.raise_for_status()
        return resp.json()["message"]["content"]


# ── JSON extraction ───────────────────────────────────────────────────────────

def _extract_json(text: str) -> Dict[str, Any]:
    """Pull the first valid JSON object out of LLM response text."""
    # Direct parse (model returned clean JSON)
    try:
        return json.loads(text.strip())
    except json.JSONDecodeError:
        pass

    # Strip markdown code fences
    for pattern in (r"```json\s*([\s\S]+?)\s*```", r"```\s*([\s\S]+?)\s*```"):
        m = re.search(pattern, text)
        if m:
            try:
                return json.loads(m.group(1))
            except json.JSONDecodeError:
                pass

    # Find first {...} blob
    m = re.search(r"(\{[\s\S]+\})", text)
    if m:
        try:
            return json.loads(m.group(1))
        except json.JSONDecodeError:
            pass

    raise ValueError(f"No valid JSON in LLM response: {text[:300]}")


# ── Public API ────────────────────────────────────────────────────────────────

async def generate_test_plan(screen_context: Dict[str, Any]) -> TestPlan:
    """
    Ask the configured LLM to produce a test plan for `screen_context`.
    Falls back to tapping all clickable elements if LLM is unavailable.
    """
    prompt = build_screen_prompt(screen_context)

    try:
        if settings.llm_provider == "groq":
            raw = await _call_groq(prompt)
        else:
            raw = await _call_ollama(prompt)

        data = _extract_json(raw)

        actions = [
            TestAction(
                action=a.get("action", "tap"),
                target=str(a.get("target", "")),
                value=a.get("value"),
                direction=a.get("direction"),
                index=a.get("index"),
            )
            for a in data.get("test_plan", [])
        ]

        return TestPlan(
            screen_type=data.get("screen_type", "unknown"),
            goal=data.get("goal", "Explore screen"),
            test_plan=actions,
        )

    except Exception as e:
        # Heuristic fallback
        actions = [
            TestAction(action="tap", target=e.get("label") or str(e.get("index", 0)))
            for e in screen_context.get("elements", [])
            if e.get("clickable") and e.get("enabled", True)
        ][:settings.max_actions_per_screen]

        return TestPlan(
            screen_type="unknown",
            goal=f"Heuristic fallback (LLM error: {type(e).__name__})",
            test_plan=actions,
        )
