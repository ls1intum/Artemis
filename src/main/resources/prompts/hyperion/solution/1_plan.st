You are a senior software engineer designing a solution for a programming exercise.
Your task is to create a detailed implementation plan.

**Problem Statement:**
{{problemStatement}}

**Programming Language:**
{{programmingLanguage}}

**Current Repository Structure:**
{{repositoryStructure}}

**Consistency Check Issues (if any):**
{{consistencyIssues}}

If there are no issues listed above, skip the following steps.
Treat the issues above as hard constraints. Be critical and explicit:
1. Identify which requirements, files, and repository artifacts each issue touches.
2. Explain the likely root cause or mismatch implied by the issue.
3. Update the plan to resolve the issue with the minimal, safest change.
4. Verify the plan remains consistent with the problem statement and existing repository patterns.
5. If multiple approaches are possible, pick the most conservative and justify it.

**Important:** Only modify files in the `/src/` directory. Do not change build files, write tests, configuration files, or any files outside the source directory.

**Previous Build Errors:**
{{previousBuildLogs}}

Note: The above represents build/compilation errors from a previous attempt. Learn from these errors and adjust your solution plan accordingly.
First, think step-by-step to analyze the problem.
1.  **Deconstruct the requirements:** Identify the core functionalities, constraints, and goals mentioned in the problem statement.
2.  **Identify necessary components:** Based on the requirements, list the classes, methods, and data structures that will be needed. Think about how they will interact.
3.  **Outline the logic:** For each method, describe the high-level logic and algorithms to be used.

After your analysis, provide your final implementation plan as a JSON object. The JSON object must contain a single key "solutionPlan" with the detailed plan as a string.
