/* global Buffer, console, process */
import http from 'node:http';
import { clearTimeout, setTimeout } from 'node:timers';

const port = Number(process.env.HYPERION_LLM_MOCK_PORT ?? 1234);
let requestCount = 0;
let completedHeldProviderResponseCount = 0;
const pendingLateFailures = new Set();
const pendingProviderResponses = new Set();
let holdUnmatchedRequests = false;
const failMarker = 'HYPERION_E2E_FAIL_LLM';
const mechanicalRejectionMarker = 'HYPERION_E2E_MECHANICAL_REJECTION';
const invalidCandidatePath = 'solution/src/de/test/VerifierRejected.java';
const diagnosticFilePath = 'solution/src/de/test/HyperionDiagnostic.java';
const writeSnapshotMarker = 'HYPERION_E2E_WRITE_SNAPSHOT';
const submitSeedMarker = 'HYPERION_E2E_SUBMIT_SEEDED_EXERCISE';
const reviewRequiredMarker = 'HYPERION_E2E_REQUIRE_INSTRUCTOR_REVIEW';
const submitNewExerciseMarker = 'HYPERION_E2E_SUBMIT_NEW_EXERCISE';
const correctedSeedStatementMarker = 'more than 5 dates';
const adaptedPolicyMarker = 'DATES_SIZE_THRESHOLD = 5';
const adaptedTestLoopMarker = 'for (int i = 0; i < 6; i++)';
const adaptedTestMessageMarker = 'The sort algorithm of Context was not MergeSort for a list with more than 5 dates.';
const adaptedBoundaryTestLoopMarker = 'for (int i = 0; i < 5; i++)';
const adaptedBoundaryTestMessageMarker = 'The sort algorithm of Context was not BubbleSort for a list with less or equal than 5 dates.';
const criticPromptMarker = 'reviewer for a generated programming exercise';
const oracleReviewPromptMarker = 'test-oracle reviewer for a generated programming exercise';
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
const generatedProblemStatement = `# Temperature Alert Classification

Implement \`TemperatureClassifier.classify(List<Integer>)\`. Return one label for each reading in input order: \`FREEZING\` below 0, \`NORMAL\` from 0 through 25, and \`HOT\` above 25. Reject null readings without returning a partial result. An empty list produces an empty list.

## Tasks

1. [task][Classify temperatures](testRepresentativeTemperatures)
2. [task][Handle boundaries and empty input](testBoundariesAndEmptyInput)
3. [task][Reject missing readings atomically](testMissingReadingIsRejected)
`;
const correctedGeneratedProblemStatement = generatedProblemStatement
    .replace('testRepresentativeTemperatures)', 'testRepresentativeTemperatures())')
    .replace('testBoundariesAndEmptyInput)', 'testBoundariesAndEmptyInput())')
    .replace('testMissingReadingIsRejected)', 'testMissingReadingIsRejected())');
const generatedSolution = `package temperaturealertclassification;

import java.util.ArrayList;
import java.util.List;

public final class TemperatureClassifier {
    private TemperatureClassifier() {
    }

    public static List<String> classify(List<Integer> readings) {
        List<String> labels = new ArrayList<>();
        for (Integer reading : readings) {
            if (reading == null) {
                throw new IllegalArgumentException("readings must not contain null");
            }
            labels.add(reading < 0 ? "FREEZING" : reading <= 25 ? "NORMAL" : "HOT");
        }
        return labels;
    }
}
`;
const generatedTemplate = `package temperaturealertclassification;

import java.util.List;

public final class TemperatureClassifier {
    private TemperatureClassifier() {
    }

    public static List<String> classify(List<Integer> readings) {
        // TODO: Implement the classification rules.
        throw new UnsupportedOperationException("Not implemented");
    }
}
`;
const generatedTests = `package temperaturealertclassification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.in.test.api.BlacklistPath;
import de.tum.in.test.api.StrictTimeout;
import de.tum.in.test.api.WhitelistPath;
import de.tum.in.test.api.jupiter.Public;

@Public
@WhitelistPath("target")
@BlacklistPath("target/test-classes")
class TemperatureClassifierTest {
    @Test
    @StrictTimeout(1)
    void testRepresentativeTemperatures() {
        assertEquals(List.of("FREEZING", "NORMAL", "HOT"), TemperatureClassifier.classify(List.of(-4, 12, 30)), "Each reading should receive its matching label");
    }

    @Test
    @StrictTimeout(1)
    void testBoundariesAndEmptyInput() {
        assertEquals(List.of("NORMAL", "NORMAL"), TemperatureClassifier.classify(List.of(0, 25)), "Both inclusive boundary values should be normal");
        assertEquals(List.of(), TemperatureClassifier.classify(List.of()), "An empty input should produce an empty result");
    }

    @Test
    @StrictTimeout(1)
    void testMissingReadingIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TemperatureClassifier.classify(Arrays.asList(-1, null, 26)), "A null reading should reject the complete input");
    }
}
`;
const correctedSeedProblemStatement = `In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.

### Part 1: Sorting

First, we need to implement two sorting algorithms, in this case \`MergeSort\` and \`BubbleSort\`.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort,testClass[BubbleSort])
Implement the method \`performSort(List<Date>)\` in the class \`BubbleSort\`. Make sure to follow the Bubble Sort algorithm exactly.

2. [task][Implement Merge Sort](testMergeSort,testClass[MergeSort])
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
    Select \`MergeSort\` when the List has more than 5 dates.

    2. [task][Use bubble sort for small lists](testUseBubbleSortForSmallList)
    Select \`BubbleSort\` when the List has less or equal 5 dates.

4. Complete the \`Client\` class which demonstrates switching between two strategies at runtime.
`;

function hasAcknowledgedToolCall(rawBody, toolName, expectedArguments) {
    try {
        const messages = JSON.parse(rawBody).messages;
        return (
            Array.isArray(messages) &&
            messages.some((message, messageIndex) =>
                message?.tool_calls?.some((toolCall) => {
                    if (toolCall?.function?.name !== toolName || typeof toolCall.function.arguments !== 'string') {
                        return false;
                    }
                    try {
                        return (
                            expectedArguments(JSON.parse(toolCall.function.arguments)) &&
                            typeof toolCall.id === 'string' &&
                            messages.slice(messageIndex + 1).some((candidate) => candidate?.role === 'tool' && candidate.tool_call_id === toolCall.id)
                        );
                    } catch {
                        return false;
                    }
                }),
            )
        );
    } catch {
        return false;
    }
}

function currentCandidateContains(rawBody, expectedContent) {
    const artifactsStart = rawBody.indexOf('MECHANICALLY VERIFIED CANDIDATE ARTIFACTS:');
    if (artifactsStart < 0) {
        return false;
    }
    const adaptationChangesStart = rawBody.indexOf('ADAPTATION CHANGES (baseline to candidate):', artifactsStart);
    const artifactsEnd = adaptationChangesStart < 0 ? rawBody.length : adaptationChangesStart;
    return rawBody.slice(artifactsStart, artifactsEnd).includes(expectedContent);
}

function jsonResponse(res, status, body) {
    if (res.destroyed || res.writableEnded) {
        return;
    }
    res.writeHead(status, { 'content-type': 'application/json' });
    res.end(JSON.stringify(body));
}

function failPendingResponses(pendingResponses, message) {
    let released = 0;
    for (const pendingResponse of pendingResponses) {
        pendingResponses.delete(pendingResponse);
        jsonResponse(pendingResponse, 400, { error: { message } });
        released++;
    }
    return released;
}

function resetScenario() {
    const releasedLateFailures = failPendingResponses(pendingLateFailures, 'Hyperion E2E scenario reset released a pending failure response');
    const releasedProviderResponses = failPendingResponses(pendingProviderResponses, 'Hyperion E2E scenario reset released a held provider response');
    requestCount = 0;
    completedHeldProviderResponseCount = 0;
    holdUnmatchedRequests = false;
    return { releasedLateFailures, releasedProviderResponses };
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
        jsonResponse(res, 200, {
            ok: true,
            requestCount,
            completedHeldProviderResponseCount,
            pendingLateFailureCount: pendingLateFailures.size,
            pendingProviderResponseCount: pendingProviderResponses.size,
            holdUnmatchedRequests,
        });
        return;
    }

    if (req.method === 'POST' && req.url === '/release-late-failure') {
        if (pendingLateFailures.size !== 1) {
            jsonResponse(res, 409, { error: { message: `Expected one pending late failure, found ${pendingLateFailures.size}` } });
            return;
        }
        failPendingResponses(pendingLateFailures, 'Hyperion E2E requested LLM failure after producing diagnostic work');
        jsonResponse(res, 200, { released: 1 });
        return;
    }

    if (req.method === 'POST' && req.url === '/release-held-provider-response') {
        if (pendingProviderResponses.size !== 1) {
            jsonResponse(res, 409, { error: { message: `Expected one held provider response, found ${pendingProviderResponses.size}` } });
            return;
        }
        for (const pendingResponse of pendingProviderResponses) {
            pendingProviderResponses.delete(pendingResponse);
            let settled = false;
            const timeout = setTimeout(() => settle(504, 'Timed out while releasing the held provider response'), 5_000);
            const settle = (status, error) => {
                if (settled) {
                    return;
                }
                settled = true;
                clearTimeout(timeout);
                jsonResponse(res, status, error ? { error: { message: error } } : { released: 1 });
            };
            pendingResponse.once('finish', () => {
                completedHeldProviderResponseCount++;
                settle(200);
            });
            pendingResponse.once('close', () => settle(502, 'Held provider response closed before completion'));
            pendingResponse.once('error', () => settle(502, 'Held provider response failed before completion'));
            jsonResponse(
                pendingResponse,
                200,
                toolCallResponse(requestCount, 'write_file', {
                    path: 'solution/src/de/test/LateAfterCancellation.java',
                    content: 'package de.test;\n\nfinal class LateAfterCancellation {}\n',
                }),
            );
        }
        return;
    }

    if (req.method === 'POST' && req.url === '/reset') {
        jsonResponse(res, 200, resetScenario());
        return;
    }

    if (req.method === 'POST' && req.url === '/scenario') {
        const chunks = [];
        req.on('data', (chunk) => chunks.push(chunk));
        req.on('end', () => {
            try {
                const scenario = JSON.parse(Buffer.concat(chunks).toString('utf8'));
                if (typeof scenario.holdUnmatchedRequests !== 'boolean') {
                    jsonResponse(res, 400, { error: { message: 'holdUnmatchedRequests must be a boolean' } });
                    return;
                }
                holdUnmatchedRequests = scenario.holdUnmatchedRequests;
                jsonResponse(res, 200, { holdUnmatchedRequests });
            } catch {
                jsonResponse(res, 400, { error: { message: 'Scenario body must be valid JSON' } });
            }
        });
        return;
    }

    if (req.method === 'POST' && req.url === '/v1/chat/completions') {
        const chunks = [];
        req.on('data', (chunk) => chunks.push(chunk));
        req.on('end', () => {
            requestCount++;
            const requestNumber = requestCount;
            const body = Buffer.concat(chunks).toString('utf8');
            if (body.includes(draftPromptMarker)) {
                if (!body.includes(submitNewExerciseMarker)) {
                    jsonResponse(res, 400, { error: { message: 'The instructor brief was not forwarded to draft generation' } });
                    return;
                }
                jsonResponse(res, 200, textResponse(requestNumber, draftProblemStatement));
                return;
            }
            if (body.includes(failMarker)) {
                if (!hasAcknowledgedToolCall(body, 'write_file', (args) => args.path === diagnosticFilePath)) {
                    jsonResponse(
                        res,
                        200,
                        toolCallResponse(requestNumber, 'write_file', {
                            path: diagnosticFilePath,
                            content:
                                'package de.test;\n\npublic class HyperionDiagnostic {\n    public String marker() {\n        return "captured-after-provider-failure";\n    }\n}\n',
                        }),
                    );
                    return;
                }
                pendingLateFailures.add(res);
                res.once('close', () => pendingLateFailures.delete(res));
                return;
            }
            if (body.includes(mechanicalRejectionMarker)) {
                if (!hasAcknowledgedToolCall(body, 'write_file', (args) => args.path === invalidCandidatePath)) {
                    jsonResponse(
                        res,
                        200,
                        toolCallResponse(requestNumber, 'write_file', {
                            path: invalidCandidatePath,
                            content: 'package de.test;\n\npublic class VerifierRejected {\n    this is not valid Java\n}\n',
                        }),
                    );
                    return;
                }
                jsonResponse(res, 200, toolCallResponse(requestNumber, 'submit', { summary: 'Submit an intentionally invalid candidate to exercise the real verifier.' }));
                return;
            }
            if (body.includes(criticPromptMarker)) {
                const weakThresholdOracle = body.includes(submitSeedMarker) && currentCandidateContains(body, 'for (int i = 0; i < 3; i++)');
                const oracleReview = body.includes(oracleReviewPromptMarker);
                if (body.includes(reviewRequiredMarker) && !oracleReview) {
                    jsonResponse(res, 200, textResponse(requestNumber, '{}'));
                    return;
                }
                const audit = oracleReview
                    ? `"exampleChecks":[],"apiChecks":[],"templateChecks":[],"mutantChecks":[{"mutant":"a threshold of 4","killed":${!weakThresholdOracle},"sourceQuote":"use merge sort for lists with more than 5 dates and update the matching test.","reason":"the boundary tests must distinguish sizes 5 and 6"}]`
                    : '"exampleChecks":[],"apiChecks":[{"symbol":"seeded public API","discoverable":true,"reason":"the statement and starter expose it"}],"templateChecks":[{"test":"seeded task groups","targetReached":true,"reason":"the existing starter reaches each target"}],"mutantChecks":[]';
                jsonResponse(
                    res,
                    200,
                    textResponse(
                        requestNumber,
                        `{${audit},"uncovered":[],"contradictions":[],"hiddenRequirements":[],"weakOracle":[],"templateGaps":[],"missingExamples":[],"invented":[],"unrequestedChanges":[],"missingRequestedChanges":[]}`,
                    ),
                );
                return;
            }
            if (body.includes(submitNewExerciseMarker) || body.includes('# Temperature Alert Classification')) {
                if (
                    body.includes('These [task] bindings reference names that match no actual test') &&
                    !hasAcknowledgedToolCall(body, 'write_file', (args) => args.path === 'problem-statement.md' && args.content?.includes('testRepresentativeTemperatures()'))
                ) {
                    jsonResponse(res, 200, toolCallResponse(requestNumber, 'write_file', { path: 'problem-statement.md', content: correctedGeneratedProblemStatement }));
                    return;
                }
                const files = [
                    ['solution/src/temperaturealertclassification/TemperatureClassifier.java', generatedSolution],
                    ['template/src/temperaturealertclassification/TemperatureClassifier.java', generatedTemplate],
                    ['tests/test/temperaturealertclassification/TemperatureClassifierTest.java', generatedTests],
                    ['problem-statement.md', generatedProblemStatement],
                ];
                const nextFile = files.find(([file]) => !hasAcknowledgedToolCall(body, 'write_file', (args) => args.path === file));
                if (nextFile) {
                    jsonResponse(res, 200, toolCallResponse(requestNumber, 'write_file', { path: nextFile[0], content: nextFile[1] }));
                    return;
                }
                jsonResponse(res, 200, toolCallResponse(requestNumber, 'submit', { summary: 'Submitted the generated temperature exercise for deterministic E2E verification.' }));
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
            if (
                body.includes(submitSeedMarker) &&
                body.includes('threshold of 4') &&
                !hasAcknowledgedToolCall(
                    body,
                    'edit_file',
                    (args) => args.path === 'tests/test/de/test/SortingExampleBehaviorTest.java' && args.newText === adaptedBoundaryTestLoopMarker,
                )
            ) {
                jsonResponse(
                    res,
                    200,
                    toolCallResponse(requestNumber, 'edit_file', {
                        path: 'tests/test/de/test/SortingExampleBehaviorTest.java',
                        oldText: 'for (int i = 0; i < 3; i++)',
                        newText: adaptedBoundaryTestLoopMarker,
                    }),
                );
                return;
            }
            if (
                body.includes(submitSeedMarker) &&
                body.includes('threshold of 4') &&
                !hasAcknowledgedToolCall(
                    body,
                    'edit_file',
                    (args) => args.path === 'tests/test/de/test/SortingExampleBehaviorTest.java' && args.newText === adaptedBoundaryTestMessageMarker,
                )
            ) {
                jsonResponse(
                    res,
                    200,
                    toolCallResponse(requestNumber, 'edit_file', {
                        path: 'tests/test/de/test/SortingExampleBehaviorTest.java',
                        oldText: 'The sort algorithm of Context was not BubbleSort for a list with less or equal than 10 dates.',
                        newText: adaptedBoundaryTestMessageMarker,
                    }),
                );
                return;
            }
            if (
                body.includes(submitSeedMarker) &&
                !hasAcknowledgedToolCall(body, 'write_file', (args) => args.path === 'problem-statement.md' && args.content?.includes(correctedSeedStatementMarker))
            ) {
                jsonResponse(res, 200, toolCallResponse(requestNumber, 'write_file', { path: 'problem-statement.md', content: correctedSeedProblemStatement }));
                return;
            }
            if (
                body.includes(submitSeedMarker) &&
                !hasAcknowledgedToolCall(body, 'edit_file', (args) => args.path === 'solution/src/de/test/Policy.java' && args.newText === adaptedPolicyMarker)
            ) {
                jsonResponse(
                    res,
                    200,
                    toolCallResponse(requestNumber, 'edit_file', {
                        path: 'solution/src/de/test/Policy.java',
                        oldText: 'DATES_SIZE_THRESHOLD = 10',
                        newText: adaptedPolicyMarker,
                    }),
                );
                return;
            }
            if (
                body.includes(submitSeedMarker) &&
                !hasAcknowledgedToolCall(body, 'edit_file', (args) => args.path === 'tests/test/de/test/SortingExampleBehaviorTest.java' && args.newText === adaptedTestLoopMarker)
            ) {
                jsonResponse(
                    res,
                    200,
                    toolCallResponse(requestNumber, 'edit_file', {
                        path: 'tests/test/de/test/SortingExampleBehaviorTest.java',
                        oldText: 'for (int i = 0; i < 11; i++)',
                        newText: adaptedTestLoopMarker,
                    }),
                );
                return;
            }
            if (
                body.includes(submitSeedMarker) &&
                !hasAcknowledgedToolCall(
                    body,
                    'edit_file',
                    (args) => args.path === 'tests/test/de/test/SortingExampleBehaviorTest.java' && args.newText === adaptedTestMessageMarker,
                )
            ) {
                jsonResponse(
                    res,
                    200,
                    toolCallResponse(requestNumber, 'edit_file', {
                        path: 'tests/test/de/test/SortingExampleBehaviorTest.java',
                        oldText: 'The sort algorithm of Context was not MergeSort for a list with more than 10 dates.',
                        newText: adaptedTestMessageMarker,
                    }),
                );
                return;
            }
            if (body.includes(submitSeedMarker)) {
                jsonResponse(res, 200, toolCallResponse(requestNumber, 'submit', { summary: 'Submitted the seeded Java exercise for deterministic E2E verification.' }));
                return;
            }
            if (holdUnmatchedRequests) {
                pendingProviderResponses.add(res);
                res.once('close', () => pendingProviderResponses.delete(res));
                return;
            }
            jsonResponse(res, 400, {
                error: { message: 'Unhandled Hyperion E2E LLM request. Configure holdUnmatchedRequests only for scenarios that intentionally cancel a running provider call.' },
            });
        });
        return;
    }

    jsonResponse(res, 404, { error: `Unhandled ${req.method} ${req.url}` });
});

server.listen(port, '127.0.0.1', () => {
    console.log(`Hyperion E2E LLM mock listening on http://127.0.0.1:${port}`);
});

function shutDown() {
    failPendingResponses(pendingLateFailures, 'Hyperion E2E mock is shutting down');
    failPendingResponses(pendingProviderResponses, 'Hyperion E2E mock is shutting down');
    server.close(() => process.exit(0));
}

process.on('SIGTERM', shutDown);
process.on('SIGINT', shutDown);
