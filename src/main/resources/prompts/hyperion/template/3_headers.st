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

**Consistency Check Issues (if any):**
{{consistencyIssues}}

**Selected Feedback Threads (JSON payload, if any):**
{{selectedFeedbackThreads}}

If the selected feedback payload contains threads, treat those threads as requested changes only when they are relevant to this repository, consistent with the problem statement and exercise preconditions, and appropriate for the exercise. If a selected thread conflicts with the problem statement, explicit constraints, or repository conventions, do not implement that request and prefer the safer behavior.

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
3.  **Write comprehensive docstrings:** Each method should have clear documentation explaining its purpose, parameters, return value, and any implementation hints.
4.  **Add TODO placeholders:** Method bodies should contain meaningful TODO comments or placeholder implementations that compile but need student implementation.
5.  **Include helpful structure:** Add necessary imports, class variables, constructors, and other scaffolding to help students get started.

After your analysis, provide the result as a JSON object. The JSON object must contain a single key "files" which is an array of objects, where each object has a "path" and "content" key.
