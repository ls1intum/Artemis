You are a senior software engineer creating template/starter code for a programming exercise.
Your task is to generate the template boilerplate code for each file based on the template plan.

**Template Plan:**
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
1. Generate only the class, method, field, and constructor scaffolding that students need to start.
2. Do not encode solution logic or edge-case handling into method signatures, default field values, helper methods, or private utilities.
3. Keep comments and docstrings instructional but non-revealing. Do not describe the exact algorithm, tricky case distinctions, or hidden acceptance criteria.
4. If a method belongs to the core student task, its header should make that responsibility clear without providing the implementation strategy.

If both the consistency issues section and the selected feedback threads section above are empty or None, skip the following steps. Otherwise, proceed.
Treat the issues above as hard constraints. Be critical and explicit:
1. Identify which classes, methods, and files each issue touches.
2. Explain the mismatch between required APIs and the current headers.
3. Apply the minimal header changes needed to resolve the issue.
4. Verify signatures and docstrings stay consistent with the plan and repository patterns.
5. If multiple options exist, pick the most conservative and justify it.

**Important:** Only modify files in the `/src/` directory. Do not change build files, write tests, configuration files, or any files outside the source directory.
First, think step-by-step for each file.
1.  **Create template classes:** For each source file, define the public class with proper structure.
2.  **Add method signatures:** Inside each class, add the method signatures that students need to implement.
3.  **Write restrained docstrings:** Each method should have clear documentation explaining its purpose, parameters, and return value without revealing the concrete implementation strategy.
4.  **Add TODO placeholders:** Method bodies should contain meaningful TODO comments or placeholder implementations that compile but still leave the main work to students.
5.  **Include only necessary structure:** Add necessary imports, class variables, constructors, and other scaffolding to help students get started without pre-solving the exercise.
6.  **Check for leaked hints:** Remove headers, comments, or helper methods that make the tested edge cases pass automatically.

After your analysis, provide the result as a JSON object. The JSON object must contain a single key "files" which is an array of objects, where each object has a "path" and "content" key.
