from pydantic import BaseModel
from typing import Optional, List


class UIElement(BaseModel):
    type: str                          # button | input | text | link | etc.
    label: Optional[str] = None
    text: Optional[str] = None
    resource_id: Optional[str] = None
    content_desc: Optional[str] = None
    bounds: Optional[str] = None
    clickable: bool = False
    enabled: bool = True
    focused: bool = False
    index: int = 0


class ScreenContext(BaseModel):
    screen: str
    package: Optional[str] = None
    activity: Optional[str] = None
    elements: List[UIElement]


class TestAction(BaseModel):
    action: str                        # tap | type | scroll | back | swipe | long_press
    target: Optional[str] = None
    value: Optional[str] = None
    index: Optional[int] = None
    direction: Optional[str] = None   # up | down | left | right


class TestPlan(BaseModel):
    screen_type: str
    goal: str
    test_plan: List[TestAction]
