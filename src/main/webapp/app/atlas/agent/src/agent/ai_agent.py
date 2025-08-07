import os
from openai import AzureOpenAI


class AIAgent:
    def __init__(self, model_name: str = "gpt-4o", azure_endpoint: str = None, api_version: str = None):
        api_key = os.getenv("OPENAI_API_KEY")
        if not api_key:
            raise ValueError("OPENAI_API_KEY environment variable is required")

        self.client = AzureOpenAI(
            api_key=api_key,
            api_version=api_version or os.getenv("OPENAI_API_VERSION", "2025-01-01-preview"),
            azure_endpoint=azure_endpoint or os.getenv("AZURE_OPENAI_ENDPOINT", "https://ase-se01.openai.azure.com/")
        )
        self.model_name = model_name
        self.memory = []

    def handle_prompt(self, user_input: str) -> str:
        """
        Handles a single user prompt, updates conversation memory, and returns agent reply.
        """
        if not user_input or not user_input.strip():
         raise ValueError("User input cannot be empty")
        # Append user message to memory
        self.memory.append({"role": "user", "content": user_input})
        try:
            # Call OpenAI Chat API
            response = self.client.chat.completions.create(
                model=self.model_name,
                messages=self.memory,
                temperature=0.3
            )

            reply = response.choices[0].message.content
            if not reply:
                raise ValueError("Empty response from OpenAI API")

        except Exception as e:
            # Remove the user message if API call fails
            self.memory.pop()
            raise RuntimeError(f"Failed to get response from OpenAI: {str(e)}")

        # Save agent reply in memory
        self.memory.append({"role": "assistant", "content": reply})

        return reply

    def reset_memory(self):
        """
        Clears the conversation history.
        """
        self.memory = []
