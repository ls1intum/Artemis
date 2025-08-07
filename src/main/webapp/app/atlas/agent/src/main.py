from fastapi import FastAPI
from fastapi import HTTPException
from pydantic import BaseModel
from ai_agent import AIAgent
import logging
app = FastAPI(title="Conversational AI Agent")
agent = AIAgent(model_name="gpt-4o")
logger = logging.getLogger(__name__)
class ChatRequest(BaseModel):
    message: str

@app.post("/chat")
async def chat_endpoint(req: ChatRequest):
        try:
            logger.info(f"Received chat request: {req.message[:50]}...")
            # If handle_prompt is CPU-intensive, consider running in thread pool
            reply = agent.handle_prompt(req.message)
            return {"reply": reply}
        except Exception as e:
            logger.error(f"Error processing chat request: {e}")
            raise HTTPException(status_code=500, detail="Internal server error") from e

# Keep CLI only if needed for local testing:
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
