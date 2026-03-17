from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.v1.router import router

app = FastAPI(
    title="MExAgent Backend",
    description="Autonomous mobile exploratory testing agent — Think → Plan → Execute",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router)


@app.get("/", tags=["root"])
async def root():
    return {
        "message": "MExAgent Backend is running",
        "docs": "/docs",
        "endpoints": ["/start", "/stop", "/status", "/logs", "/health"],
    }


if __name__ == "__main__":
    import uvicorn
    from app.config.settings import settings
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
    )
