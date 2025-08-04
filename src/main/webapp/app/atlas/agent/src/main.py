from fastapi import FastAPI
from pydantic import BaseModel
from agent.ai_agent import AIAgent

app = FastAPI(title="Conversational AI Agent")
agent = AIAgent(model_name="gpt-4o")

class ChatRequest(BaseModel):
    message: str

@app.post("/chat")
async def chat_endpoint(req: ChatRequest):
    reply = agent.handle_prompt(req.message)
    return {"reply": reply}

# Keep CLI only if needed for local testing:
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
