You are a senior software engineer creating template/starter code for a programming exercise.
Your task is to complete the template code based on the template plan and existing headers.

**Template Plan:**
{{solutionPlan}}

**Existing Files with Headers:**
{{filesWithHeaders}}

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
1. The template must compile, but it must not contain a hidden or partial solution to the exercise.
2. Do not implement core algorithms, business rules, validation rules, or edge-case handling that students are supposed to discover and write.
3. For student-owned methods, prefer explicit TODO markers, fail-fast stubs, or clearly incomplete placeholder returns over logic that might accidentally satisfy tests.
4. Only include minimal glue code that is necessary for compilation or framework wiring.
5. Do not add convenience behavior that makes tests pass before the student has implemented the exercise.
6. Keep comments high-level and non-revealing. Do not include algorithmic hints, edge-case recipes, or near-solution pseudocode.

If both the consistency issues section and the selected feedback threads section above are empty or None, skip the following steps. Otherwise, proceed.
Treat the issues above as hard constraints. Be critical and explicit:
1. Identify which methods, files, and repository artifacts each issue touches.
2. Explain the likely root cause in the current code, headers, or plan.
3. Choose the minimal, safest logic change that resolves the issue without introducing new behavior.
4. Verify the fix aligns with the problem statement, the plan, and existing repository patterns.
5. If multiple fixes are possible, pick the most conservative change and justify it.

**Important:** Only modify files in the `/src/` directory. Do not change build files, write tests, configuration files, or any files outside the source directory.
First, think step-by-step for each file and method.
1.  **Complete the template:** Build upon the existing headers and class structures provided.
2.  **Add helpful scaffolding:** Include necessary imports, instance variables, constructors, and basic structure that students will need.
3.  **Implement placeholder logic conservatively:** Add meaningful TODO comments and placeholder implementations that compile but remain intentionally incomplete.
4.  **Preserve the learning task:** Check that no method now handles important correctness cases that belong to the student solution.
5.  **Provide restrained guidance:** Include comments that clarify responsibility without revealing the implementation approach.
6.  **Ensure compilability:** The template must compile successfully while still failing tests that require the student’s missing logic.

After your analysis, provide the template code as a JSON object. The JSON object must contain a single key "files" which is an array of objects, where each object has a "path" and "content" key with the complete template code.
