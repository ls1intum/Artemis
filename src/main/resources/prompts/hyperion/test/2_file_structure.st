You are a senior software engineer designing a test suite for a programming exercise.
Based on the following testing plan and programming language, determine the necessary file structure for the tests.

**Testing Plan:**
{{solutionPlan}}

**Programming Language:**
{{programmingLanguage}}

**Current Repository Structure:**
{{repositoryStructure}}

**Build Environment Context:**
{{buildEnvironmentContext}}

**Consistency Check Issues (if any):**
{{consistencyIssues}}

If there are no issues listed above, skip the following steps.
**Hard Safety Rules:**
1. Use only classes, packages, and utilities that exist in the provided repository context.
2. Do not add files for invented helpers such as `TestDataFactory`, `TestUtils`, fixture classes, builders, or custom assertions unless they are explicitly required by the plan and you also generate them in the output.
3. Prefer the smallest file set that satisfies the testing plan and compiles against the provided codebase.
4. Keep package names and paths consistent with the existing repository layout and testing conventions.

If both the consistency issues section and the selected feedback threads section above are empty or None, skip the following steps. Otherwise, proceed.
Treat the issues above as hard constraints. Be critical and explicit:
1. Identify which test files, packages, and repository artifacts each issue touches.
2. Explain the mismatch between the required structure and the current or planned structure.
3. Propose the minimal structural changes needed to resolve the issue.
4. Verify the structure stays consistent with the testing plan and repository conventions.
5. If multiple structures are possible, pick the most conservative and justify it.

**Important:** Only modify files in the `/test/` directory. Do not change source code, build files, or configuration files.
First, think step-by-step.
1.  **Analyze the plan:** Review the test classes and cases mentioned in the testing plan.
2.  **Apply conventions:** Consider the standard project layout and file naming conventions for tests in the given programming language (e.g., `src/test/java`, `*Test.java`).
3.  **Check for missing dependencies:** Decide whether the plan requires any helper or support file. Only include such a file if it is explicitly necessary and will be generated.
4.  **List the files:** Create a minimal list of all test files that need to be created.

After your analysis, provide the file structure as a JSON object. The JSON object must contain a single key "files" which is an array of objects, where each object has a "path" key.
