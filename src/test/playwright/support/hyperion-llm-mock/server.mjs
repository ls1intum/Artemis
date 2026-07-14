/* global Buffer, console, process */
import http from 'node:http';

const port = Number(process.env.HYPERION_LLM_MOCK_PORT ?? 1234);
let requestCount = 0;
const requests = [];
const failMarker = 'HYPERION_E2E_FAIL_LLM';
const writeSnapshotMarker = 'HYPERION_E2E_WRITE_SNAPSHOT';
const submitSeedMarker = 'HYPERION_E2E_SUBMIT_SEEDED_EXERCISE';
const correctedSeedStatementMarker = 'Use merge sort for big lists';
const solutionMarkerPath = 'solution/hyperion-e2e-solution-marker.txt';
const templateMarkerPath = 'template/hyperion-e2e-template-marker.txt';
const testsMarkerPath = 'tests/hyperion-e2e-tests-marker.txt';
const criticPromptMarker = 'meticulous QA reviewer for programming-exercise test suites';
const draftPromptMarker = 'expert technical writing assistant for programming exercise problem statements';
const draftProblemStatement = `# Temperature Alert Classification

## Introduction

Create a small Java program that classifies temperature readings. The exercise focuses on precise boundary handling and predictable validation behavior.

## Required Behaviors

- Classify readings below 0 as \`FREEZING\`, readings from 0 through 25 as \`NORMAL\`, and readings above 25 as \`HOT\`.
- Preserve the input order when classifying multiple readings.
- Reject a collection containing a missing reading. If validation fails, produce no partial result.

## Boundary Cases

- An empty collection produces an empty result.
- The exact boundary values 0 and 25 are classified as \`NORMAL\`.

## Worked Examples

- \`[-1, 0, 26]\` produces \`[FREEZING, NORMAL, HOT]\`.
- \`[]\` produces \`[]\`.
`;
const correctedSeedProblemStatement = `In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.

### Part 1: Sorting

First, we need to implement two sorting algorithms, in this case \`MergeSort\` and \`BubbleSort\`.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort)
Implement the method \`performSort(List<Date>)\` in the class \`BubbleSort\`. Make sure to follow the Bubble Sort algorithm exactly.

2. [task][Implement Merge Sort](testMergeSort)
Implement the method \`performSort(List<Date>)\` in the class \`MergeSort\`. Make sure to follow the Merge Sort algorithm exactly.

### Part 2: Strategy Pattern

We want the application to apply different algorithms for sorting a \`List\` of \`Date\` objects.
Use the strategy pattern to select the right sorting algorithm at runtime.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
Create a \`SortStrategy\` interface and adjust the sorting algorithms so that they implement this interface.

2. [task][Context Class](testAttributes[Context],testMethods[Context])
Create and implement a \`Context\` class following the below class diagram.

3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
Create and implement a \`Policy\` class following the below class diagram with a simple configuration mechanism:

    1. [task][Use merge sort for big lists](testUseMergeSortForBigList)
    Select \`MergeSort\` when the List has more than 10 dates.

    2. [task][Use bubble sort for small lists](testUseBubbleSortForSmallList)
    Select \`BubbleSort\` when the List has less or equal 10 dates.

4. Complete the \`Client\` class which demonstrates switching between two strategies at runtime.
`;

function summarizeRequest(rawBody) {
    try {
        const parsed = JSON.parse(rawBody);
        const messages = Array.isArray(parsed.messages) ? parsed.messages : [];
        return {
            messageCount: messages.length,
            roles: messages.map((message) => message?.role).filter(Boolean),
            promptText: messages.map((message) => (typeof message?.content === 'string' ? message.content : JSON.stringify(message?.content ?? ''))).join('\n'),
            hasWriteFileTool: rawBody.includes('write_file'),
            hasBashTool: rawBody.includes('bash'),
            hasSubmitTool: rawBody.includes('submit'),
        };
    } catch (error) {
        return { parseError: String(error), rawBodyPrefix: rawBody.slice(0, 500) };
    }
}

function jsonResponse(res, status, body) {
    res.writeHead(status, { 'content-type': 'application/json' });
    res.end(JSON.stringify(body));
}

function toolCallResponse(requestNumber, toolName, args) {
    return {
        id: `chatcmpl-hyperion-e2e-${requestNumber}`,
        object: 'chat.completion',
        created: Math.floor(Date.now() / 1000),
        model: 'hyperion-e2e-mock',
        choices: [
            {
                index: 0,
                message: {
                    role: 'assistant',
                    content: '',
                    tool_calls: [
                        {
                            id: `call_hyperion_e2e_${requestNumber}`,
                            type: 'function',
                            function: {
                                name: toolName,
                                arguments: JSON.stringify(args),
                            },
                        },
                    ],
                },
                finish_reason: 'tool_calls',
            },
        ],
        usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 },
    };
}

function textResponse(requestNumber, content) {
    return {
        id: `chatcmpl-hyperion-e2e-${requestNumber}`,
        object: 'chat.completion',
        created: Math.floor(Date.now() / 1000),
        model: 'hyperion-e2e-mock',
        choices: [{ index: 0, message: { role: 'assistant', content }, finish_reason: 'stop' }],
        usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 },
    };
}

const server = http.createServer((req, res) => {
    if (req.method === 'GET' && req.url === '/health') {
        jsonResponse(res, 200, { ok: true, requestCount });
        return;
    }

    if (req.method === 'GET' && req.url === '/requests') {
        jsonResponse(res, 200, { requests });
        return;
    }

    if (req.method === 'POST' && req.url === '/v1/chat/completions') {
        const chunks = [];
        req.on('data', (chunk) => chunks.push(chunk));
        req.on('end', () => {
            requestCount++;
            const requestNumber = requestCount;
            const body = Buffer.concat(chunks).toString('utf8');
            requests.push(summarizeRequest(body));
            if (body.includes(failMarker)) {
                jsonResponse(res, 400, { error: { message: 'Hyperion E2E requested LLM failure' } });
                return;
            }
            if (body.includes(draftPromptMarker)) {
                jsonResponse(res, 200, textResponse(requestNumber, draftProblemStatement));
                return;
            }
            if (body.includes(criticPromptMarker)) {
                jsonResponse(res, 200, textResponse(requestNumber, '{"uncovered":[],"missingExamples":[],"invented":[],"unrequestedChanges":[],"missingRequestedChanges":[]}'));
                return;
            }
            if (body.includes(writeSnapshotMarker) && !body.includes('HyperionPreview.java')) {
                jsonResponse(
                    res,
                    200,
                    toolCallResponse(requestNumber, 'write_file', {
                        path: 'solution/src/de/test/HyperionPreview.java',
                        content: 'package de.test;\n\npublic class HyperionPreview {\n    public String marker() {\n        return "retained-preview";\n    }\n}\n',
                    }),
                );
                return;
            }
            if (body.includes(submitSeedMarker) && !body.includes(correctedSeedStatementMarker)) {
                jsonResponse(res, 200, toolCallResponse(requestNumber, 'write_file', { path: 'problem-statement.md', content: correctedSeedProblemStatement }));
                return;
            }
            if (body.includes(submitSeedMarker) && !body.includes(solutionMarkerPath)) {
                jsonResponse(
                    res,
                    200,
                    toolCallResponse(requestNumber, 'write_file', {
                        path: solutionMarkerPath,
                        content: 'hyperion-e2e-solution-marker\n',
                    }),
                );
                return;
            }
            if (body.includes(submitSeedMarker) && !body.includes(templateMarkerPath)) {
                jsonResponse(
                    res,
                    200,
                    toolCallResponse(requestNumber, 'write_file', {
                        path: templateMarkerPath,
                        content: 'hyperion-e2e-template-marker\n',
                    }),
                );
                return;
            }
            if (body.includes(submitSeedMarker) && !body.includes(testsMarkerPath)) {
                jsonResponse(
                    res,
                    200,
                    toolCallResponse(requestNumber, 'write_file', {
                        path: testsMarkerPath,
                        content: 'hyperion-e2e-tests-marker\n',
                    }),
                );
                return;
            }
            if (body.includes(submitSeedMarker)) {
                jsonResponse(res, 200, toolCallResponse(requestNumber, 'submit', { summary: 'Submitted the seeded Java exercise for deterministic E2E verification.' }));
                return;
            }
            jsonResponse(res, 200, toolCallResponse(requestNumber, 'bash', { command: 'sleep 30' }));
        });
        return;
    }

    jsonResponse(res, 404, { error: `Unhandled ${req.method} ${req.url}` });
});

server.listen(port, '127.0.0.1', () => {
    console.log(`Hyperion E2E LLM mock listening on http://127.0.0.1:${port}`);
});

process.on('SIGTERM', () => server.close(() => process.exit(0)));
process.on('SIGINT', () => server.close(() => process.exit(0)));
