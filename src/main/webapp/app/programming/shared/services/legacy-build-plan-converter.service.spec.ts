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
        expect(buildPlanPhases?.phases[0].script).toContain('cat << \'  __LEGACY_INNER_SCRIPT_END__\' > "${tmp_file}"\n');
        expect(buildPlanPhases?.phases[0].script).toContain('./gradlew test\n');
    });

    it('should return undefined for invalid json', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration('./gradlew test', 'non legacy');
        expect(buildPlanPhases).toBeUndefined();
    });

    it('should return undefined for missing legacy script or build config', () => {
        expect(service.convertLegacyBuildPlanConfiguration(undefined, '{}')).toBeUndefined();
        expect(service.convertLegacyBuildPlanConfiguration('./gradlew test', undefined)).toBeUndefined();
    });

    it('should return undefined when parsed configuration is not an object', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration('./gradlew test', JSON.stringify(['legacy']));
        expect(buildPlanPhases).toBeUndefined();
    });

    it('should return undefined for missing docker image', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration(
            './gradlew test',
            JSON.stringify({
                actions: [],
            }),
        );

        expect(buildPlanPhases).toBeUndefined();
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

    it('should return undefined for missing actions', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration(
            './gradlew test',
            JSON.stringify({
                metadata: { docker: { image: 'legacy/image:2.0' } },
            }),
        );

        expect(buildPlanPhases).toBeUndefined();
    });

    it('should return undefined for non-array actions', () => {
        const buildPlanPhases = service.convertLegacyBuildPlanConfiguration(
            './gradlew test',
            JSON.stringify({
                metadata: { docker: { image: 'legacy/image:2.0' } },
                actions: 'invalid',
            }),
        );

        expect(buildPlanPhases).toBeUndefined();
    });

    it('should return undefined for malformed action results', () => {
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

        expect(buildPlanPhases).toBeUndefined();
    });

    it('should return undefined for non-textual result path', () => {
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

        expect(buildPlanPhases).toBeUndefined();
    });
});
