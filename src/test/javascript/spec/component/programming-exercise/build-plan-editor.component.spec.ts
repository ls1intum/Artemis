import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, flush, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { BuildPlanEditorComponent } from 'app/exercises/programming/manage/build-plan-editor.component';
import { BuildPlanService } from 'app/exercises/programming/manage/services/build-plan.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, ActivatedRouteSnapshot } from '@angular/router';
import { BuildPlan } from 'app/entities/build-plan.model';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { MockBuildPlanService } from '../../helpers/mocks/service/mock-build-plan.service';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { CodeEditorHeaderComponent } from 'app/exercises/programming/shared/code-editor/header/code-editor-header.component';
import { MockComponent } from 'ng-mocks';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { NgbTooltipMocksModule } from '../../helpers/mocks/directive/ngbTooltipMocks.module';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../helpers/mocks/service/mock-alert.service';

describe('Build Plan Editor', () => {
    let fixture: ComponentFixture<BuildPlanEditorComponent>;
    let comp: BuildPlanEditorComponent;

    let activatedRoute: MockActivatedRoute;
    let alertService: MockAlertService;
    let buildPlanService: BuildPlanService;
    let programmingExerciseService: ProgrammingExerciseService;

    let putBuildPlanStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, AceEditorModule, NgbTooltipMocksModule],
            declarations: [BuildPlanEditorComponent, TranslatePipeMock, MockComponent(CodeEditorHeaderComponent), MockComponent(UpdatingResultComponent)],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: AlertService, useValue: new MockAlertService() },
                { provide: BuildPlanService, useValue: new MockBuildPlanService() },
                { provide: ProgrammingExerciseService, useValue: new MockProgrammingExerciseService() },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(BuildPlanEditorComponent);
                comp = fixture.componentInstance;

                activatedRoute = fixture.debugElement.injector.get(ActivatedRoute) as MockActivatedRoute;
                alertService = fixture.debugElement.injector.get(AlertService) as MockAlertService;
                buildPlanService = fixture.debugElement.injector.get(BuildPlanService);
                programmingExerciseService = fixture.debugElement.injector.get(ProgrammingExerciseService);

                putBuildPlanStub = jest.spyOn(buildPlanService, 'putBuildPlan');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should not submit a build plan if none is loaded', () => {
        comp.buildPlan = undefined;

        comp.submit();

        expect(putBuildPlanStub).not.toHaveBeenCalled();
    });

    it('should update the build plan on submit', () => {
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

        comp.onTextChanged('new text');

        expect(comp.buildPlan.buildPlan).toBe('new text');
    });

    it('should load the exercise on init', fakeAsync(() => {
        const exercise = {
            id: 3,
        } as ProgrammingExercise;

        activatedRoute.data = of({ exercise });
        const getExerciseWithSubmissionsStub = jest
            .spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults')
            .mockReturnValue(of(new HttpResponse<ProgrammingExercise>({ body: exercise })));

        comp.ngOnInit();
        tick();

        expect(getExerciseWithSubmissionsStub).toHaveBeenCalledWith(exercise.id);
    }));

    it('should load the build plan after the editor is initialized', fakeAsync(() => {
        const buildPlan: BuildPlan = {
            id: 2,
            buildPlan: 'some text',
        };

        activatedRoute.snapshot = {
            params: { exerciseId: 3 },
        } as unknown as ActivatedRouteSnapshot;
        const getBuildPlanStub = jest.spyOn(buildPlanService, 'getBuildPlan').mockReturnValue(of(new HttpResponse<BuildPlan>({ body: buildPlan })));

        comp.ngAfterViewInit();
        tick();

        expect(getBuildPlanStub).toHaveBeenCalledWith(3);
        expect(comp.isLoading).toBeFalse();
        expect(comp.buildPlan).toEqual(buildPlan);

        flush();
        discardPeriodicTasks();
    }));

    it.each([
        [404, 'artemisApp.programmingExercise.buildPlanFetchError'],
        [405, 'error.http.405'],
    ])(
        'should show an error message if fetching the build plan failed',
        fakeAsync((status: number, expectedError: string) => {
            activatedRoute.snapshot = {
                params: { exerciseId: 3 },
            } as unknown as ActivatedRouteSnapshot;
            const getBuildPlanStub = jest.spyOn(buildPlanService, 'getBuildPlan').mockReturnValue(throwError(() => new HttpResponse<BuildPlan>({ status })));

            const alertStub = jest.spyOn(alertService, 'error');

            comp.ngAfterViewInit();
            tick();

            expect(getBuildPlanStub).toHaveBeenCalledWith(3);
            expect(comp.isLoading).toBeFalse();
            expect(comp.buildPlan).toBeUndefined();

            expect(alertStub).toHaveBeenCalledOnce();
            expect(alertStub).toHaveBeenCalledWith(expectedError);

            flush();
            discardPeriodicTasks();
        }),
    );
});
