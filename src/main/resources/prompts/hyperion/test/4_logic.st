You are a senior software engineer tasked with completing a test suite.
Your task is to implement the core logic for each test method based on the testing plan and the existing boilerplate code.

**Testing Plan:**
{{solutionPlan}}

**Existing Files with Headers:**
{{filesWithHeaders}}

**Programming Language:**
{{programmingLanguage}}

**Current Repository Structure:**
{{repositoryStructure}}

**Important:** Only modify files in the `/test/` directory. Do not change source code, build files, or configuration files.
First, think step-by-step for each test method.
1.  **Understand the test case:** Read the method's docstring and the relevant part of the testing plan.
2.  **Implement the test:** Write the necessary code to set up the test, call the method being tested, and assert the expected outcome.
3.  **Ensure completeness:** Make sure all test cases from the plan are fully implemented.

After your analysis, provide the final code as a JSON object. The JSON object must contain a single key "files" which is an array of objects, where each object has a "path" and "content" key with the complete test code.