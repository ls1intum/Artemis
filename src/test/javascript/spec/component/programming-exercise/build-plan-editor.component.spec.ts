import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { BuildPlanEditorComponent } from 'app/exercises/programming/manage/build-plan-editor.component';
import { BuildPlanService } from 'app/exercises/programming/manage/services/build-plan.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { BuildPlan } from 'app/entities/build-plan.model';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { MockBuildPlanService } from '../../helpers/mocks/service/mock-build-plan.service';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { CodeEditorHeaderComponent } from 'app/exercises/programming/shared/code-editor/header/code-editor-header.component';
import { MockComponent } from 'ng-mocks';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { NgbTooltipMocksModule } from '../../helpers/mocks/directive/ngbTooltipMocks.module';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';

describe('Build Plan Editor', () => {
    let fixture: ComponentFixture<BuildPlanEditorComponent>;
    let comp: BuildPlanEditorComponent;

    let buildPlanService: BuildPlanService;
    let putBuildPlanStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, AceEditorModule, NgbTooltipMocksModule],
            declarations: [
                BuildPlanEditorComponent,
                TranslatePipeMock,
                MockComponent(AceEditorComponent),
                MockComponent(CodeEditorHeaderComponent),
                MockComponent(UpdatingResultComponent),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: BuildPlanService, useValue: new MockBuildPlanService() },
                { provide: ProgrammingExerciseService, useValue: new MockProgrammingExerciseService() },
            ],
        })
            .compileComponents()
            .then(() => {
                console.log('pre-init');
                fixture = TestBed.createComponent(BuildPlanEditorComponent);
                console.log('post-init');
                comp = fixture.componentInstance;

                buildPlanService = fixture.debugElement.injector.get(BuildPlanService);
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

        expect(putBuildPlanStub).toHaveBeenCalledOnceWith(3, originalBuildPlan);
        expect(comp.buildPlan).toEqual(buildPlan);
    });

    it('should update the build plan text on editor text changes', () => {
        comp.buildPlan = {
            buildPlan: 'empty text',
        } as BuildPlan;

        comp.onTextChanged('new text');

        expect(comp.buildPlan.buildPlan).toBe('new text');
    });

    it('should load the build plan that was previously saved on init', () => {
        const originalBuildPlan = {
            id: 1,
            buildPlan: 'build plan text',
        } as BuildPlan;
        const buildPlan = {
            id: 2,
            buildPlan: 'new text',
        } as BuildPlan;

        putBuildPlanStub = putBuildPlanStub.mockReturnValue(of(new HttpResponse<BuildPlan>({ body: buildPlan })));

        comp.exerciseId = 3;
        comp.buildPlan = originalBuildPlan;

        comp.submit();
        comp.ngOnInit();

        expect(comp.buildPlan.buildPlan).toBe('new text');
    });
});
