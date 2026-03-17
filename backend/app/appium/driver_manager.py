"""
Manages a single Appium WebDriver session for the lifetime of an exploration run.
"""
import asyncio
from typing import Optional
from appium import webdriver  # type: ignore
from appium.options.android.uiautomator2.base import UiAutomator2Options  # type: ignore
from app.config.settings import settings


class DriverManager:
    def __init__(self):
        self._driver: Optional[webdriver.Remote] = None
        self._lock = asyncio.Lock()

    async def get_driver(self, package_name: Optional[str] = None) -> webdriver.Remote:
        async with self._lock:
            if self._driver is None:
                loop = asyncio.get_event_loop()
                self._driver = await loop.run_in_executor(
                    None, self._create_driver, package_name
                )
            return self._driver

    def _create_driver(self, package_name: Optional[str]) -> webdriver.Remote:
        options = UiAutomator2Options()
        options.platform_name       = "Android"
        options.automation_name     = "UiAutomator2"
        options.no_reset            = True
        options.new_command_timeout = 300

        # Device targeting
        if settings.device_udid:
            options.udid = settings.device_udid
        if settings.device_name:
            options.device_name = settings.device_name
        if settings.platform_version:
            options.platform_version = settings.platform_version

        if package_name:
            options.app_package = package_name

        driver = webdriver.Remote(
            command_executor=settings.appium_url,
            options=options,
        )
        driver.implicitly_wait(settings.appium_timeout)
        return driver

    async def quit(self):
        async with self._lock:
            if self._driver:
                try:
                    loop = asyncio.get_event_loop()
                    await loop.run_in_executor(None, self._driver.quit)
                except Exception:
                    pass
                finally:
                    self._driver = None

    def is_connected(self) -> bool:
        return self._driver is not None


# Module-level singleton
driver_manager = DriverManager()
