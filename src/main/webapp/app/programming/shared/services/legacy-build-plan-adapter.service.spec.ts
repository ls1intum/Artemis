import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { BuildPhasesTemplateService } from 'app/programming/shared/services/build-phases-template.service';
import { LegacyBuildPlanAdapterService } from 'app/programming/shared/services/legacy-build-plan-adapter.service';

describe('LegacyBuildPlanAdapterService', () => {
    let service: LegacyBuildPlanAdapterService;

    const buildPhasesTemplateServiceMock = {
        getTemplate: vi.fn(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [LegacyBuildPlanAdapterService, { provide: BuildPhasesTemplateService, useValue: buildPhasesTemplateServiceMock }],
        });

        service = TestBed.inject(LegacyBuildPlanAdapterService);
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should extract legacy docker image', () => {
        const dockerImage = service.extractLegacyDockerImage('{"metadata":{"docker":{"image":"legacy/image:2.0"}}}');
        expect(dockerImage).toBe('legacy/image:2.0');
    });

    it('should create wrapped legacy phase and fetch result paths from template endpoint', async () => {
        buildPhasesTemplateServiceMock.getTemplate.mockReturnValue(
            of({
                phases: [
                    { name: 'compile', script: 'echo compile', condition: 'ALWAYS', forceRun: false, resultPaths: ['build/test-results/test/*.xml'] },
                    { name: 'test', script: 'echo test', condition: 'ALWAYS', forceRun: false, resultPaths: ['build/test-results/test/*.xml', 'coverage.xml'] },
                ],
                dockerImage: 'ignored',
            }),
        );

        // Await the observable using firstValueFrom to ensure assertions are actually executed
        const buildPlanPhases = await firstValueFrom(
            service.createBuildPhasesFromLegacyBuildScript(
                './gradlew test',
                '{"metadata":{"docker":{"image":"legacy/image:2.0"}}}',
                ProgrammingLanguage.JAVA,
                ProjectType.PLAIN_MAVEN,
                false,
                false,
            ),
        );

        expect(buildPhasesTemplateServiceMock.getTemplate).toHaveBeenCalledWith(ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, false, false);
        expect(buildPlanPhases.dockerImage).toBe('legacy/image:2.0');
        expect(buildPlanPhases.phases).toHaveLength(1);
        expect(buildPlanPhases.phases[0].name).toBe('script');
        expect(buildPlanPhases.phases[0].resultPaths).toEqual(['build/test-results/test/*.xml', 'coverage.xml']);
        expect(buildPlanPhases.phases[0].script).toContain('cd /var/tmp/testing-dir\n');
        expect(buildPlanPhases.phases[0].script).toContain('cat << \'__LEGACY_INNER_SCRIPT_END__\' > "${tmp_file}"\n');
        expect(buildPlanPhases.phases[0].script).toContain('./gradlew test\n');
    });
});
