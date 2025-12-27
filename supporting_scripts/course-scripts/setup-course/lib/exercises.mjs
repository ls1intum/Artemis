/**
 * Exercise creation functions
 */

import { createMultipartFormData } from './http-client.mjs';

const timestamp = Date.now();

/**
 * Create 5 programming exercises with different languages and configurations
 */
export async function createProgrammingExercises(client, courseId) {
    const exercises = [];

    const exerciseConfigs = [
        {
            title: 'Java Sorting Algorithm',
            shortName: `JavaSort${timestamp}`,
            programmingLanguage: 'JAVA',
            projectType: 'PLAIN_GRADLE',
            staticCodeAnalysisEnabled: true,
            sequentialTestRuns: false,
            packageName: 'de.tum.in.ase.sorting',
        },
        {
            title: 'Python Data Analysis',
            shortName: `PyData${timestamp}`,
            programmingLanguage: 'PYTHON',
            projectType: null,
            staticCodeAnalysisEnabled: false,
            sequentialTestRuns: false,
            packageName: null,
        },
        {
            title: 'C Calculator',
            shortName: `CCalc${timestamp}`,
            programmingLanguage: 'C',
            projectType: 'GCC',
            staticCodeAnalysisEnabled: false,
            sequentialTestRuns: false,
            packageName: null,
        },
        {
            title: 'JavaScript Web App',
            shortName: `JSWeb${timestamp}`,
            programmingLanguage: 'JAVASCRIPT',
            projectType: null,
            staticCodeAnalysisEnabled: false,
            sequentialTestRuns: false,
            packageName: null,
        },
        {
            title: 'Java Design Patterns',
            shortName: `JavaDP${timestamp}`,
            programmingLanguage: 'JAVA',
            projectType: 'PLAIN_MAVEN',
            staticCodeAnalysisEnabled: true,
            sequentialTestRuns: true,
            packageName: 'de.tum.in.ase.patterns',
        },
    ];

    for (const config of exerciseConfigs) {
        const exercise = await createProgrammingExercise(client, courseId, config);
        exercises.push(exercise);
    }

    console.log(`  Created ${exercises.length} programming exercises`);
    return exercises;
}

async function createProgrammingExercise(client, courseId, config) {
    const exercise = {
        type: 'programming',
        title: config.title,
        shortName: config.shortName,
        course: { id: courseId },
        programmingLanguage: config.programmingLanguage,
        projectType: config.projectType,
        allowOnlineEditor: true,
        allowOfflineIde: true,
        maxPoints: 100,
        assessmentType: 'AUTOMATIC',
        packageName: config.packageName,
        staticCodeAnalysisEnabled: config.staticCodeAnalysisEnabled,
        sequentialTestRuns: config.sequentialTestRuns,
        releaseDate: new Date().toISOString(),
        dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
        problemStatement: getProblemStatement(config.title, config.programmingLanguage),
        buildConfig: {
            buildScript: getBuildScript(config.programmingLanguage),
            checkoutSolutionRepository: false,
        },
    };

    const response = await client.post('/api/programming/programming-exercises/setup', exercise);
    console.log(`    Created: ${config.title} (${config.programmingLanguage})`);
    return response.data;
}

function getBuildScript(language) {
    const scripts = {
        JAVA: `#!/usr/bin/env bash
set -e
gradle () {
  echo '⚙️ executing gradle'
  chmod +x ./gradlew
  ./gradlew clean test
}
main () {
  gradle
}
main "\${@}"
`,
        PYTHON: `#!/usr/bin/env bash
set -e
python3 -m pytest --junitxml=test-reports/results.xml
`,
        C: `#!/usr/bin/env bash
set -e
make clean
make test
`,
        JAVASCRIPT: `#!/usr/bin/env bash
set -e
npm install
npm test
`,
    };
    return scripts[language] || scripts.JAVA;
}

function getProblemStatement(title, language) {
    const statements = {
        'Java Sorting Algorithm': `# Java Sorting Algorithm

## Task Description
Implement a sorting algorithm in Java that can sort an array of integers.

## Requirements
1. Implement the \`sort(int[] array)\` method in the \`Sorting\` class
2. The algorithm should sort the array in ascending order
3. Handle edge cases (empty array, single element, already sorted)

## Example
\`\`\`java
int[] array = {5, 2, 8, 1, 9};
Sorting.sort(array);
// Result: {1, 2, 5, 8, 9}
\`\`\`

## Hints
- Consider using QuickSort or MergeSort for efficiency
- Think about time and space complexity`,

        'Python Data Analysis': `# Python Data Analysis

## Task Description
Implement data analysis functions using Python.

## Requirements
1. Implement the \`analyze_data(data)\` function
2. Calculate mean, median, and standard deviation
3. Handle missing values appropriately

## Example
\`\`\`python
data = [1, 2, 3, 4, 5]
result = analyze_data(data)
# Result: {'mean': 3.0, 'median': 3.0, 'std': 1.41}
\`\`\`

## Hints
- Use numpy for numerical operations
- Consider edge cases with empty data`,

        'C Calculator': `# C Calculator

## Task Description
Implement a simple calculator in C.

## Requirements
1. Implement basic arithmetic operations (+, -, *, /)
2. Handle division by zero
3. Support integer and floating-point numbers

## Example
\`\`\`c
double result = calculate(10, 5, '+');
// Result: 15.0
\`\`\`

## Hints
- Use switch statements for operations
- Consider precision for floating-point results`,

        'JavaScript Web App': `# JavaScript Web App

## Task Description
Implement a simple web application component.

## Requirements
1. Create a function that manipulates DOM elements
2. Handle user events appropriately
3. Validate user input

## Example
\`\`\`javascript
const result = processInput("Hello");
// Result: "HELLO"
\`\`\`

## Hints
- Use modern ES6+ syntax
- Consider async operations`,

        'Java Design Patterns': `# Java Design Patterns

## Task Description
Implement common design patterns in Java.

## Requirements
1. Implement the Singleton pattern
2. Implement the Factory pattern
3. Write unit tests for your implementations

## Example
\`\`\`java
Singleton instance = Singleton.getInstance();
Product product = Factory.create("TypeA");
\`\`\`

## Hints
- Ensure thread safety for Singleton
- Use interfaces for Factory products`,
    };

    return statements[title] || `# ${title}

## Task Description
Complete the programming exercise according to the requirements.

## Requirements
1. Implement the required functionality
2. Ensure all tests pass
3. Follow clean code principles

## Hints
- Read the existing code carefully
- Check the test cases for expected behavior`;
}

/**
 * Create 2 modeling exercises
 */
export async function createModelingExercises(client, courseId) {
    const exercises = [];

    const configs = [
        {
            title: 'UML Class Diagram',
            shortName: `UMLClass${timestamp}`,
            diagramType: 'ClassDiagram',
            difficulty: 'EASY',
        },
        {
            title: 'Activity Diagram',
            shortName: `Activity${timestamp}`,
            diagramType: 'ActivityDiagram',
            difficulty: 'MEDIUM',
        },
    ];

    for (const config of configs) {
        const exercise = {
            type: 'modeling',
            title: config.title,
            shortName: config.shortName,
            course: { id: courseId },
            diagramType: config.diagramType,
            difficulty: config.difficulty,
            maxPoints: 100,
            assessmentType: 'MANUAL',
            releaseDate: new Date().toISOString(),
            dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
            exampleSolutionModel: JSON.stringify({
                version: '2.0.0',
                type: config.diagramType,
                size: { width: 800, height: 600 },
                interactive: { elements: [], relationships: [] },
                elements: [],
                relationships: [],
                assessments: [],
            }),
            problemStatement: `Create a ${config.diagramType} for the given requirements.

## Requirements
1. Model the main components
2. Show relationships between components
3. Use proper notation

## Tips
- Start with the main entities
- Add relationships step by step`,
        };

        const response = await client.post('/api/modeling/modeling-exercises', exercise);
        exercises.push(response.data);
        console.log(`    Created: ${config.title}`);
    }

    console.log(`  Created ${exercises.length} modeling exercises`);
    return exercises;
}

/**
 * Create 2 text exercises
 */
export async function createTextExercises(client, courseId) {
    const exercises = [];

    const configs = [
        {
            title: 'Essay: Software Architecture',
            shortName: `Essay1${timestamp}`,
            difficulty: 'MEDIUM',
        },
        {
            title: 'Summary: Design Patterns',
            shortName: `Summary1${timestamp}`,
            difficulty: 'EASY',
        },
    ];

    for (const config of configs) {
        const exercise = {
            type: 'text',
            title: config.title,
            shortName: config.shortName,
            course: { id: courseId },
            difficulty: config.difficulty,
            maxPoints: 100,
            assessmentType: 'MANUAL',
            releaseDate: new Date().toISOString(),
            dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
            problemStatement: `# ${config.title}

Write a comprehensive response addressing the following points:

1. **Introduction**: Provide context and background
2. **Main Content**: Discuss the key concepts
3. **Examples**: Give practical examples
4. **Conclusion**: Summarize your findings

## Evaluation Criteria
- Clarity and structure
- Technical accuracy
- Use of examples
- Writing quality`,
        };

        const response = await client.post('/api/text/text-exercises', exercise);
        exercises.push(response.data);
        console.log(`    Created: ${config.title}`);
    }

    console.log(`  Created ${exercises.length} text exercises`);
    return exercises;
}

/**
 * Generate a unique temp ID for quiz question elements
 */
function generateTempId() {
    return Math.floor(Math.random() * Number.MAX_SAFE_INTEGER);
}

/**
 * Create 2 quiz exercises
 */
export async function createQuizExercises(client, courseId) {
    const exercises = [];

    const configs = [
        {
            title: 'Programming Fundamentals Quiz',
            shortName: `Quiz1${timestamp}`,
            duration: 30,
            questions: [
                {
                    type: 'multiple-choice',
                    title: 'What is a variable?',
                    text: 'Which statement best describes a variable in programming?',
                    answerOptions: [
                        { text: 'A container that stores data', isCorrect: true },
                        { text: 'A type of loop', isCorrect: false },
                        { text: 'A function call', isCorrect: false },
                        { text: 'A comment in code', isCorrect: false },
                    ],
                    points: 1,
                    singleChoice: true,
                },
                {
                    type: 'multiple-choice',
                    title: 'Object-Oriented Concepts',
                    text: 'Which are key principles of OOP? (Select all that apply)',
                    answerOptions: [
                        { text: 'Encapsulation', isCorrect: true },
                        { text: 'Inheritance', isCorrect: true },
                        { text: 'Compilation', isCorrect: false },
                        { text: 'Polymorphism', isCorrect: true },
                    ],
                    points: 3,
                    singleChoice: false,
                },
                {
                    type: 'short-answer',
                    title: 'Keyword Question',
                    text: 'What keyword is used to create a [-spot 1] in Java?',
                    solutions: ['class'],
                    points: 1,
                },
            ],
        },
        {
            title: 'Data Structures Quiz',
            shortName: `Quiz2${timestamp}`,
            duration: 30,
            questions: [
                {
                    type: 'multiple-choice',
                    title: 'Array vs List',
                    text: 'What is the main advantage of an ArrayList over a regular array?',
                    answerOptions: [
                        { text: 'Dynamic resizing', isCorrect: true },
                        { text: 'Faster access time', isCorrect: false },
                        { text: 'Less memory usage', isCorrect: false },
                        { text: 'Type safety', isCorrect: false },
                    ],
                    points: 1,
                    singleChoice: true,
                },
                {
                    type: 'multiple-choice',
                    title: 'Stack Operations',
                    text: 'Which operation adds an element to the top of a stack?',
                    answerOptions: [
                        { text: 'push', isCorrect: true },
                        { text: 'pop', isCorrect: false },
                        { text: 'peek', isCorrect: false },
                        { text: 'enqueue', isCorrect: false },
                    ],
                    points: 1,
                    singleChoice: true,
                },
            ],
        },
    ];

    for (const config of configs) {
        const quizQuestions = config.questions.map((q, index) => createQuizQuestion(q, index));

        const exercise = {
            type: 'quiz',
            title: config.title,
            shortName: config.shortName,
            course: { id: courseId },
            duration: config.duration,
            quizQuestions,
            isVisibleBeforeStart: false,
            isOpenForPractice: false,
            isPlannedToStart: false,
            randomizeQuestionOrder: false,
            releaseDate: new Date().toISOString(),
            dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
            quizMode: 'SYNCHRONIZED',
            mode: 'INDIVIDUAL',
            includedInOverallScore: 'INCLUDED_COMPLETELY',
        };

        const { body, contentType } = createMultipartFormData({ exercise });
        const response = await client.post(`/api/quiz/courses/${courseId}/quiz-exercises`, body, {
            headers: { 'Content-Type': contentType },
            contentType: 'multipart',
        });
        const createdExercise = response.data;
        exercises.push(createdExercise);
        console.log(`    Created: ${config.title}`);

        // Start the quiz so students can participate
        try {
            await client.put(`/api/quiz/quiz-exercises/${createdExercise.id}/start-now`);
            console.log(`    Started: ${config.title}`);
        } catch (error) {
            console.log(`    Note: Could not start quiz ${config.title}: ${error.response?.data?.message || error.message}`);
        }
    }

    console.log(`  Created ${exercises.length} quiz exercises`);
    return exercises;
}

function createQuizQuestion(config, index) {
    const base = {
        title: config.title,
        text: config.text,
        points: config.points,
        exportQuiz: false,
        invalid: false,
        randomizeOrder: false,
    };

    if (config.type === 'multiple-choice') {
        return {
            ...base,
            type: 'multiple-choice',
            answerOptions: config.answerOptions.map((opt) => ({
                text: opt.text,
                isCorrect: opt.isCorrect,
                explanation: opt.explanation || null,
                invalid: false,
            })),
            singleChoice: config.singleChoice,
            scoringType: 'ALL_OR_NOTHING',
        };
    } else if (config.type === 'short-answer') {
        // Build spots, solutions, and correctMappings with proper tempIDs
        const spots = [];
        const solutions = [];
        const correctMappings = [];

        config.solutions.forEach((solutionText, i) => {
            const spotTempId = generateTempId();
            const solutionTempId = generateTempId();

            const spot = {
                tempID: spotTempId,
                invalid: false,
                width: 15,
                spotNr: i + 1,
            };

            const solution = {
                tempID: solutionTempId,
                invalid: false,
                text: solutionText,
            };

            spots.push(spot);
            solutions.push(solution);

            // correctMappings uses spotTempId and solutionTempId as direct fields
            correctMappings.push({
                spotTempId: spotTempId,
                solutionTempId: solutionTempId,
                invalid: false,
            });
        });

        return {
            ...base,
            type: 'short-answer',
            spots,
            solutions,
            correctMappings,
            matchLetterCase: false,
            similarityValue: 85,
            scoringType: 'PROPORTIONAL_WITHOUT_PENALTY',
        };
    }

    return base;
}

/**
 * Create 1 file upload exercise
 */
export async function createFileUploadExercise(client, courseId) {
    const exercise = {
        type: 'file-upload',
        title: 'Document Submission',
        shortName: `FileUp${timestamp}`,
        course: { id: courseId },
        maxPoints: 100,
        assessmentType: 'MANUAL',
        releaseDate: new Date().toISOString(),
        dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
        filePattern: 'pdf,doc,docx,txt',
        problemStatement: `# Document Submission

Upload your document following these guidelines:

## Requirements
1. Maximum file size: 10MB
2. Allowed formats: PDF, DOC, DOCX, TXT
3. File naming: LastName_FirstName_Assignment

## Content Requirements
- Cover page with your details
- Table of contents
- Main content
- References

## Submission Guidelines
Ensure your document is properly formatted before submission.`,
    };

    const response = await client.post('/api/fileupload/file-upload-exercises', exercise);
    console.log(`    Created: ${exercise.title}`);
    console.log(`  Created 1 file upload exercise`);
    return response.data;
}
