from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from agent.ai_agent import AIAgent
import logging

logger = logging.getLogger(__name__)
app = FastAPI(title="Conversational AI Agent")
agent = AIAgent(model_name="gpt-4o")

class ChatRequest(BaseModel):
    message: str

@app.post("/chat")
async def chat_endpoint(req: ChatRequest):
    try:
        reply = agent.handle_prompt(req.message)
        return {"reply": reply}
    except ValueError as e:
        logger.warning(f"Invalid input: {e}")
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.error(f"Error processing chat request: {e}")
        raise HTTPException(status_code=500, detail="Internal server error")

# Keep CLI only if needed for local testing:
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
