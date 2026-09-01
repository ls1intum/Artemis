import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Component, input, output, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';

import { LocalCIBuildPlanEditorComponent } from 'app/programming/manage/build-plan-editor/localci-build-plan-editor.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiButtonComponent, TumUiTooltipDirective } from '@tumaet/ui-angular';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/programming/manage/build-plan-editor/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { BuildPhasesEditorComponent } from 'app/programming/manage/build-plan-editor/build-phases-editor/build-phases-editor.component';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { BuildPlanConfigurationService } from 'app/programming/manage/services/build-plan-configuration.service';
import { BUILD_PLAN_CONFIGURATION_MAX_LENGTH } from 'app/programming/shared/entities/programming-exercise-build.config';
import { BuildPhasesTemplateService } from 'app/programming/shared/services/build-phases-template.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { BuildContainer, BuildPhase, BuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';

/**
 * Lightweight stand-in for the build configuration child. It exposes the timeout bounds that the editor reads through its
 * view child, so rendering the template lets the timeout validation be exercised for real instead of against an absent
 * view child. It provides itself as the real component so the {@link viewChild} query resolves to this stub.
 */
@Component({
    selector: 'jhi-programming-exercise-build-configuration',
    template: '',
    providers: [{ provide: ProgrammingExerciseBuildConfigurationComponent, useExisting: StubProgrammingExerciseBuildConfigurationComponent }],
})
class StubProgrammingExerciseBuildConfigurationComponent {
    readonly programmingExercise = input<ProgrammingExercise>();
    readonly timeout = input<number>();
    readonly timeoutChange = output<number>();
    readonly timeoutMinValue = signal<number | undefined>(undefined);
    readonly timeoutMaxValue = signal<number | undefined>(undefined);
    readonly areDockerResourcesValid = signal(true);
    readonly areDockerFlagsWithinSizeLimit = signal(true);
}

describe('LocalCIBuildPlanEditorComponent', () => {
    let fixture: ComponentFixture<LocalCIBuildPlanEditorComponent>;
    let comp: LocalCIBuildPlanEditorComponent;
    let activatedRoute: MockActivatedRoute;
    let alertService: MockAlertService;
    let programmingExerciseService: ProgrammingExerciseService;
    let buildPlanConfigurationService: BuildPlanConfigurationService;
    let getTemplateSubject: Subject<BuildPlanPhases>;
    let getTemplateStub: ReturnType<typeof vi.fn>;

    const phases: BuildPhase[] = [{ name: 'compile', script: 'echo compile', condition: 'ALWAYS', forceRun: false, resultPaths: [] }];
    const buildPlanConfiguration = JSON.stringify({ phases, dockerImage: 'some-image' });
    const container = (name: string) => ({ name, dockerImage: 'some-image', phases });

    beforeEach(() => {
        // a controllable subject so a test can decide when (and whether) the requested template resolves
        getTemplateSubject = new Subject<BuildPlanPhases>();
        getTemplateStub = vi.fn(() => getTemplateSubject.asObservable());
        TestBed.configureTestingModule({
            imports: [LocalCIBuildPlanEditorComponent],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: AlertService, useValue: new MockAlertService() },
                { provide: ProgrammingExerciseService, useValue: new MockProgrammingExerciseService() },
                { provide: BuildPlanConfigurationService, useValue: { updateBuildPlanConfiguration: vi.fn() } },
                { provide: BuildPhasesTemplateService, useValue: { getTemplate: getTemplateStub } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        // Replace the template children with lightweight mocks (and a stub that reports timeout bounds) so the page can be
        // rendered and the view-child-based timeout validation is actually exercised.
        TestBed.overrideComponent(LocalCIBuildPlanEditorComponent, {
            remove: {
                imports: [
                    ProgrammingExerciseBuildConfigurationComponent,
                    BuildPhasesEditorComponent,
                    TumUiButtonComponent,
                    TumUiTooltipDirective,
                    HelpIconComponent,
                    UpdatingResultComponent,
                    TranslateDirective,
                    ArtemisTranslatePipe,
                ],
            },
            add: {
                imports: [
                    StubProgrammingExerciseBuildConfigurationComponent,
                    MockComponent(BuildPhasesEditorComponent),
                    MockComponent(TumUiButtonComponent),
                    MockDirective(TumUiTooltipDirective),
                    MockComponent(HelpIconComponent),
                    MockComponent(UpdatingResultComponent),
                    MockDirective(TranslateDirective),
                    MockPipe(ArtemisTranslatePipe),
                ],
            },
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

        // a legacy build plan is normalized into a single container, so the editor only deals with containers
        expect(comp.containers()).toEqual([{ name: 'default', dockerImage: 'some-image', phases }]);
        expect(comp.timeout()).toBe(90);
        expect(findStub).toHaveBeenCalledWith(7);
        expect(comp.loadingResults()).toBe(false);
    });

    it('should ignore a stale participation response once a different exercise is active', () => {
        const phasesB: BuildPhase[] = [{ name: 'build', script: 'echo build', condition: 'ALWAYS', forceRun: false, resultPaths: [] }];
        const exerciseA = {
            id: 7,
            buildConfig: { buildPlanConfiguration: JSON.stringify({ phases, dockerImage: 'image-a' }), timeoutSeconds: 60 },
        } as unknown as ProgrammingExercise;
        const exerciseB = {
            id: 8,
            buildConfig: { buildPlanConfiguration: JSON.stringify({ phases: phasesB, dockerImage: 'image-b' }), timeoutSeconds: 120 },
        } as unknown as ProgrammingExercise;

        // exercise A's participation lookup is deferred so it can resolve after B has become active; B's resolves at once
        const participationResponseA = new Subject<HttpResponse<ProgrammingExercise>>();
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults')
            .mockReturnValueOnce(participationResponseA.asObservable())
            .mockReturnValueOnce(of(new HttpResponse<ProgrammingExercise>({ body: { id: 8 } as ProgrammingExercise })));

        // drive both exercises through the same component instance, mimicking a same-route navigation
        const routeData = new Subject<{ exercise: ProgrammingExercise }>();
        activatedRoute.data = routeData.asObservable();
        comp.ngOnInit();

        routeData.next({ exercise: exerciseA });
        routeData.next({ exercise: exerciseB });

        // B is active with B's editing state before A's lookup returns
        expect(comp.programmingExercise()?.id).toBe(8);
        expect(comp.containers()).toEqual([{ name: 'default', dockerImage: 'image-b', phases: phasesB }]);

        // A's participation response arrives late; it must not replace the active exercise
        participationResponseA.next(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise }));
        participationResponseA.complete();

        expect(comp.programmingExercise()?.id).toBe(8);
        expect(comp.containers()).toEqual([{ name: 'default', dockerImage: 'image-b', phases: phasesB }]);
    });

    it('should ignore a stale participation error once a different exercise is active', () => {
        const exerciseA = { id: 7, buildConfig: { buildPlanConfiguration, timeoutSeconds: 60 } } as unknown as ProgrammingExercise;
        const exerciseB = { id: 8, buildConfig: { buildPlanConfiguration, timeoutSeconds: 120 } } as unknown as ProgrammingExercise;

        // exercise A's participation lookup is deferred so it can fail after B has become active; B's resolves at once
        const participationResponseA = new Subject<HttpResponse<ProgrammingExercise>>();
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults')
            .mockReturnValueOnce(participationResponseA.asObservable())
            .mockReturnValueOnce(of(new HttpResponse<ProgrammingExercise>({ body: { id: 8 } as ProgrammingExercise })));
        const errorStub = vi.spyOn(alertService, 'error');

        const routeData = new Subject<{ exercise: ProgrammingExercise }>();
        activatedRoute.data = routeData.asObservable();
        comp.ngOnInit();

        routeData.next({ exercise: exerciseA });
        routeData.next({ exercise: exerciseB });
        expect(comp.programmingExercise()?.id).toBe(8);

        // A's lookup fails late; the stale error must not replace the active exercise nor surface an alert for the
        // exercise that is no longer on screen (404 surfaces an alert via onError, unlike a 500)
        participationResponseA.error(new HttpErrorResponse({ status: 404 }));

        expect(comp.programmingExercise()?.id).toBe(8);
        expect(errorStub).not.toHaveBeenCalled();
    });

    it('should keep the editor usable and surface an alert when loading the participations fails', () => {
        const exercise = { id: 7, buildConfig: { buildPlanConfiguration, timeoutSeconds: 90 } } as unknown as ProgrammingExercise;
        activatedRoute.data = of({ exercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
        const errorStub = vi.spyOn(alertService, 'error');

        comp.ngOnInit();

        expect(comp.programmingExercise()).toBe(exercise);
        expect(comp.containers()).toEqual([{ name: 'default', dockerImage: 'some-image', phases }]);
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

        expect(comp.containers()).toHaveLength(1);
        expect(comp.containers()[0].phases).toHaveLength(1);
        expect(comp.containers()[0].phases[0].name).toBe('script');
        expect(comp.containers()[0].phases[0].script).toContain('echo hello');
        expect(comp.timeout()).toBe(60);
    });

    it('should seed the default phases when the exercise stored no build plan configuration', () => {
        const exercise = {
            id: 7,
            programmingLanguage: ProgrammingLanguage.JAVA,
            buildConfig: { timeoutSeconds: 60 },
        } as unknown as ProgrammingExercise;
        activatedRoute.data = of({ exercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })),
        );

        comp.ngOnInit();

        // a null configuration builds on the language default, so the editor opens empty and asks for the template
        expect(comp.containers()).toHaveLength(0);
        expect(getTemplateStub).toHaveBeenCalledWith(false, ProgrammingLanguage.JAVA, undefined, undefined, undefined);

        // the template's phases fill the editor so it shows the real default plan instead of an empty list
        getTemplateSubject.next({ phases, dockerImage: 'language-default-image' });

        expect(comp.containers()).toEqual([{ name: 'default', phases }]);
        expect(comp.defaultDockerImage()).toBe('language-default-image');
    });

    const templateFlagCases: [string, Partial<ProgrammingExercise>, boolean | undefined, boolean | undefined][] = [
        ['static code analysis', { staticCodeAnalysisEnabled: true }, true, undefined],
        ['sequential test runs', { buildConfig: { sequentialTestRuns: true } } as Partial<ProgrammingExercise>, undefined, true],
    ];

    it.each(templateFlagCases)('should request the template with the exercise %s setting', (_description, overrides, expectedStaticAnalysis, expectedSequentialRuns) => {
        const exercise = {
            id: 7,
            programmingLanguage: ProgrammingLanguage.JAVA,
            ...overrides,
            buildConfig: { timeoutSeconds: 60, ...(overrides.buildConfig ?? {}) },
        } as unknown as ProgrammingExercise;
        activatedRoute.data = of({ exercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })),
        );

        comp.ngOnInit();

        // these settings select a different template file on the server, so omitting them would seed a plan the exercise
        // would not otherwise be built with
        expect(getTemplateStub).toHaveBeenCalledWith(false, ProgrammingLanguage.JAVA, undefined, expectedStaticAnalysis, expectedSequentialRuns);
    });

    it('should keep an edit made while the default phases were loading unsaved', () => {
        const exercise = {
            id: 7,
            programmingLanguage: ProgrammingLanguage.JAVA,
            buildConfig: { timeoutSeconds: 60 },
        } as unknown as ProgrammingExercise;
        activatedRoute.data = of({ exercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })),
        );

        comp.ngOnInit();
        expect(comp.canDeactivate()).toBe(true);

        // the instructor edits a field the template does not seed while the request is still running
        comp.timeout.set(200);
        getTemplateSubject.next({ phases, dockerImage: 'language-default-image' });

        // only the seeded containers may be folded into the baseline, so the timeout edit is still unsaved
        expect(comp.containers()).toEqual([{ name: 'default', phases }]);
        expect(comp.timeout()).toBe(200);
        expect(comp.canDeactivate()).toBe(false);
    });

    it('should not overwrite phases authored while the template request was in flight', () => {
        const exercise = {
            id: 7,
            programmingLanguage: ProgrammingLanguage.JAVA,
            buildConfig: { timeoutSeconds: 60 },
        } as unknown as ProgrammingExercise;
        activatedRoute.data = of({ exercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })),
        );

        comp.ngOnInit();

        // the instructor authored a container before the template came back, so seeding must not discard that work
        const authoredContainers = [{ ...container('tests'), phases: [{ ...phases[0], name: 'authored' }] }];
        comp.containers.set(authoredContainers);
        getTemplateSubject.next({ phases, dockerImage: 'language-default-image' });

        expect(comp.containers()).toEqual(authoredContainers);
    });

    it('should not apply a resolved template once a different exercise is open', () => {
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
        expect(comp.defaultDockerImage()).toBe('');

        // the instructor navigated on: a different exercise is now shown before the slow template response arrives
        comp.programmingExercise.set({ id: 8 } as unknown as ProgrammingExercise);
        getTemplateSubject.next({ phases, dockerImage: 'language-default-image' });

        // the late response belongs to exercise 7, not the one now on screen, so it is discarded
        expect(comp.defaultDockerImage()).toBe('');
    });

    it('should not request a template when the exercise already has both a docker image and phases', () => {
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

        // nothing is missing, so there is no reason to fetch the template
        expect(comp.containers()).toEqual([{ name: 'default', dockerImage: 'some-image', phases }]);
        expect(getTemplateStub).not.toHaveBeenCalled();
    });

    it('should treat a stored timeout of 0 as valid without pinning it', () => {
        activatedRoute.data = of({ exercise: { id: 7, buildConfig: { buildPlanConfiguration } } as unknown as ProgrammingExercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })),
        );

        fixture.detectChanges();
        const buildConfiguration = (comp as unknown as { buildConfigurationComponent: () => StubProgrammingExerciseBuildConfigurationComponent }).buildConfigurationComponent();
        buildConfiguration.timeoutMinValue.set(10);
        buildConfiguration.timeoutMaxValue.set(240);

        // 0 means "use the global default", so even though it is below the minimum it must not block saving or show the error
        expect(comp.timeout()).toBe(0);
        expect(comp.isTimeoutValid()).toBe(true);
        expect(comp.canSubmit()).toBe(true);
        expect(fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.programmingExercise.timeout.outOfBounds"]'))).toBeNull();

        const updateStub = vi.spyOn(buildPlanConfigurationService, 'updateBuildPlanConfiguration').mockReturnValue(of(new HttpResponse<object>({ body: {} })));
        comp.submit();

        // the untouched default must be persisted as 0, not rewritten to a concrete value that stops following default bumps
        expect(updateStub).toHaveBeenCalledWith(7, expect.objectContaining({ timeoutSeconds: 0 }));
    });

    it('should submit the build plan configuration and show a success alert', () => {
        comp.programmingExercise.set({ id: 7, buildConfig: { dockerFlags: '{"network":"none"}' } } as unknown as ProgrammingExercise);
        comp.containers.set([container('student_tests')]);
        comp.timeout.set(120);
        const updateStub = vi.spyOn(buildPlanConfigurationService, 'updateBuildPlanConfiguration').mockReturnValue(of(new HttpResponse<object>({ body: {} })));
        const successStub = vi.spyOn(alertService, 'success');

        comp.submit();

        expect(updateStub).toHaveBeenCalledWith(7, {
            buildPlan: { containers: [container('student_tests')] },
            timeoutSeconds: 120,
            dockerFlags: '{"network":"none"}',
        });
        expect(successStub).toHaveBeenCalledWith('artemisApp.programmingExercise.buildPlanConfiguration.saved');
        expect(comp.isSaving()).toBe(false);
    });

    it('should ignore a second submit while the first is still in flight', () => {
        comp.programmingExercise.set({ id: 7, buildConfig: {} } as unknown as ProgrammingExercise);
        comp.containers.set([container('tests')]);
        comp.timeout.set(120);
        // a subject that never emits keeps the first save in flight, so isSaving stays true across the second call
        const updateStub = vi.spyOn(buildPlanConfigurationService, 'updateBuildPlanConfiguration').mockReturnValue(new Subject<HttpResponse<object>>().asObservable());

        comp.submit();
        comp.submit();

        // the double click must not send a second identical PUT
        expect(updateStub).toHaveBeenCalledTimes(1);
        expect(comp.isSaving()).toBe(true);
    });

    it('should surface an error alert when saving fails', () => {
        comp.programmingExercise.set({ id: 7, buildConfig: {} } as unknown as ProgrammingExercise);
        comp.containers.set([container('student_tests')]);
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

    it('should block submitting when the timeout is outside the bounds reported by the build configuration', () => {
        activatedRoute.data = of({ exercise: { id: 7, buildConfig: { buildPlanConfiguration } } as unknown as ProgrammingExercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })),
        );

        // render the template so the build configuration view child resolves; without rendering the timeout check reads an
        // absent view child and can never fail
        fixture.detectChanges();
        const buildConfiguration = (comp as unknown as { buildConfigurationComponent: () => StubProgrammingExerciseBuildConfigurationComponent }).buildConfigurationComponent();
        buildConfiguration.timeoutMinValue.set(10);
        buildConfiguration.timeoutMaxValue.set(240);
        comp.containers.set([container('tests')]);

        const timeoutMessage = () => fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.programmingExercise.timeout.outOfBounds"]'));

        comp.timeout.set(5);
        fixture.detectChanges();
        expect(comp.canSubmit()).toBe(false);
        // a stored timeout below the minimum must surface a message instead of only disabling save
        expect(timeoutMessage()).not.toBeNull();

        comp.timeout.set(300);
        fixture.detectChanges();
        expect(comp.canSubmit()).toBe(false);
        expect(timeoutMessage()).not.toBeNull();

        comp.timeout.set(120);
        fixture.detectChanges();
        expect(comp.canSubmit()).toBe(true);
        expect(timeoutMessage()).toBeNull();
    });

    it('should block submitting while a docker resource limit is invalid', () => {
        activatedRoute.data = of({ exercise: { id: 7, buildConfig: { buildPlanConfiguration } } as unknown as ProgrammingExercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })),
        );

        fixture.detectChanges();
        const buildConfiguration = (comp as unknown as { buildConfigurationComponent: () => StubProgrammingExerciseBuildConfigurationComponent }).buildConfigurationComponent();
        expect(comp.canSubmit()).toBe(true);

        // an invalid cpu, memory or memory swap value would be rejected by the server, so saving is blocked up front
        buildConfiguration.areDockerResourcesValid.set(false);
        expect(comp.canSubmit()).toBe(false);

        buildConfiguration.areDockerResourcesValid.set(true);
        expect(comp.canSubmit()).toBe(true);
    });

    it('should block submitting while the docker flags exceed the maximum length', () => {
        activatedRoute.data = of({ exercise: { id: 7, buildConfig: { buildPlanConfiguration } } as unknown as ProgrammingExercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })),
        );

        fixture.detectChanges();
        const buildConfiguration = (comp as unknown as { buildConfigurationComponent: () => StubProgrammingExerciseBuildConfigurationComponent }).buildConfigurationComponent();
        expect(comp.canSubmit()).toBe(true);

        // the server rejects oversized docker flags, so saving is blocked up front; the message itself is rendered by the
        // build configuration component, next to the environment variables that cause it
        buildConfiguration.areDockerFlagsWithinSizeLimit.set(false);
        expect(comp.canSubmit()).toBe(false);

        buildConfiguration.areDockerFlagsWithinSizeLimit.set(true);
        expect(comp.canSubmit()).toBe(true);
    });

    it('should allow leaving with no edits and prompt once the plan is edited', () => {
        activatedRoute.data = of({ exercise: { id: 7, buildConfig: { buildPlanConfiguration } } as unknown as ProgrammingExercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })),
        );

        comp.ngOnInit();

        // the loaded state matches the persisted baseline, so leaving is free
        expect(comp.canDeactivate()).toBe(true);

        // an edit diverges from the baseline, so the guard must prompt
        comp.containers.set([{ ...container('tests'), phases: [{ ...phases[0], name: 'renamed' }] }]);
        expect(comp.canDeactivate()).toBe(false);
    });

    it('should treat edited docker flags as unsaved changes', () => {
        const exercise = { id: 7, buildConfig: { buildPlanConfiguration, dockerFlags: '{"network":"none"}' } } as unknown as ProgrammingExercise;
        activatedRoute.data = of({ exercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })),
        );

        comp.ngOnInit();
        expect(comp.canDeactivate()).toBe(true);

        // the build configuration child edits the docker flags in place, and submit() persists them, so they must count
        // towards the unsaved-changes check
        comp.programmingExercise()!.buildConfig!.dockerFlags = '{"network":"none","cpuCount":4}';
        expect(comp.canDeactivate()).toBe(false);
    });

    it('should warn on closing or reloading the page only while there are unsaved changes', () => {
        activatedRoute.data = of({ exercise: { id: 7, buildConfig: { buildPlanConfiguration } } as unknown as ProgrammingExercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })),
        );
        const event = { preventDefault: vi.fn() } as unknown as BeforeUnloadEvent;

        comp.ngOnInit();

        // the router guard does not cover closing the tab or reloading, so the browser has to be asked to confirm as well
        expect(comp.unloadNotification(event)).toBe(true);
        expect(event.preventDefault).not.toHaveBeenCalled();

        comp.containers.set([{ ...container('tests'), phases: [{ ...phases[0], name: 'renamed' }] }]);

        expect(comp.unloadNotification(event)).toBe('pendingChanges');
        expect(event.preventDefault).toHaveBeenCalled();
    });

    it('should keep an edit made while the save is in flight unsaved', () => {
        comp.programmingExercise.set({ id: 7, buildConfig: {} } as unknown as ProgrammingExercise);
        comp.containers.set([container('tests')]);
        comp.timeout.set(120);
        // a controllable response so the editor can be edited while the request is still running
        const response = new Subject<HttpResponse<object>>();
        vi.spyOn(buildPlanConfigurationService, 'updateBuildPlanConfiguration').mockReturnValue(response.asObservable());

        comp.submit();

        // the fields stay editable during the save, so this edit is never part of the request
        const editedDuringSave = [{ ...container('tests'), phases: [{ ...phases[0], name: 'edited_during_save' }] }];
        comp.containers.set(editedDuringSave);
        response.next(new HttpResponse<object>({ body: {} }));
        response.complete();

        // only the submitted state may become the baseline, otherwise this edit is silently discarded on navigation
        expect(comp.containers()).toEqual(editedDuringSave);
        expect(comp.canDeactivate()).toBe(false);
    });

    it('should allow leaving again after a successful save', () => {
        comp.programmingExercise.set({ id: 7, buildConfig: {} } as unknown as ProgrammingExercise);
        comp.containers.set([container('tests')]);
        comp.timeout.set(120);
        // nothing has been persisted as the baseline yet, so the unsaved state blocks leaving
        expect(comp.canDeactivate()).toBe(false);
        vi.spyOn(buildPlanConfigurationService, 'updateBuildPlanConfiguration').mockReturnValue(of(new HttpResponse<object>({ body: {} })));

        comp.submit();

        // the save rebaselines the state, so leaving is free again
        expect(comp.canDeactivate()).toBe(true);
    });

    it('should not treat seeded default phases as unsaved changes', () => {
        const exercise = {
            id: 7,
            programmingLanguage: ProgrammingLanguage.JAVA,
            buildConfig: { timeoutSeconds: 60 },
        } as unknown as ProgrammingExercise;
        activatedRoute.data = of({ exercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })),
        );

        comp.ngOnInit();
        expect(comp.canDeactivate()).toBe(true);

        // seeding the default plan is not a user edit, so leaving must still be free
        getTemplateSubject.next({ phases, dockerImage: 'language-default-image' });
        expect(comp.canDeactivate()).toBe(true);
    });

    const invalidConfigurations: [string, BuildContainer[]][] = [
        ['duplicate phase names in a container', [{ ...container('tests'), phases: [phases[0], { ...phases[0], name: 'Compile' }] }]],
        ['a reserved phase name', [{ ...container('tests'), phases: [{ ...phases[0], name: 'main' }] }]],
        ['an invalid phase name', [{ ...container('tests'), phases: [{ ...phases[0], name: 'compile phase' }] }]],
        ['a container without phases', [{ ...container('tests'), phases: [] }]],
        ['no containers', []],
        ['duplicate container names', [container('tests'), { ...container('Tests') }]],
        ['an invalid container name', [container('student tests')]],
    ];

    it.each(invalidConfigurations)('should not submit with %s', (_description, invalidContainers) => {
        comp.programmingExercise.set({ id: 7, buildConfig: {} } as unknown as ProgrammingExercise);
        comp.containers.set(invalidContainers);
        const updateStub = vi.spyOn(buildPlanConfigurationService, 'updateBuildPlanConfiguration');

        comp.submit();

        expect(comp.canSubmit()).toBe(false);
        expect(updateStub).not.toHaveBeenCalled();
    });

    it('should not submit when the serialized build plan configuration exceeds the maximum length', () => {
        comp.programmingExercise.set({ id: 7, buildConfig: {} } as unknown as ProgrammingExercise);
        // one oversized script is enough to push the serialized plan past the server-side limit
        comp.containers.set([{ ...container('tests'), phases: [{ ...phases[0], script: 'a'.repeat(BUILD_PLAN_CONFIGURATION_MAX_LENGTH) }] }]);
        const updateStub = vi.spyOn(buildPlanConfigurationService, 'updateBuildPlanConfiguration');

        comp.submit();

        expect(comp.isBuildPlanConfigurationWithinSizeLimit()).toBe(false);
        expect(comp.canSubmit()).toBe(false);
        expect(updateStub).not.toHaveBeenCalled();
    });

    it('should submit a build plan configuration that stays within the maximum length', () => {
        comp.programmingExercise.set({ id: 7, buildConfig: {} } as unknown as ProgrammingExercise);
        comp.containers.set([container('tests')]);

        expect(comp.isBuildPlanConfigurationWithinSizeLimit()).toBe(true);
        expect(comp.canSubmit()).toBe(true);
    });

    it.each([
        ['an empty phase script', ''],
        ['a whitespace-only phase script', '   '],
    ])('should submit with %s, because the server accepts it and the phase just does nothing', (_description, blankScript) => {
        comp.programmingExercise.set({ id: 7, buildConfig: {} } as unknown as ProgrammingExercise);
        comp.containers.set([{ ...container('tests'), phases: [{ ...phases[0], script: blankScript }] }]);
        const updateStub = vi.spyOn(buildPlanConfigurationService, 'updateBuildPlanConfiguration').mockReturnValue(of(new HttpResponse<object>({ body: {} })));

        comp.submit();

        expect(comp.canSubmit()).toBe(true);
        expect(updateStub).toHaveBeenCalledOnce();
    });

    it('should keep an inheriting container editable and save it without pinning an image', () => {
        // a legacy exercise relying on the language default normalizes into one container with no image; it must stay
        // savable and the save must not write an image, otherwise the exercise stops following default image bumps
        const inheritingConfiguration = JSON.stringify({ phases });
        activatedRoute.data = of({ exercise: { id: 7, buildConfig: { buildPlanConfiguration: inheritingConfiguration, timeoutSeconds: 120 } } as unknown as ProgrammingExercise });
        vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            of(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise })),
        );
        const updateStub = vi.spyOn(buildPlanConfigurationService, 'updateBuildPlanConfiguration').mockReturnValue(of(new HttpResponse<object>({ body: {} })));

        comp.ngOnInit();

        expect(comp.canSubmit()).toBe(true);
        comp.submit();

        expect(updateStub).toHaveBeenCalledWith(7, expect.objectContaining({ buildPlan: { containers: [{ name: 'default', dockerImage: undefined, phases }] } }));
    });

    it('should trim a container image and send undefined for a blank one', () => {
        comp.programmingExercise.set({ id: 7, buildConfig: {} } as unknown as ProgrammingExercise);
        comp.containers.set([
            { ...container('pinned'), dockerImage: '  some-image  ' },
            { ...container('inheriting'), dockerImage: '   ' },
        ]);
        comp.timeout.set(120);
        const updateStub = vi.spyOn(buildPlanConfigurationService, 'updateBuildPlanConfiguration').mockReturnValue(of(new HttpResponse<object>({ body: {} })));

        comp.submit();

        const sent = updateStub.mock.calls[0][1].buildPlan.containers!;
        expect(sent.map((sentContainer) => sentContainer.dockerImage)).toEqual(['some-image', undefined]);
    });

    it('should allow the same phase name in different containers', () => {
        // containers execute independently, so a phase name only has to be unique within its container
        comp.containers.set([container('student_tests'), container('instructor_tests')]);

        expect(comp.canSubmit()).toBe(true);
    });

    it('should add and remove containers', () => {
        comp.containers.set([container('student_tests')]);

        comp.addContainer();
        expect(comp.containers()).toHaveLength(2);
        // a new container reuses the image of the first one, as exercises usually build every container from one image
        expect(comp.containers()[1].dockerImage).toBe('some-image');
        // it checks out the repositories configured on the exercise until they are scoped explicitly
        expect(comp.containers()[1].repositories).toBeUndefined();

        comp.removeContainer(0);
        expect(comp.containers()).toEqual([{ name: '', dockerImage: 'some-image', phases: [] }]);
    });
});
