from pydantic_settings import BaseSettings
from pydantic import Field
from typing import Optional


class Settings(BaseSettings):
    # Appium
    appium_url: str = Field(default="http://localhost:4723", env="APPIUM_URL")
    appium_timeout: int = Field(default=30, env="APPIUM_TIMEOUT")

    # LLM
    llm_provider: str = Field(default="groq", env="LLM_PROVIDER")   # groq | ollama
    groq_api_key: Optional[str] = Field(default=None, env="GROQ_API_KEY")
    groq_model: str = Field(default="llama3-70b-8192", env="GROQ_MODEL")
    ollama_url: str = Field(default="http://localhost:11434", env="OLLAMA_URL")
    ollama_model: str = Field(default="llama3", env="OLLAMA_MODEL")

    # Agent behaviour
    default_depth: int = Field(default=2, env="DEFAULT_DEPTH")
    max_actions_per_screen: int = Field(default=10, env="MAX_ACTIONS_PER_SCREEN")
    action_delay_ms: int = Field(default=800, env="ACTION_DELAY_MS")

    # Server
    host: str = Field(default="0.0.0.0", env="HOST")
    port: int = Field(default=8000, env="PORT")
    debug: bool = Field(default=False, env="DEBUG")

    class Config:
        env_file = ".env"
        extra = "ignore"


settings = Settings()
