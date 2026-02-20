You are a senior software engineer creating template/starter code for a programming exercise.
Based on the following template plan and programming language, determine the necessary file structure for the starter code.

**Template Plan:**
{{solutionPlan}}

**Programming Language:**
{{programmingLanguage}}

**Current Repository Structure:**
{{repositoryStructure}}

**Consistency Check Issues (if any):**
{{consistencyIssues}}

If there are no issues listed above, skip the following steps.
Treat the issues above as hard constraints. Be critical and explicit:
1. Identify which files, packages, and repository artifacts each issue touches.
2. Explain the mismatch between the required structure and the current or planned structure.
3. Propose the minimal structural changes needed to resolve the issue.
4. Verify the structure stays consistent with the plan and repository conventions.
5. If multiple structures are possible, pick the most conservative and justify it.

**Important:** Only modify files in the `/src/` directory. Do not change build files, write tests, configuration files, or any files outside the source directory.
First, think step-by-step.
1.  **Analyze the plan:** Review the template structure mentioned in the template plan - what classes, interfaces, and components should be scaffolded for students.
2.  **Apply conventions:** Consider the standard project layout and file naming conventions for the given programming language.
3.  **List the files:** Create a list of all the template files that need to be created, including their full paths from the repository root.

After your analysis, provide the file structure as a JSON object. The JSON object must contain a single key "files" which is an array of objects, where each object has a "path" key.

**Example Output:**
{
  "files": [
    { "path": "src/main/java/com/example/BubbleSort.java" },
    { "path": "src/main/java/com/example/SortStrategy.java" }
  ]
}
