import os
from dotenv import load_dotenv

# Load from .env file
load_dotenv()

class AgentConfig:
    OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
    AGENT_API_URL = os.getenv("AGENT_API_URL")
    AZURE_ENDPOINT = os.getenv("AZURE_ENDPOINT")
    AZURE_API_VERSION = os.getenv("AZURE_API_VERSION")
