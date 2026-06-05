import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BuildPlanEditorComponent } from 'app/programming/manage/build-plan-editor/build-plan-editor.component';
import { BuildPlanService } from 'app/programming/manage/services/build-plan.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, ActivatedRouteSnapshot } from '@angular/router';
import { BuildPlan } from 'app/programming/shared/entities/build-plan.model';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { MockBuildPlanService } from 'test/helpers/mocks/service/mock-build-plan.service';
import { CodeEditorHeaderComponent } from 'app/programming/manage/code-editor/header/code-editor-header.component';
import { MockComponent, MockModule } from 'ng-mocks';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { MonacoEditorComponent } from 'app/editor/monaco-editor/monaco-editor.component';
import { TranslateService } from '@ngx-translate/core';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';

describe('Build Plan Editor', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<BuildPlanEditorComponent>;
    let comp: BuildPlanEditorComponent;

    let activatedRoute: MockActivatedRoute;
    let alertService: MockAlertService;
    let buildPlanService: BuildPlanService;
    let programmingExerciseService: ProgrammingExerciseService;

    let putBuildPlanStub: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                BuildPlanEditorComponent,
                TranslatePipeMock,
                MockModule(NgbTooltipModule),
                MockComponent(MonacoEditorComponent),
                MockComponent(CodeEditorHeaderComponent),
                MockComponent(UpdatingResultComponent),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: AlertService, useValue: new MockAlertService() },
                { provide: BuildPlanService, useValue: new MockBuildPlanService() },
                { provide: ProgrammingExerciseService, useValue: new MockProgrammingExerciseService() },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        fixture = TestBed.createComponent(BuildPlanEditorComponent);
        comp = fixture.componentInstance;

        activatedRoute = TestBed.inject(ActivatedRoute) as MockActivatedRoute;
        alertService = TestBed.inject(AlertService) as MockAlertService;
        buildPlanService = TestBed.inject(BuildPlanService);
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);

        // Provide a baseline route resolve so the ngOnInit triggered by fixture.detectChanges()
        // (needed to resolve the editor viewChild) finds a valid exercise. In production a route
        // resolver always supplies the exercise; the default MockActivatedRoute data does not.
        activatedRoute.data = of({ exercise: { id: 1 } as ProgrammingExercise });
        // ngAfterViewInit (also run by detectChanges) reads snapshot.params.exerciseId; the default
        // MockActivatedRoute snapshot lacks a params object, so supply a baseline. Tests that exercise
        // the load path override this snapshot and re-invoke ngAfterViewInit themselves.
        activatedRoute.snapshot = { params: { exerciseId: 1 } } as unknown as ActivatedRouteSnapshot;

        putBuildPlanStub = vi.spyOn(buildPlanService, 'putBuildPlan');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should not submit a build plan if none is loaded', () => {
        comp.buildPlan = undefined;

        comp.submit();

        expect(putBuildPlanStub).not.toHaveBeenCalled();
    });

    it('should update the build plan on submit', () => {
        // Resolve the editor viewChild so onBuildPlanUpdate() can call setText() on the mocked editor.
        fixture.detectChanges();

        const originalBuildPlan = {
            id: 1,
            buildPlan: 'build plan text',
        } as BuildPlan;
        const buildPlan = {
            id: 2,
            buildPlan: originalBuildPlan.buildPlan,
        } as BuildPlan;

        putBuildPlanStub = putBuildPlanStub.mockReturnValue(of(new HttpResponse<BuildPlan>({ body: buildPlan })));

        comp.exerciseId = 3;
        comp.buildPlan = originalBuildPlan;

        comp.submit();

        expect(putBuildPlanStub).toHaveBeenCalledWith(3, originalBuildPlan);
        expect(comp.buildPlan).toEqual(buildPlan);
    });

    it('should update the build plan text on editor text changes', () => {
        comp.buildPlan = {
            buildPlan: 'empty text',
        } as BuildPlan;

        comp.onTextChanged({ text: 'new text', fileName: 'ignored' });

        expect(comp.buildPlan.buildPlan).toBe('new text');
    });

    it('should load the exercise on init', () => {
        const exercise = {
            id: 3,
        } as ProgrammingExercise;

        // The mocked services emit synchronously (of(...)), so no fake timers are required.
        activatedRoute.data = of({ exercise });
        const getExerciseWithSubmissionsStub = vi
            .spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults')
            .mockReturnValue(of(new HttpResponse<ProgrammingExercise>({ body: exercise })));

        comp.ngOnInit();

        expect(getExerciseWithSubmissionsStub).toHaveBeenCalledWith(exercise.id);
    });

    it('should load the build plan after the editor is initialized', () => {
        // Resolve the editor viewChild so initEditor() can call layout()/setText() on the mocked editor.
        fixture.detectChanges();

        const buildPlan: BuildPlan = {
            id: 2,
            buildPlan: 'some text',
        };

        activatedRoute.snapshot = {
            params: { exerciseId: 3 },
        } as unknown as ActivatedRouteSnapshot;
        const getBuildPlanStub = vi.spyOn(buildPlanService, 'getBuildPlan').mockReturnValue(of(new HttpResponse<BuildPlan>({ body: buildPlan })));

        comp.ngAfterViewInit();

        expect(getBuildPlanStub).toHaveBeenCalledWith(3);
        expect(comp.isLoading).toBe(false);
        expect(comp.buildPlan).toEqual(buildPlan);
    });

    it.each([
        [404, 'artemisApp.programmingExercise.buildPlanFetchError'],
        [405, 'error.http.405'],
    ])('should show an error message if fetching the build plan failed', (status: number, expectedError: string) => {
        // Resolve the editor viewChild before ngAfterViewInit triggers the (synchronous) fetch.
        fixture.detectChanges();

        activatedRoute.snapshot = {
            params: { exerciseId: 3 },
        } as unknown as ActivatedRouteSnapshot;
        const getBuildPlanStub = vi.spyOn(buildPlanService, 'getBuildPlan').mockReturnValue(throwError(() => new HttpResponse<BuildPlan>({ status })));

        const alertStub = vi.spyOn(alertService, 'error');

        comp.ngAfterViewInit();

        expect(getBuildPlanStub).toHaveBeenCalledWith(3);
        expect(comp.isLoading).toBe(false);
        expect(comp.buildPlan).toBeUndefined();

        expect(alertStub).toHaveBeenCalledOnce();
        expect(alertStub).toHaveBeenCalledWith(expectedError);
    });

    it('should update the tab size', () => {
        // Resolve the editor viewChild before reading it.
        fixture.detectChanges();
        const updateTabSizeSpy = vi.spyOn(comp.editor(), 'updateModelIndentationSize');
        comp.updateTabSize(5);
        expect(updateTabSizeSpy).toHaveBeenCalledExactlyOnceWith(5);
    });
});
