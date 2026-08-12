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
import { BuildPhasesTemplateService } from 'app/programming/shared/services/build-phases-template.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { BuildPhase, BuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';

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
    readonly dockerImage = input<string>();
    readonly timeout = input<number>();
    readonly dockerImageChange = output<string>();
    readonly timeoutChange = output<number>();
    readonly timeoutMinValue = signal<number | undefined>(undefined);
    readonly timeoutMaxValue = signal<number | undefined>(undefined);
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

        expect(comp.phases()).toEqual(phases);
        expect(comp.dockerImage()).toBe('some-image');
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
        expect(comp.phases()).toEqual(phasesB);

        // A's participation response arrives late; it must not replace the active exercise
        participationResponseA.next(new HttpResponse<ProgrammingExercise>({ body: { id: 7 } as ProgrammingExercise }));
        participationResponseA.complete();

        expect(comp.programmingExercise()?.id).toBe(8);
        expect(comp.phases()).toEqual(phasesB);
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
        expect(getTemplateStub).toHaveBeenCalledWith(false, ProgrammingLanguage.JAVA, undefined);
        expect(comp.dockerImage()).toBe('');

        // once the template resolves, its default image fills the empty field
        getTemplateSubject.next({ phases, dockerImage: 'language-default-image' });

        expect(comp.dockerImage()).toBe('language-default-image');
        // an empty image alone never blocked submitting, so the button is enabled with a valid plan
        expect(comp.canSubmit()).toBe(true);
    });

    it('should not apply a resolved template image once a different exercise is open', () => {
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
        expect(comp.dockerImage()).toBe('');

        // the instructor navigated on: a different exercise is now shown before the slow template response arrives
        comp.programmingExercise.set({ id: 8 } as unknown as ProgrammingExercise);
        getTemplateSubject.next({ phases, dockerImage: 'language-default-image' });

        // the late response belongs to exercise 7, not the one now on screen, so it is discarded
        expect(comp.dockerImage()).toBe('');
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
        expect(getTemplateStub).not.toHaveBeenCalled();
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
        comp.phases.set(phases);
        comp.dockerImage.set('some-image');

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

    const invalidConfigurations: [string, typeof phases, string][] = [
        ['duplicate phase names', [phases[0], { ...phases[0], name: 'Compile' }], 'some-image'],
        ['a reserved phase name', [{ ...phases[0], name: 'main' }], 'some-image'],
        ['an invalid phase name', [{ ...phases[0], name: 'compile phase' }], 'some-image'],
        ['an empty phase script', [{ ...phases[0], script: '' }], 'some-image'],
        ['a whitespace-only phase script', [{ ...phases[0], script: '   ' }], 'some-image'],
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
