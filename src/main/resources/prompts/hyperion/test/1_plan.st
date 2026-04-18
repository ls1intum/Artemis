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

**Build Environment Context:**
{{buildEnvironmentContext}}

**Consistency Check Issues (if any):**
{{consistencyIssues}}

If there are no issues listed above, skip the following steps.
**Hard Safety Rules:**
1. Use only classes, constructors, methods, fields, packages, and imports that exist in the provided repository context.
2. Do not invent helper classes, factories, fixtures, builders, utilities, base test classes, or custom assertions unless they already exist in the provided context or you explicitly plan to generate them in the test output.
3. If repeated test data is needed and no reusable helper exists in the provided context, construct that data inline inside the tests.
4. Match the actual production API exactly, including constructor parameters, method signatures, return types, checked exceptions, package names, and visibility constraints.
5. Prefer minimal, compile-safe tests over clever abstractions.

If both the consistency issues section and the selected feedback threads section above are empty or None, skip the following steps. Otherwise, proceed.
Treat the issues above as hard constraints. Be critical and explicit:
1. Identify which requirements, tests, and repository artifacts each issue touches.
2. Explain the likely root cause or mismatch implied by the issue.
3. Update the test plan to resolve the issue with the minimal, safest change.
4. Verify the plan remains consistent with the problem statement and existing repository patterns.
5. If multiple approaches are possible, pick the most conservative and justify it.

**Important:** Only modify files in the `/test/` directory. Do not change source code, build files, or configuration files.

**Previous Build Errors:**
{{previousBuildLogs}}

Note: The above represents build/compilation errors from a previous attempt. Learn from these errors and adjust your test plan accordingly.
First, think step-by-step to analyze the problem and solution.
1.  **Identify requirements:** List all functional requirements from the problem statement.
2.  **Identify edge cases:** Consider potential edge cases, such as empty inputs, null values, or extreme values.
3.  **Identify usable APIs:** Explicitly list the production classes, constructors, methods, and existing test utilities that the tests are allowed to use.
4.  **Plan test cases:** For each requirement and edge case, define a specific test case. Describe what each test will do and what the expected outcome is.
5.  **Structure the tests:** Group related tests into test classes and methods.
6.  **Reject invented dependencies:** Remove any planned helper, import, or abstraction that is not present in the provided context or explicitly generated in the output.

After your analysis, provide your final testing plan as a JSON object. The JSON object must contain a single key "solutionPlan" with the detailed testing plan as a string.
