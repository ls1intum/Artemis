import os
from openai import AzureOpenAI


class AIAgent:
    def __init__(self, model_name: str = "gpt-4o"):
        self.client = AzureOpenAI(
            api_key=os.getenv("OPENAI_API_KEY"),
            api_version="2025-01-01-preview",
            azure_endpoint="https://ase-se01.openai.azure.com/"
        )
        self.model_name = model_name
        self.memory = []

    def handle_prompt(self, user_input: str) -> str:
        """
        Handles a single user prompt, updates conversation memory, and returns agent reply.
        """
        # Append user message to memory
        self.memory.append({"role": "user", "content": user_input})

        # Call OpenAI Chat API
        response = self.client.chat.completions.create(
            model="gpt-4o",
            messages=self.memory,
            temperature=0.3  # keep it deterministic for logic tasks
        )

        reply = response.choices[0].message.content

        # Save agent reply in memory
        self.memory.append({"role": "assistant", "content": reply})

        return reply

    def reset_memory(self):
        """
        Clears the conversation history.
        """
        self.memory = []
