import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, ActivatedRouteSnapshot, convertToParamMap, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { Feedback, FeedbackCorrectionErrorType } from 'app/entities/feedback.model';
import { Result } from 'app/entities/result.model';
import { TextBlock } from 'app/entities/text-block.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TutorParticipationService } from 'app/exercises/shared/dashboards/tutor/tutor-participation.service';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { TextAssessmentAreaComponent } from 'app/exercises/text/assess/text-assessment-area/text-assessment-area.component';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { State } from 'app/exercises/text/manage/example-text-submission/example-text-submission-state.model';
import { ExampleTextSubmissionComponent } from 'app/exercises/text/manage/example-text-submission/example-text-submission.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, throwError } from 'rxjs';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTestModule } from '../../test.module';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { UnreferencedFeedbackComponent } from 'app/exercises/shared/unreferenced-feedback/unreferenced-feedback.component';
import { AlertService } from 'app/core/util/alert.service';
import { DebugElement } from '@angular/core';

describe('ExampleTextSubmissionComponent', () => {
    let fixture: ComponentFixture<ExampleTextSubmissionComponent>;
    let debugElement: DebugElement;
    let comp: ExampleTextSubmissionComponent;
    let exerciseService: ExerciseService;
    let exampleSubmissionService: ExampleSubmissionService;
    let assessmentsService: TextAssessmentService;
    let alertService: AlertService;

    const EXERCISE_ID = 1;
    const EXAMPLE_SUBMISSION_ID = 2;
    const SUBMISSION_ID = 3;
    let exercise: TextExercise;
    let exampleSubmission: ExampleSubmission;
    let result: Result;
    let submission: TextSubmission;
    let activatedRouteSnapshot: ActivatedRouteSnapshot;

    beforeEach(() => {
        const route: ActivatedRoute = {
            snapshot: {
                paramMap: convertToParamMap({ exerciseId: EXERCISE_ID }),
                queryParamMap: convertToParamMap({}),
            },
        } as any;
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [
                ExampleTextSubmissionComponent,
                MockComponent(ResizeableContainerComponent),
                MockComponent(ScoreDisplayComponent),
                MockComponent(TextAssessmentAreaComponent),
                MockComponent(AssessmentInstructionsComponent),
                MockComponent(UnreferencedFeedbackComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                MockProvider(TranslateService),
                MockProvider(AlertService),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExampleTextSubmissionComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        activatedRouteSnapshot = debugElement.injector.get(ActivatedRoute).snapshot;
        exerciseService = debugElement.injector.get(ExerciseService);
        exampleSubmissionService = debugElement.injector.get(ExampleSubmissionService);
        assessmentsService = debugElement.injector.get(TextAssessmentService);
        alertService = debugElement.injector.get(AlertService);
        exercise = new TextExercise(undefined, undefined);
        exercise.id = EXERCISE_ID;
        exercise.title = 'Test case exercise';
        exampleSubmission = new ExampleSubmission();
        exampleSubmission.id = EXAMPLE_SUBMISSION_ID;
        result = new Result();
        submission = result.submission = exampleSubmission.submission = new TextSubmission();
        submission.id = SUBMISSION_ID;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should fetch example submission with result for existing example submission and switch to edit state', fakeAsync(() => {
        // GIVEN
        // @ts-ignore
        activatedRouteSnapshot.paramMap.params = { exerciseId: EXERCISE_ID, exampleSubmissionId: EXAMPLE_SUBMISSION_ID };
        jest.spyOn(exerciseService, 'find').mockReturnValue(httpResponse(exercise));
        jest.spyOn(exampleSubmissionService, 'get').mockReturnValue(httpResponse(exampleSubmission));
        jest.spyOn(assessmentsService, 'getExampleResult').mockReturnValue(of(result));

        // WHEN
        comp.ngOnInit();
        tick();

        // THEN
        expect(exerciseService.find).toHaveBeenCalledWith(EXERCISE_ID);
        expect(exampleSubmissionService.get).toHaveBeenCalledWith(EXAMPLE_SUBMISSION_ID);
        expect(assessmentsService.getExampleResult).toHaveBeenCalledWith(EXERCISE_ID, SUBMISSION_ID);
        expect(comp.state.constructor.name).toBe('EditState');
    }));

    it('should not fail while fetching submission with null result for existing example submission in tutorial submission mode', fakeAsync(() => {
        // GIVEN
        // @ts-ignore
        activatedRouteSnapshot.paramMap.params = { toComplete: 'true' };
        jest.spyOn(exampleSubmissionService, 'get').mockReturnValue(httpResponse(exampleSubmission));
        // @ts-ignore
        jest.spyOn(assessmentsService, 'getExampleResult').mockReturnValue(of(null));

        // WHEN
        comp.ngOnInit();
        tick();

        // THEN
        expect(comp.result).not.toBeNull();
        expect(comp.result!.submission).toBe(comp.submission);
    }));

    it('should only fetch exercise for new example submission and stay in new state', fakeAsync(() => {
        // GIVEN
        // @ts-ignore
        activatedRouteSnapshot.paramMap.params = { exerciseId: EXERCISE_ID, exampleSubmissionId: 'new' };
        jest.spyOn(exerciseService, 'find').mockReturnValue(httpResponse(exercise));
        jest.spyOn(exampleSubmissionService, 'get').mockImplementation();
        jest.spyOn(assessmentsService, 'getExampleResult').mockImplementation();

        // WHEN
        comp.ngOnInit();
        tick();

        // THEN
        expect(exerciseService.find).toHaveBeenCalledWith(EXERCISE_ID);
        expect(exampleSubmissionService.get).not.toHaveBeenCalled();
        expect(assessmentsService.getExampleResult).not.toHaveBeenCalled();
        expect(comp.state.constructor.name).toBe('NewState');
    }));

    it('should switch state when starting assessment', fakeAsync(() => {
        // GIVEN
        // @ts-ignore
        activatedRouteSnapshot.paramMap.params = { exerciseId: EXERCISE_ID, exampleSubmissionId: EXAMPLE_SUBMISSION_ID };
        comp.ngOnInit();
        tick();
        comp.exercise = exercise;
        comp.exercise!.isAtLeastInstructor = true;
        comp.exampleSubmission = exampleSubmission;
        comp.submission = submission;

        // WHEN
        comp.startAssessment();
        tick();

        // THEN
        expect(comp.state.constructor.name).toBe('NewAssessmentState');
    }));

    it('should save assessment', fakeAsync(() => {
        // GIVEN
        // @ts-ignore
        activatedRouteSnapshot.paramMap.params = { exerciseId: EXERCISE_ID, exampleSubmissionId: EXAMPLE_SUBMISSION_ID };
        comp.ngOnInit();
        tick();
        comp.exercise = exercise;
        comp.exercise!.isAtLeastEditor = true;
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
        submission.text = '123456789';
        comp.result = result;
        const feedback = Feedback.forText(textBlock1, 0, 'Test');
        result.feedbacks = [feedback];
        comp.state.edit();
        comp.state.assess();
        comp['prepareTextBlocksAndFeedbacks']();
        comp.validateFeedback();
        jest.spyOn(assessmentsService, 'saveExampleAssessment').mockReturnValue(httpResponse(result));

        // WHEN
        fixture.detectChanges();
        debugElement.query(By.css('#saveNewAssessment')).nativeElement.click();

        // THEN
        expect(assessmentsService.saveExampleAssessment).toHaveBeenCalledWith(EXERCISE_ID, EXAMPLE_SUBMISSION_ID, [feedback], [textBlock1]);
    }));

    it('should not save the assessment when it is invalid', () => {
        const alertErrorSpy = jest.spyOn(alertService, 'error');
        comp.saveAssessments();

        expect(alertErrorSpy).toHaveBeenCalledOnce();
    });

    it('editing submission from assessment state switches state', fakeAsync(() => {
        // GIVEN
        comp.exercise = exercise;
        comp.exercise!.isAtLeastEditor = true;
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
        submission.text = '123456789';
        comp.result = result;
        const feedback = Feedback.forText(textBlock1, 0, 'Test');
        result.feedbacks = [feedback];
        comp.state = State.forExistingAssessmentWithContext(comp);
        comp['prepareTextBlocksAndFeedbacks']();
        comp.validateFeedback();
        jest.spyOn(assessmentsService, 'deleteExampleAssessment').mockReturnValue(of(undefined));

        // WHEN
        fixture.detectChanges();
        tick();
        debugElement.query(By.css('#editSampleSolution')).nativeElement.click();
        tick();

        // THEN
        expect(comp.state.constructor.name).toBe('EditState');
        expect(assessmentsService.deleteExampleAssessment).toHaveBeenCalledWith(EXERCISE_ID, EXAMPLE_SUBMISSION_ID);
        expect(comp.submission?.blocks).toBeUndefined();
        expect(comp.submission?.results).toBeUndefined();
        expect(comp.submission?.latestResult).toBeUndefined();
        expect(comp.result).toBeUndefined();
        expect(comp.textBlockRefs).toHaveLength(0);
        expect(comp.unusedTextBlockRefs).toHaveLength(0);
    }));

    it('should verify correct tutorial submission', fakeAsync(() => {
        // GIVEN
        // @ts-ignore
        activatedRouteSnapshot.paramMap.params = { exerciseId: EXERCISE_ID, exampleSubmissionId: EXAMPLE_SUBMISSION_ID };
        // @ts-ignore
        activatedRouteSnapshot.queryParamMap.params = { toComplete: true };
        jest.spyOn(exerciseService, 'find').mockReturnValue(httpResponse(exercise));
        jest.spyOn(exampleSubmissionService, 'get').mockReturnValue(httpResponse(exampleSubmission));
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
        submission.text = '123456789';
        jest.spyOn(assessmentsService, 'getExampleResult').mockReturnValue(of(result));
        comp.ngOnInit();
        tick();

        comp.textBlockRefs[0].initFeedback();
        comp.textBlockRefs[0].feedback!.credits = 2;
        comp.validateFeedback();
        const tutorParticipationService = debugElement.injector.get(TutorParticipationService);
        jest.spyOn(tutorParticipationService, 'assessExampleSubmission').mockReturnValue(httpResponse(null));

        // WHEN
        fixture.detectChanges();
        debugElement.query(By.css('#checkAssessment')).nativeElement.click();

        // THEN
        expect(exerciseService.find).toHaveBeenCalledWith(EXERCISE_ID);
        expect(exampleSubmissionService.get).toHaveBeenCalledWith(EXAMPLE_SUBMISSION_ID);
        expect(assessmentsService.getExampleResult).toHaveBeenCalledWith(EXERCISE_ID, SUBMISSION_ID);
        expect(tutorParticipationService.assessExampleSubmission).toHaveBeenCalledOnce();
    }));

    it('should not check the assessment when it is invalid', () => {
        const alertErrorSpy = jest.spyOn(alertService, 'error');
        comp.checkAssessment();

        expect(alertErrorSpy).toHaveBeenCalledOnce();
    });

    it('when wrong tutor assessment, upon server response should mark feedback as incorrect', fakeAsync(() => {
        // GIVEN
        const textBlockRefA = TextBlockRef.new();
        textBlockRefA.block!.id = 'ID';
        const feedbackA = new Feedback();
        feedbackA.reference = textBlockRefA.block!.id;
        feedbackA.detailText = 'feedbackA';
        textBlockRefA.feedback = feedbackA;

        const textBlockRefB = TextBlockRef.new();
        const feedbackB = new Feedback();
        feedbackB.detailText = 'feebbackB';
        textBlockRefB.feedback = feedbackB;

        comp.textBlockRefs = [textBlockRefA, textBlockRefB];

        expect(feedbackA.correctionStatus).toBeUndefined();
        expect(feedbackB.correctionStatus).toBeUndefined();

        const tutorParticipationService = debugElement.injector.get(TutorParticipationService);
        const feedbackError = {
            reference: feedbackA.reference,
            type: FeedbackCorrectionErrorType.INCORRECT_SCORE,
        };
        const errorResponse = new HttpErrorResponse({
            error: { title: JSON.stringify({ errors: [feedbackError] }) },
            headers: new HttpHeaders().append('x-artemisapp-error', 'error.invalid_assessment'),
            status: 400,
        });

        jest.spyOn(tutorParticipationService, 'assessExampleSubmission').mockReturnValue(throwError(() => errorResponse));

        // WHEN
        comp.ngOnInit();
        tick();

        comp.checkAssessment();
        tick();

        // THEN
        expect(feedbackA.correctionStatus).toBe(FeedbackCorrectionErrorType.INCORRECT_SCORE);
        expect(feedbackB.correctionStatus).toBe('CORRECT');
    }));

    it('should create new example submission', fakeAsync(() => {
        comp.submission = submission;
        comp.exercise = exercise;
        const createStub = jest.spyOn(exampleSubmissionService, 'create').mockReturnValue(httpResponse(exampleSubmission));
        const alertSuccessSpy = jest.spyOn(alertService, 'success');

        comp.createNewExampleTextSubmission();
        tick();

        expect(createStub).toHaveBeenCalledOnce();
        expect(alertSuccessSpy).toHaveBeenCalledOnce();
    }));

    it('should not create example submission', fakeAsync(() => {
        comp.submission = submission;
        comp.exercise = exercise;
        const createStub = jest.spyOn(exampleSubmissionService, 'create').mockReturnValue(throwError(() => ({ status: 404 })));
        const alertErrorSpy = jest.spyOn(alertService, 'error');

        comp.createNewExampleTextSubmission();
        tick();

        expect(createStub).toHaveBeenCalledOnce();
        expect(alertErrorSpy).toHaveBeenCalledOnce();
    }));

    it('should read and understood', () => {
        // GIVEN
        const tutorParticipationService = debugElement.injector.get(TutorParticipationService);
        jest.spyOn(tutorParticipationService, 'assessExampleSubmission').mockReturnValue(of(new HttpResponse({ body: {} })));
        const alertSpy = jest.spyOn(alertService, 'success');

        const router = debugElement.injector.get(Router);
        const routerSpy = jest.spyOn(router, 'navigate');
        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;

        // WHEN
        fixture.detectChanges();
        comp.readAndUnderstood();

        // THEN
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.exampleSubmission.readSuccessfully');
        expect(routerSpy).toHaveBeenCalledOnce();
    });

    it('should go back with exam', fakeAsync(() => {
        // GIVEN
        const router = debugElement.injector.get(Router);
        const routerSpy = jest.spyOn(router, 'navigate');
        const examExercise = {
            id: EXERCISE_ID,
            exerciseGroup: {
                id: 20,
                exam: {
                    id: 30,
                    course: { id: 40 },
                },
            },
        } as TextExercise;

        comp.exercise = examExercise;

        // WHEN
        fixture.detectChanges();
        tick();

        comp.toComplete = true;

        comp.back();
        tick();

        comp.toComplete = false;

        comp.back();
        tick();

        // THEN
        expect(routerSpy).toHaveBeenCalledTimes(2);
        expect(routerSpy).toHaveBeenNthCalledWith(1, ['/course-management', examExercise.exerciseGroup?.exam?.course?.id, 'assessment-dashboard', examExercise.id]);

        expect(routerSpy).toHaveBeenNthCalledWith(2, [
            '/course-management',
            examExercise.exerciseGroup?.exam?.course?.id,
            'exams',
            examExercise.exerciseGroup?.exam?.id,
            'exercise-groups',
            examExercise.exerciseGroup?.id,
            'text-exercises',
            examExercise.id,
            'example-submissions',
        ]);
    }));

    it('should update example text submission', () => {
        // GIVEN
        const alertSuccessSpy = jest.spyOn(alertService, 'success');
        const exampleSubmissionServiceSpy = jest.spyOn(exampleSubmissionService, 'update');
        exampleSubmissionServiceSpy.mockReturnValue(httpResponse(exampleSubmission));
        comp.unsavedSubmissionChanges = true;

        // WHEN
        comp.updateExampleTextSubmission();

        // THEN
        expect(exampleSubmissionServiceSpy).toHaveBeenCalledOnce();
        expect(comp.exampleSubmission).toEqual(exampleSubmission);
        expect(comp.unsavedSubmissionChanges).toBeFalse();
        expect(alertSuccessSpy).toHaveBeenCalledOnce();
        expect(alertSuccessSpy).toHaveBeenCalledWith('artemisApp.exampleSubmission.saveSuccessful');
    });

    const httpResponse = (body: any) => of(new HttpResponse({ body }));
});
