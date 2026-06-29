import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { LegacyBuildPlanConverterService } from './legacy-build-plan-converter.service';

describe('LegacyBuildPlanConverterService', () => {
    setupTestBed({ zoneless: true });

    let service: LegacyBuildPlanConverterService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [LegacyBuildPlanConverterService],
        });

        service = TestBed.inject(LegacyBuildPlanConverterService);
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should convert legacy build plan configuration', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration(
            './gradlew test',
            JSON.stringify({
                metadata: { docker: { image: ' legacy/image:2.0 ' } },
                actions: [
                    {
                        name: 'compile',
                        results: [{ path: ' build/test-results/test/*.xml ' }],
                    },
                    {
                        name: 'test',
                        results: [{ path: 'coverage.xml' }],
                    },
                ],
            }),
        );

        expect(buildPlanPhases).toBeDefined();
        expect(buildPlanPhases?.dockerImage).toBe('legacy/image:2.0');
        expect(buildPlanPhases?.phases).toHaveLength(1);
        expect(buildPlanPhases?.phases[0].name).toBe('script');
        expect(buildPlanPhases?.phases[0].resultPaths).toEqual(['build/test-results/test/*.xml', 'coverage.xml']);
        expect(buildPlanPhases?.phases[0].script).toContain('cd /var/tmp/testing-dir\n');
        expect(buildPlanPhases?.phases[0].script).toContain('cat << \'  __LEGACY_INNER_SCRIPT_END__\' > "${tmp_file}"');
        expect(buildPlanPhases?.phases[0].script).toContain('./gradlew test\n');
    });

    it('should convert build script with invalid json', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration('./gradlew test', 'non legacy');

        expect(buildPlanPhases).toBeDefined();
        expect(buildPlanPhases?.dockerImage).toBeUndefined();
        expect(buildPlanPhases?.phases).toHaveLength(1);
        expect(buildPlanPhases?.phases[0].resultPaths).toEqual([]);
        expect(buildPlanPhases?.phases[0].script).toContain('./gradlew test\n');
    });

    it('should return undefined for invalid json without build script', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration(undefined, 'non legacy');
        expect(buildPlanPhases).toBeUndefined();
    });

    it('should return undefined when neither legacy script nor legacy actions exist', () => {
        expect(service.convertLegacyBuildPlanConfiguration(undefined, '{}')).toBeUndefined();
    });

    it('should convert build script when build config is missing', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration('./gradlew test', undefined);

        expect(buildPlanPhases).toBeDefined();
        expect(buildPlanPhases?.dockerImage).toBeUndefined();
        expect(buildPlanPhases?.phases[0].resultPaths).toEqual([]);
        expect(buildPlanPhases?.phases[0].script).toContain('./gradlew test\n');
    });

    it('should convert build script when parsed configuration is not an object', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration('./gradlew test', JSON.stringify(['legacy']));

        expect(buildPlanPhases).toBeDefined();
        expect(buildPlanPhases?.dockerImage).toBeUndefined();
        expect(buildPlanPhases?.phases[0].resultPaths).toEqual([]);
    });

    it('should convert without docker image', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration(
            './gradlew test',
            JSON.stringify({
                actions: [],
            }),
        );

        expect(buildPlanPhases).toBeDefined();
        expect(buildPlanPhases?.dockerImage).toBeUndefined();
    });

    it('should accept blank docker image', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration(
            './gradlew test',
            JSON.stringify({
                metadata: { docker: { image: '   ' } },
                actions: [],
            }),
        );

        expect(buildPlanPhases).toBeDefined();
        expect(buildPlanPhases?.dockerImage).toBe('');
    });

    it('should accept blank result path', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration(
            './gradlew test',
            JSON.stringify({
                metadata: { docker: { image: 'legacy/image:2.0' } },
                actions: [
                    {
                        results: [{ path: '   ' }],
                    },
                ],
            }),
        );

        expect(buildPlanPhases).toBeDefined();
        expect(buildPlanPhases?.phases[0].resultPaths).toEqual(['']);
    });

    it('should convert build script without actions', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration(
            './gradlew test',
            JSON.stringify({
                metadata: { docker: { image: 'legacy/image:2.0' } },
            }),
        );

        expect(buildPlanPhases).toBeDefined();
        expect(buildPlanPhases?.phases[0].resultPaths).toEqual([]);
    });

    it('should convert build script with non-array actions', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration(
            './gradlew test',
            JSON.stringify({
                metadata: { docker: { image: 'legacy/image:2.0' } },
                actions: 'invalid',
            }),
        );

        expect(buildPlanPhases).toBeDefined();
        expect(buildPlanPhases?.phases[0].resultPaths).toEqual([]);
    });

    it('should ignore malformed action results', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration(
            './gradlew test',
            JSON.stringify({
                metadata: { docker: { image: 'legacy/image:2.0' } },
                actions: [
                    {
                        results: 'invalid',
                    },
                ],
            }),
        );

        expect(buildPlanPhases).toBeDefined();
        expect(buildPlanPhases?.phases[0].resultPaths).toEqual([]);
    });

    it('should ignore non-textual result path', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration(
            './gradlew test',
            JSON.stringify({
                metadata: { docker: { image: 'legacy/image:2.0' } },
                actions: [
                    {
                        results: [{ path: 7 }],
                    },
                ],
            }),
        );

        expect(buildPlanPhases).toBeDefined();
        expect(buildPlanPhases?.phases[0].resultPaths).toEqual([]);
    });

    it('should convert legacy actions when build script is missing', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration(
            undefined,
            JSON.stringify({
                metadata: { docker: { image: ' legacy/image:2.0 ' } },
                actions: [
                    {
                        name: 'compile',
                        script: './gradlew compileJava',
                        workdir: 'assignment',
                        results: [{ path: ' build/test-results/compile/*.xml ' }],
                    },
                    {
                        name: 'test',
                        script: './gradlew test',
                        results: [{ path: 'build/test-results/test/*.xml' }],
                    },
                    {
                        name: 'platform-only',
                        platform: 'jenkins',
                    },
                ],
            }),
        );

        expect(buildPlanPhases).toBeDefined();
        expect(buildPlanPhases?.dockerImage).toBe('legacy/image:2.0');
        expect(buildPlanPhases?.phases).toHaveLength(1);
        expect(buildPlanPhases?.phases[0].resultPaths).toEqual(['build/test-results/compile/*.xml', 'build/test-results/test/*.xml']);
        expect(buildPlanPhases?.phases[0].script).toBe(`# feel free to remove the code surrounding your script and split your script into multiple phases
cd /var/tmp/testing-dir
local tmp_file=$(mktemp)
cat << '  __LEGACY_INNER_SCRIPT_END__' > "\${tmp_file}"  # two leading spaces are intentional as the final script will be indented be for a phase
#!/bin/bash
cd /var/tmp/testing-dir
cd /var/tmp/testing-dir/assignment
./gradlew compileJava
cd /var/tmp/testing-dir
./gradlew test

__LEGACY_INNER_SCRIPT_END__
chmod +x "\${tmp_file}"
"\${tmp_file}" "$@"
`);
    });
});
