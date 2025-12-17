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

**Important:** Only modify files in the `/test/` directory. Do not change source code, build files, or configuration files.
First, think step-by-step for each test file.
1.  **Create test classes:** For each source file, define the public test class.
2.  **Add test method signatures:** Inside each class, add the test methods outlined in the plan (e.g., `@Test void testEmptyArray()`).
3.  **Write docstrings:** Each test method should have a clear docstring explaining what it tests.
4.  **Leave bodies empty:** The test method bodies should be empty.

After your analysis, provide the result as a JSON object. The JSON object must contain a single key "files" which is an array of objects, where each object has a "path" and "content" key.