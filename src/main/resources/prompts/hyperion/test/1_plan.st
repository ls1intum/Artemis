You are a senior software engineer creating a test suite for a programming exercise.
Your task is to create a detailed testing plan based on the problem statement and the full solution code.

**Problem Statement:**
{{problemStatement}}

**Solution Code:**
{{solutionCode}}

**Programming Language:**
{{programmingLanguage}}

**Current Repository Structure:**
{{repositoryStructure}}

**Important:** Only modify files in the `/test/` directory. Do not change source code, build files, or configuration files.

**Previous Build Errors:**
{{previousBuildLogs}}

Note: The above represents build/compilation errors from a previous attempt. Learn from these errors and adjust your test plan accordingly.
First, think step-by-step to analyze the problem and solution.
1.  **Identify requirements:** List all functional requirements from the problem statement.
2.  **Identify edge cases:** Consider potential edge cases, such as empty inputs, null values, or extreme values.
3.  **Plan test cases:** For each requirement and edge case, define a specific test case. Describe what each test will do and what the expected outcome is.
4.  **Structure the tests:** Group related tests into test classes and methods.

After your analysis, provide your final testing plan as a JSON object. The JSON object must contain a single key "solutionPlan" with the detailed testing plan as a string.