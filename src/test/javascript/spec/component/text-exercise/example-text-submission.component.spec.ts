import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute, ActivatedRouteSnapshot, convertToParamMap } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { Observable } from 'rxjs';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslatePipe } from '@ngx-translate/core';
import { MockComponent, MockPipe } from 'ng-mocks';

import { ExampleTextSubmissionComponent } from 'app/exercises/text/manage/example-text-submission/example-text-submission.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextAssessmentsService } from 'app/exercises/text/assess/text-assessments.service';
import { Result } from 'app/entities/result.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { State } from 'app/exercises/text/manage/example-text-submission/example-text-submission-state.model';
import { Feedback } from 'app/entities/feedback.model';
import { TextBlock } from 'app/entities/text-block.model';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import { TextAssessmentAreaComponent } from 'app/exercises/text/assess/text-assessment-area/text-assessment-area.component';
import { AssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ArtemisTestModule } from '../../test.module';

describe('ExampleTextSubmissionComponent', () => {
    let fixture: ComponentFixture<ExampleTextSubmissionComponent>;
    let comp: ExampleTextSubmissionComponent;
    let exerciseService: ExerciseService;
    let exampleSubmissionService: ExampleSubmissionService;
    let assessmentsService: TextAssessmentsService;

    const EXERCISE_ID = 1;
    const EXAMPLE_SUBMISSION_ID = 2;
    const SUBMISSION_ID = 3;
    let exercise: TextExercise;
    let exampleSubmission: ExampleSubmission;
    let result: Result;
    let submission: TextSubmission;
    let activatedRouteSnapshot: ActivatedRouteSnapshot;

    beforeEach(async () => {
        const route: ActivatedRoute = {
            snapshot: {
                paramMap: convertToParamMap({}),
                queryParamMap: convertToParamMap({}),
            },
        } as any;
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [
                ExampleTextSubmissionComponent,
                MockComponent(ResizeableContainerComponent),
                MockComponent(ScoreDisplayComponent),
                MockComponent(TextAssessmentAreaComponent),
                MockComponent(AssessmentInstructionsComponent),
                MockPipe(TranslatePipe),
                MockComponent(AlertComponent),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExampleTextSubmissionComponent);
        comp = fixture.componentInstance;
        activatedRouteSnapshot = fixture.debugElement.injector.get(ActivatedRoute).snapshot;
        exerciseService = fixture.debugElement.injector.get(ExerciseService);
        exampleSubmissionService = fixture.debugElement.injector.get(ExampleSubmissionService);
        assessmentsService = fixture.debugElement.injector.get(TextAssessmentsService);

        exercise = new TextExercise(undefined, undefined);
        exercise.id = EXERCISE_ID;
        exercise.title = 'Test case exercise';
        exampleSubmission = new ExampleSubmission();
        exampleSubmission.id = EXAMPLE_SUBMISSION_ID;
        result = new Result();
        submission = result.submission = exampleSubmission.submission = new TextSubmission();
        submission.id = SUBMISSION_ID;
    });

    it('should fetch example submission with result for existing example submission and switch to edit state', fakeAsync(() => {
        // GIVEN
        // @ts-ignore
        activatedRouteSnapshot.paramMap.params = { exerciseId: EXERCISE_ID, exampleSubmissionId: EXAMPLE_SUBMISSION_ID };
        spyOn(exerciseService, 'find').and.returnValue(httpResponse(exercise));
        spyOn(exampleSubmissionService, 'get').and.returnValue(httpResponse(exampleSubmission));
        spyOn(assessmentsService, 'getExampleResult').and.returnValue(httpResponse(result));

        // WHEN
        fixture.detectChanges();
        tick();

        // THEN
        expect(exerciseService.find).toHaveBeenCalledWith(EXERCISE_ID);
        expect(exampleSubmissionService.get).toHaveBeenCalledWith(EXAMPLE_SUBMISSION_ID);
        expect(assessmentsService.getExampleResult).toHaveBeenCalledWith(EXERCISE_ID, SUBMISSION_ID);
        expect(comp.state.constructor.name).toEqual('EditState');
    }));

    it('should fetch only fetch exercise for new example submission and stay in new state', fakeAsync(() => {
        // GIVEN
        // @ts-ignore
        activatedRouteSnapshot.paramMap.params = { exerciseId: EXERCISE_ID, exampleSubmissionId: 'new' };
        spyOn(exerciseService, 'find').and.returnValue(httpResponse(exercise));
        spyOn(exampleSubmissionService, 'get').and.stub();
        spyOn(assessmentsService, 'getExampleResult').and.stub();

        // WHEN
        fixture.detectChanges();
        tick();

        // THEN
        expect(exerciseService.find).toHaveBeenCalledWith(EXERCISE_ID);
        expect(exampleSubmissionService.get).toHaveBeenCalledTimes(0);
        expect(assessmentsService.getExampleResult).toHaveBeenCalledTimes(0);
        expect(comp.state.constructor.name).toEqual('NewState');
    }));

    it('should switch state when starting assessment', fakeAsync(() => {
        // GIVEN
        comp.isAtLeastInstructor = true;
        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;
        comp.submission = submission;
        spyOn(assessmentsService, 'getExampleResult').and.returnValues(httpResponse(result));

        // WHEN
        fixture.detectChanges();
        tick();
        fixture.debugElement.query(By.css('#createNewAssessment')).nativeElement.click();
        tick();

        // THEN
        expect(assessmentsService.getExampleResult).toHaveBeenCalledWith(EXERCISE_ID, SUBMISSION_ID);
        expect(comp.state.constructor.name).toEqual('NewAssessmentState');
    }));

    it('should save assessment', fakeAsync(() => {
        // GIVEN
        comp.isAtLeastInstructor = true;
        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;
        comp.submission = submission;
        const textBlock1 = new TextBlock();
        textBlock1.startIndex = 0;
        textBlock1.endIndex = 4;
        textBlock1.setTextFromSubmission(submission);
        textBlock1.computeId();
        const textBlock2 = new TextBlock();
        textBlock2.startIndex = 5;
        textBlock2.endIndex = 9;
        textBlock2.setTextFromSubmission(submission);
        textBlock2.computeId();
        submission.blocks = [textBlock1, textBlock2];
        comp.result = result;
        const feedback = Feedback.forText(textBlock1, 0, 'Test');
        result.feedbacks = [feedback];
        comp.state.edit();
        comp.state.assess();
        comp['prepareTextBlocksAndFeedbacks']();
        comp.validateFeedback();
        spyOn(assessmentsService, 'saveExampleAssessment').and.returnValue(httpResponse(result));

        // WHEN
        fixture.detectChanges();
        tick();
        fixture.debugElement.query(By.css('#saveNewAssessment')).nativeElement.click();
        tick();

        // THEN
        expect(assessmentsService.saveExampleAssessment).toHaveBeenCalledWith(EXAMPLE_SUBMISSION_ID, [feedback], [textBlock1]);
    }));

    it('editing submission from assessment state switches state', fakeAsync(() => {
        // GIVEN
        comp.isAtLeastInstructor = true;
        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;
        comp.submission = submission;
        const textBlock1 = new TextBlock();
        textBlock1.startIndex = 0;
        textBlock1.endIndex = 4;
        textBlock1.setTextFromSubmission(submission);
        textBlock1.computeId();
        const textBlock2 = new TextBlock();
        textBlock2.startIndex = 5;
        textBlock2.endIndex = 9;
        textBlock2.setTextFromSubmission(submission);
        textBlock2.computeId();
        submission.blocks = [textBlock1, textBlock2];
        comp.result = result;
        const feedback = Feedback.forText(textBlock1, 0, 'Test');
        result.feedbacks = [feedback];
        comp.state = State.forExistingAssessmentWithContext(comp);
        comp['prepareTextBlocksAndFeedbacks']();
        comp.validateFeedback();
        spyOn(assessmentsService, 'deleteExampleFeedback').and.returnValue(httpResponse(null));

        // WHEN
        fixture.detectChanges();
        tick();
        fixture.debugElement.query(By.css('#editSampleSolution')).nativeElement.click();
        tick();

        // THEN
        expect(comp.state.constructor.name).toEqual('EditState');
        expect(assessmentsService.deleteExampleFeedback).toHaveBeenCalledWith(EXAMPLE_SUBMISSION_ID);
        expect(comp.submission?.blocks).toBeUndefined();
        expect(comp.result?.feedbacks).toBeUndefined();
        expect(comp.textBlockRefs).toHaveLength(0);
        expect(comp.unusedTextBlockRefs).toHaveLength(0);
    }));

    const httpResponse = (body: any) => Observable.of(new HttpResponse({ body }));
});
