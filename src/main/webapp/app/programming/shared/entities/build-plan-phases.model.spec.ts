import { describe, expect, it } from 'vitest';

import { BUILD_PHASE_NAME_PATTERN, BUILD_PHASE_RESERVED_NAMES, parseBuildPlanPhases } from './build-plan-phases.model';

describe('build-plan-phases.model', () => {
    it('parses a valid build plan phases json', () => {
        const parsed = parseBuildPlanPhases(
            JSON.stringify({
                phases: [
                    {
                        name: 'compile',
                        script: './gradlew compileTestJava',
                        condition: 'ALWAYS',
                        forceRun: false,
                        resultPaths: ['build/test-results/**/*.xml'],
                    },
                ],
                dockerImage: 'gradle:8.10.2-jdk21',
            }),
        );

        expect(parsed).toEqual({
            phases: [
                {
                    name: 'compile',
                    script: './gradlew compileTestJava',
                    condition: 'ALWAYS',
                    forceRun: false,
                    resultPaths: ['build/test-results/**/*.xml'],
                },
            ],
            dockerImage: 'gradle:8.10.2-jdk21',
        });
    });

    it('returns undefined for invalid json and undefined input', () => {
        expect(parseBuildPlanPhases(undefined)).toBeUndefined();
        expect(parseBuildPlanPhases('{ invalid-json')).toBeUndefined();
    });

    it('returns undefined when phases payload is malformed', () => {
        expect(parseBuildPlanPhases(JSON.stringify({ phases: 'not-an-array' }))).toBeUndefined();

        expect(
            parseBuildPlanPhases(
                JSON.stringify({
                    phases: [
                        {
                            name: 'test',
                            script: './gradlew test',
                            condition: 'SOMETIMES',
                            forceRun: false,
                            resultPaths: [],
                        },
                    ],
                }),
            ),
        ).toBeUndefined();

        expect(
            parseBuildPlanPhases(
                JSON.stringify({
                    phases: [
                        {
                            name: 'test',
                            script: './gradlew test',
                            condition: 'ALWAYS',
                            forceRun: false,
                            resultPaths: [123],
                        },
                    ],
                }),
            ),
        ).toBeUndefined();
    });

    it('validates phase naming constraints used by the editor', () => {
        expect(BUILD_PHASE_NAME_PATTERN.test('compile_phase_1')).toBe(true);
        expect(BUILD_PHASE_NAME_PATTERN.test('1compile')).toBe(false);
        expect(BUILD_PHASE_NAME_PATTERN.test('compile-phase')).toBe(false);

        expect(BUILD_PHASE_RESERVED_NAMES.has('main')).toBe(true);
        expect(BUILD_PHASE_RESERVED_NAMES.has('final_force_run_post_action')).toBe(true);
    });

    it('defaults undefined resultPaths to an empty array', () => {
        const parsed = parseBuildPlanPhases(
            JSON.stringify({
                phases: [
                    {
                        name: 'test',
                        script: './gradlew test',
                        condition: 'ALWAYS',
                        forceRun: false,
                        // resultPaths intentionally omitted
                    },
                ],
            }),
        );
        expect(parsed).toEqual({
            phases: [
                {
                    name: 'test',
                    script: './gradlew test',
                    condition: 'ALWAYS',
                    forceRun: false,
                    resultPaths: [],
                },
            ],
        });
    });
});
