import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';

import { LocalCIBuildPlanEditorComponent } from 'app/programming/manage/build-plan-editor/localci-build-plan-editor.component';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { BuildPlanConfigurationService } from 'app/programming/manage/services/build-plan-configuration.service';
import { BuildPhasesTemplateService } from 'app/programming/shared/services/build-phases-template.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { BuildPhase, BuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';

describe('LocalCIBuildPlanEditorComponent', () => {
    let fixture: ComponentFixture<LocalCIBuildPlanEditorComponent>;
    let comp: LocalCIBuildPlanEditorComponent;
    let activatedRoute: MockActivatedRoute;
    let alertService: MockAlertService;
    let programmingExerciseService: ProgrammingExerciseService;
    let buildPlanConfigurationService: BuildPlanConfigurationService;
    let templateBuildPlan: WritableSignal<BuildPlanPhases | undefined>;
    let fetchTemplateStub: ReturnType<typeof vi.fn>;

    const phases: BuildPhase[] = [{ name: 'compile', script: 'echo compile', condition: 'ALWAYS', forceRun: false, resultPaths: [] }];
    const buildPlanConfiguration = JSON.stringify({ phases, dockerImage: 'some-image' });

    beforeEach(() => {
        templateBuildPlan = signal<BuildPlanPhases | undefined>(undefined);
        fetchTemplateStub = vi.fn();
        TestBed.configureTestingModule({
            imports: [LocalCIBuildPlanEditorComponent],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: AlertService, useValue: new MockAlertService() },
                { provide: ProgrammingExerciseService, useValue: new MockProgrammingExerciseService() },
                { provide: BuildPlanConfigurationService, useValue: { updateBuildPlanConfiguration: vi.fn() } },
                { provide: BuildPhasesTemplateService, useValue: { fetchTemplate: fetchTemplateStub, buildPlan: templateBuildPlan } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        fixture = TestBed.createComponent(LocalCIBuildPlanEditorComponent);
        comp = fixture.componentInstance;

        activatedRoute = TestBed.inject(ActivatedRoute) as MockActivatedRoute;
        alertService = TestBed.inject(AlertService) as MockAlertService;
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
        buildPlanConfigurationService = TestBed.inject(BuildPlanConfigurationService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize the editing state from the resolved exercise and load participations', () => {
        const exercise = { id: 7, buildConfig: { buildPlanConfiguration, timeoutSeconds: 90, dockerFlags: '{"network":"none"}' } } as unknown as ProgrammingExercise;
        activatedRoute.data = of({ exercise });
        const findStub = vi
            .spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults')
            .mockReturnValue(of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })));

        comp.ngOnInit();

        expect(comp.phases()).toEqual(phases);
        expect(comp.dockerImage()).toBe('some-image');
        expect(comp.timeout()).toBe(90);
        expect(findStub).toHaveBeenCalledWith(7);
        expect(comp.loadingResults()).toBe(false);
    });

    it('should keep the editor usable and surface an alert when loading the participations fails', () => {
        const exercise = { id: 7, buildConfig: { buildPlanConfiguration, timeoutSeconds: 90 } } as unknown as ProgrammingExercise;
        activatedRoute.data = of({ exercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
        const errorStub = vi.spyOn(alertService, 'error');

        comp.ngOnInit();

        expect(comp.programmingExercise()).toBe(exercise);
        expect(comp.phases()).toEqual(phases);
        expect(comp.loadingResults()).toBe(false);
        expect(errorStub).toHaveBeenCalledWith('error.http.404');
    });

    it('should convert a legacy build script when the exercise has no structured phases', () => {
        const exercise = { id: 7, buildConfig: { buildScript: 'echo hello', timeoutSeconds: 60 } } as unknown as ProgrammingExercise;
        activatedRoute.data = of({ exercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })),
        );

        comp.ngOnInit();

        expect(comp.phases()).toHaveLength(1);
        expect(comp.phases()[0].name).toBe('script');
        expect(comp.phases()[0].script).toContain('echo hello');
        expect(comp.timeout()).toBe(60);
    });

    it('should seed the language default docker image when the exercise configured none', () => {
        const exercise = {
            id: 7,
            programmingLanguage: ProgrammingLanguage.JAVA,
            buildConfig: { buildPlanConfiguration: JSON.stringify({ phases }), timeoutSeconds: 60 },
        } as unknown as ProgrammingExercise;
        activatedRoute.data = of({ exercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })),
        );

        comp.ngOnInit();

        // no image in the stored configuration, so the editor asks the template service for the language default
        expect(fetchTemplateStub).toHaveBeenCalledWith(false, ProgrammingLanguage.JAVA, undefined);
        expect(comp.dockerImage()).toBe('');

        // once the template resolves, its default image fills the empty field
        templateBuildPlan.set({ phases, dockerImage: 'language-default-image' });
        TestBed.tick();

        expect(comp.dockerImage()).toBe('language-default-image');
        // an empty image alone never blocked submitting, so the button is enabled with a valid plan
        expect(comp.canSubmit()).toBe(true);
    });

    it('should not request a template when the exercise already has a docker image', () => {
        const exercise = {
            id: 7,
            programmingLanguage: ProgrammingLanguage.JAVA,
            buildConfig: { buildPlanConfiguration, timeoutSeconds: 90 },
        } as unknown as ProgrammingExercise;
        activatedRoute.data = of({ exercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })),
        );

        comp.ngOnInit();

        expect(comp.dockerImage()).toBe('some-image');
        expect(fetchTemplateStub).not.toHaveBeenCalled();
    });

    it('should submit the build plan configuration and show a success alert', () => {
        comp.programmingExercise.set({ id: 7, buildConfig: { dockerFlags: '{"network":"none"}' } } as unknown as ProgrammingExercise);
        comp.phases.set(phases);
        comp.dockerImage.set('some-image');
        comp.timeout.set(120);
        const updateStub = vi.spyOn(buildPlanConfigurationService, 'updateBuildPlanConfiguration').mockReturnValue(of(new HttpResponse<object>({ body: {} })));
        const successStub = vi.spyOn(alertService, 'success');

        comp.submit();

        expect(updateStub).toHaveBeenCalledWith(7, {
            buildPlan: { phases, dockerImage: 'some-image' },
            timeoutSeconds: 120,
            dockerFlags: '{"network":"none"}',
        });
        expect(successStub).toHaveBeenCalledWith('artemisApp.programmingExercise.buildPlanConfiguration.saved');
        expect(comp.isSaving()).toBe(false);
    });

    it('should trim surrounding whitespace from the docker image before saving', () => {
        comp.programmingExercise.set({ id: 7, buildConfig: {} } as unknown as ProgrammingExercise);
        comp.phases.set(phases);
        comp.dockerImage.set('  some-image  ');
        comp.timeout.set(120);
        const updateStub = vi.spyOn(buildPlanConfigurationService, 'updateBuildPlanConfiguration').mockReturnValue(of(new HttpResponse<object>({ body: {} })));

        comp.submit();

        // the image is validated trimmed, so the whitespace must not end up in the stored configuration
        expect(updateStub).toHaveBeenCalledWith(7, expect.objectContaining({ buildPlan: { phases, dockerImage: 'some-image' } }));
    });

    it('should surface an error alert when saving fails', () => {
        comp.programmingExercise.set({ id: 7, buildConfig: {} } as unknown as ProgrammingExercise);
        comp.phases.set(phases);
        comp.dockerImage.set('some-image');
        vi.spyOn(buildPlanConfigurationService, 'updateBuildPlanConfiguration').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const errorStub = vi.spyOn(alertService, 'error');

        comp.submit();

        expect(errorStub).toHaveBeenCalledWith('error.http.400');
        expect(comp.isSaving()).toBe(false);
    });

    it('should not submit when no exercise is loaded', () => {
        comp.programmingExercise.set(undefined);
        const updateStub = vi.spyOn(buildPlanConfigurationService, 'updateBuildPlanConfiguration');

        comp.submit();

        expect(updateStub).not.toHaveBeenCalled();
    });

    const invalidConfigurations: [string, typeof phases, string][] = [
        ['duplicate phase names', [phases[0], { ...phases[0], name: 'Compile' }], 'some-image'],
        ['a reserved phase name', [{ ...phases[0], name: 'main' }], 'some-image'],
        ['an invalid phase name', [{ ...phases[0], name: 'compile phase' }], 'some-image'],
        ['no phases', [], 'some-image'],
    ];

    it.each(invalidConfigurations)('should not submit with %s', (_description, invalidPhases, dockerImage) => {
        comp.programmingExercise.set({ id: 7, buildConfig: {} } as unknown as ProgrammingExercise);
        comp.phases.set(invalidPhases);
        comp.dockerImage.set(dockerImage);
        const updateStub = vi.spyOn(buildPlanConfigurationService, 'updateBuildPlanConfiguration');

        comp.submit();

        expect(comp.canSubmit()).toBe(false);
        expect(updateStub).not.toHaveBeenCalled();
    });
});
