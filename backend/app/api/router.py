from fastapi import APIRouter

from app.api.v1.routes.community import router as community_router
from app.api.v1.routes.home import router as home_router
from app.api.v1.routes.profile import router as profile_router

api_router = APIRouter()
api_router.include_router(community_router)
api_router.include_router(home_router)
api_router.include_router(profile_router)
