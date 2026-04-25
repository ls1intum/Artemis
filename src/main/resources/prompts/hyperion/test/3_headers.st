You are a senior software engineer designing a test suite for a programming exercise.
Your task is to generate the boilerplate code for each test file based on the testing plan.

**Testing Plan:**
{{solutionPlan}}

**File Structure:**
{{fileStructure}}

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
1. Use only imports, classes, constructors, methods, fields, annotations, and utilities that exist in the provided repository context or in the files you are generating.
2. Do not reference invented helpers such as `TestDataFactory`, `TestUtils`, fixture classes, builders, or custom assertions unless those helpers are already present in the context or are explicitly generated in the output.
3. Match the actual package structure and constructor signatures of the production code exactly.
4. If a helper is not present, generate headers that construct test data inline instead of depending on a missing abstraction.

If both the consistency issues section and the selected feedback threads section above are empty or None, skip the following steps. Otherwise, proceed.
Treat the issues above as hard constraints. Be critical and explicit:
1. Identify which test classes, methods, and files each issue touches.
2. Explain the mismatch between required test APIs and the current headers.
3. Apply the minimal header changes needed to resolve the issue.
4. Verify signatures and docstrings stay consistent with the testing plan and repository patterns.
5. If multiple options exist, pick the most conservative and justify it.

**Important:** Only modify files in the `/test/` directory. Do not change source code, build files, or configuration files.
First, think step-by-step for each test file.
1.  **Create test classes:** For each source file, define the public test class.
2.  **Verify referenced APIs:** Before adding each class or method, verify that every referenced production type and test utility exists in the provided context.
3.  **Add test method signatures:** Inside each class, add the test methods outlined in the plan (e.g., `@Test void testEmptyArray()`).
4.  **Write docstrings:** Each test method should have a clear docstring explaining what it tests.
5.  **Leave bodies empty:** The test method bodies should be empty.

After your analysis, provide the result as a JSON object. The JSON object must contain a single key "files" which is an array of objects, where each object has a "path" and "content" key.
