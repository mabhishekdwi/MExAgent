"""
LLM prompt templates for screen analysis and test plan generation.
"""
from typing import Dict, Any

SYSTEM_PROMPT = """You are an expert Android QA engineer performing exploratory testing.

Your task: analyze a mobile screen's UI elements and produce a JSON test plan.

Rules:
1. Identify the screen type and primary user goal.
2. Generate test actions in priority order (critical paths first).
3. For input fields supply realistic test data:
   - Username/email fields  → testuser / test@example.com
   - Password fields        → Password123!
   - Name fields            → John Doe
   - Phone fields           → +1-555-0123
   - Search fields          → search term relevant to the app
4. Always prefer tapping the primary CTA (Login, Submit, Continue) after filling inputs.
5. Include at least one negative test if a clear opportunity exists.
6. Limit to 8 actions maximum per screen.

Output ONLY valid JSON — no prose, no markdown code fences:
{
  "screen_type": "<login|home|form|list|detail|settings|error|onboarding|unknown>",
  "goal": "<one-sentence description of this screen's purpose>",
  "test_plan": [
    {"action": "<tap|type|scroll|back|swipe>", "target": "<element label or index number>", "value": "<string — required for type actions>"},
    ...
  ]
}"""


def build_screen_prompt(screen_ctx: Dict[str, Any]) -> str:
    """Build the user-turn message for screen analysis."""
    lines = []
    for elem in screen_ctx.get("elements", []):
        idx       = elem.get("index", "?")
        etype     = elem.get("type", "view")
        label     = elem.get("label", "")
        clickable = "clickable" if elem.get("clickable") else "read-only"
        val       = f'  current="{elem["current_value"]}"' if elem.get("current_value") else ""
        lines.append(f"  [{idx}] {etype}: {label}  ({clickable}){val}")

    elements_block = "\n".join(lines) or "  (no visible elements)"

    return (
        f"Screen name : {screen_ctx.get('screen', 'Unknown')}\n"
        f"Activity    : {screen_ctx.get('activity', 'N/A')}\n\n"
        f"UI Elements :\n{elements_block}\n\n"
        f"Generate the test plan JSON now."
    )
