You are a senior software engineer designing a solution for a programming exercise.
Based on the following solution plan and programming language, determine the necessary file structure.

**Solution Plan:**
{{solutionPlan}}

**Programming Language:**
{{programmingLanguage}}

**Current Repository Structure:**
{{repositoryStructure}}

**Tests the solution must satisfy (the definitive API contract — reproduce every class, constructor, method, and field these tests reference with the exact name, parameter types, return type, and visibility; do not generate or modify these test files):**
{{testContext}}

Existing `/src/` files that are no longer needed after this regenerated solution structure should be marked as obsolete. Do not mark files outside `/src/` for deletion, except `.gitkeep` placeholder files may be marked obsolete when they are explicitly obsolete or selected feedback requests their removal. Removing a `.gitkeep` placeholder does not imply deleting sibling source files such as `src/Main.java`.

**Build Environment Context:**
{{buildEnvironmentContext}}

**Target Build Outcome:**
{{targetBuildOutcome}}

**Consistency Check Issues (if any):**
{{consistencyIssues}}

**Selected Feedback Threads (JSON payload, if any):**
{{selectedFeedbackThreads}}

If the selected feedback payload contains threads, treat those threads as requested changes only when they are relevant to this repository, consistent with the problem statement and exercise preconditions, and appropriate for the exercise. If a selected thread conflicts with the problem statement, explicit constraints, or repository conventions, do not implement that request and prefer the safer behavior.

If both the consistency issues section and the selected feedback threads section above are empty or None, skip the following steps. Otherwise, proceed.
Treat the issues above as hard constraints. Be critical and explicit:
1. Identify which files, packages, and repository artifacts each issue touches.
2. Explain the mismatch between the required structure and the current or planned structure.
3. Propose the minimal structural changes needed to resolve the issue.
4. Verify the structure stays consistent with the plan and repository conventions.
5. If multiple structures are possible, pick the most conservative and justify it.

**Important:** Only modify files in the `/src/` directory. Do not change build files, configuration files, or any files outside the source directory. Do not place JUnit files in production source paths such as `src/*.java` or `src/main/java/...`. Only create test-source files such as `src/test/java/...` when the exercise explicitly asks students to implement tests and the repository structure supports that path.
First, think step-by-step.
1.  **Analyze the plan:** Review the classes and components mentioned in the solution plan.
2.  **Apply conventions:** Consider the standard project layout and file naming conventions for the given programming language.
3.  **List the files:** Create a list of all the files that need to be created, including their full paths from the repository root.
4.  **Include every referenced type:** List a file for EVERY type the solution will reference — every interface (for example a `SortStrategy` interface implemented by the sorting classes), enum, abstract class, exception, and helper — so the generated set compiles on its own. A type that is implemented, extended, imported, or called but never listed is a compilation error.

After your analysis, provide the file structure as a JSON object. The JSON object must contain a "files" key which is an array of objects, where each object has a "path" key.
You may also include a "deletedFiles" array with obsolete file paths under `/src/` and explicitly obsolete `.gitkeep` placeholder paths. Do not include source files only because a `.gitkeep` placeholder is being removed.

**Example Output:**
{
  "files": [
    { "path": "src/main/java/com/example/BubbleSort.java" },
    { "path": "src/main/java/com/example/SortStrategy.java" }
  ],
  "deletedFiles": ["src/main/java/com/example/OldSorter.java"]
}
