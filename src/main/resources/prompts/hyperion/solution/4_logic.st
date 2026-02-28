You are a senior software engineer tasked with completing a programming exercise.
Your task is to implement the core logic for each method based on the solution plan and the existing boilerplate code.

**Solution Plan:**
{{solutionPlan}}

**Existing Files with Headers:**
{{filesWithHeaders}}

**Programming Language:**
{{programmingLanguage}}

**Current Repository Structure:**
{{repositoryStructure}}

**Consistency Check Issues (if any):**
{{consistencyIssues}}

If there are no issues listed above, skip the following steps.
Treat the issues above as hard constraints. Be critical and explicit:
1. Identify which methods, files, and repository artifacts each issue touches.
2. Explain the likely root cause in the current code, headers, or plan.
3. Choose the minimal, safest logic change that resolves the issue without introducing new behavior.
4. Verify the fix aligns with the problem statement, the plan, and existing repository patterns.
5. If multiple fixes are possible, pick the most conservative change and justify it.

**Important:** Only modify files in the `/src/` directory. Do not change build files, write tests, configuration files, or any files outside the source directory.
First, think step-by-step for each method.
1.  **Understand the goal:** Read the method's docstring and the relevant part of the solution plan.
2.  **Implement the logic:** Write clean, efficient, and correct code to fulfill the requirements.
3.  **Ensure completeness:** Make sure all methods from the plan are fully implemented.

After your analysis, provide the final code as a JSON object. The JSON object must contain a single key "files" which is an array of objects, where each object has a "path" and "content" key with the complete code.
