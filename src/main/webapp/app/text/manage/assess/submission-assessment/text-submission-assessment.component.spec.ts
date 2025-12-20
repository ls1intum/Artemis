import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { TextSubmissionAssessmentComponent } from 'app/text/manage/assess/submission-assessment/text-submission-assessment.component';
import { By } from '@angular/platform-browser';
import { of, throwError } from 'rxjs';
import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { TextAssessmentAreaComponent } from 'app/text/manage/assess/text-assessment-area/text-assessment-area.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TextBlockAssessmentCardComponent } from 'app/text/manage/assess/textblock-assessment-card/text-block-assessment-card.component';
import { TextBlockFeedbackEditorComponent } from 'app/text/manage/assess/textblock-feedback-editor/text-block-feedback-editor.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { SubmissionExerciseType, SubmissionType, getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import dayjs from 'dayjs/esm';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ManualTextblockSelectionComponent } from 'app/text/manage/assess/manual-textblock-selection/manual-textblock-selection.component';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { TextBlock, TextBlockType } from 'app/text/shared/entities/text-block.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { AlertService } from 'app/shared/service/alert.service';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { GradingInstructionLinkIconComponent } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import { AssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AssessmentAfterComplaint } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { TextAssessmentBaseComponent } from 'app/text/manage/assess/assessment-base/text-assessment-base.component';
import { AthenaService } from 'app/assessment/shared/services/athena.service';
import { MockAthenaService } from 'test/helpers/mocks/service/mock-athena-service';
import { TextBlockRef } from 'app/text/shared/entities/text-block-ref.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('TextSubmissionAssessmentComponent', () => {
    let component: TextSubmissionAssessmentComponent;
    let fixture: ComponentFixture<TextSubmissionAssessmentComponent>;
    let textAssessmentService: TextAssessmentService;
    let submissionService: SubmissionService;
    let exampleSubmissionService: ExampleSubmissionService;
    let athenaService: AthenaService;
    let router: Router;

    let exercise: TextExercise;
    let participation: StudentParticipation;
    let submission: TextSubmission;
    let mockActivatedRoute: ActivatedRoute;

    function createTextBlockRefWithFeedbackFromTo(startIndex: number, endIndex: number): TextBlockRef {
        const textBlock = new TextBlock();
        textBlock.startIndex = startIndex;
        textBlock.endIndex = endIndex;
        const feedback = new Feedback();
        feedback.type = FeedbackType.AUTOMATIC;
        feedback.detailText = 'detail';
        feedback.credits = 1;
        return new TextBlockRef(textBlock, feedback);
    }

    beforeEach(() => {
        exercise = {
            id: 1,
            type: ExerciseType.TEXT,
            assessmentType: AssessmentType.MANUAL,
            problemStatement: '',
            course: { id: 123, isAtLeastInstructor: true } as Course,
            maxPoints: 42,
        } as TextExercise;
        participation = {
            type: ParticipationType.STUDENT,
            id: 2,
            exercise,
        } as unknown as StudentParticipation;
        submission = {
            submissionExerciseType: SubmissionExerciseType.TEXT,
            id: 2278,
            submitted: true,
            type: SubmissionType.MANUAL,
            submissionDate: dayjs('2019-07-09T10:47:33.244Z'),
            text: 'First text. Second text.',
            participation,
        } as unknown as TextSubmission;
        submission.results = [
            {
                id: 2374,
                completionDate: dayjs('2019-07-09T11:51:23.251Z'),
                successful: false,
                score: 8,
                rated: true,
                hasComplaint: true,
                submission,
                participation,
            } as unknown as Result,
        ];

        getLatestSubmissionResult(submission)!.feedbacks = [
            {
                id: 1,
                detailText: 'First Feedback',
                credits: 1,
                reference: 'First text id',
            } as Feedback,
        ];
        submission.blocks = [
            {
                id: 'First text id',
                text: 'First text.',
                startIndex: 0,
                endIndex: 11,
                submissionId: submission.id,
            } as any as TextBlock,
            {
                id: 'second text id',
                text: 'Second text.',
                startIndex: 12,
                endIndex: 24,
                type: TextBlockType.MANUAL,
                submissionId: submission.id,
            } as any as TextBlock,
        ];
        submission.results = [getLatestSubmissionResult(submission)!];
        submission.participation!.submissions = [submission];

        mockActivatedRoute = {
            paramMap: of(convertToParamMap({ courseId: 123, exerciseId: 1, examId: 2, exerciseGroupId: 3 })),
            queryParamMap: of(convertToParamMap({ testRun: 'false', correctionRound: 2 })),
            data: of({
                studentParticipation: participation,
            }),
        } as unknown as ActivatedRoute;

        TestBed.configureTestingModule({
            imports: [FaIconComponent],
            declarations: [
                TextSubmissionAssessmentComponent,
                TextAssessmentAreaComponent,
                MockComponent(TextBlockAssessmentCardComponent),
                MockComponent(TextBlockFeedbackEditorComponent),
                MockComponent(ManualTextblockSelectionComponent),
                MockComponent(GradingInstructionLinkIconComponent),
                MockComponent(ConfirmIconComponent),
                MockComponent(AssessmentLayoutComponent),
                MockComponent(ScoreDisplayComponent),
                MockComponent(AssessmentInstructionsComponent),
                MockComponent(ResizeableContainerComponent),
                MockComponent(UnreferencedFeedbackComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
            ],
            providers: [
                provideRouter([]),
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AthenaService, useClass: MockAthenaService },
                MockProvider(Router),
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(TextSubmissionAssessmentComponent);
        component = fixture.componentInstance;
        submissionService = TestBed.inject(SubmissionService);
        exampleSubmissionService = TestBed.inject(ExampleSubmissionService);
        textAssessmentService = TestBed.inject(TextAssessmentService);
        athenaService = TestBed.inject(AthenaService);
        router = TestBed.inject(Router);

        fixture.changeDetectorRef.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create and set parameters correctly', async () => {
        expect(component).not.toBeNull();
        await component.ngOnInit();
        expect(component.isTestRun).toBeFalse();
        expect(component.exerciseId).toBe(1);
        expect(component.examId).toBe(2);
    });

    it('should show jhi-text-assessment-area', () => {
        component['setPropertiesFromServerResponse'](participation);
        fixture.changeDetectorRef.detectChanges();

        const textAssessmentArea = fixture.debugElement.query(By.directive(TextAssessmentAreaComponent));
        expect(textAssessmentArea).not.toBeNull();
    });

    it('should use jhi-assessment-layout', () => {
        const sharedLayout = fixture.debugElement.query(By.directive(AssessmentLayoutComponent));
        expect(sharedLayout).not.toBeNull();
    });

    it('should update score', () => {
        component['setPropertiesFromServerResponse'](participation);
        fixture.changeDetectorRef.detectChanges();

        const textAssessmentArea = fixture.debugElement.query(By.directive(TextAssessmentAreaComponent));
        const textAssessmentAreaComponent = textAssessmentArea.componentInstance as TextAssessmentAreaComponent;
        const textBlockRef = textAssessmentAreaComponent.textBlockRefs[0];
        textBlockRef.feedback!.credits = 42;
        textAssessmentAreaComponent.textBlockRefsChangeEmit();

        expect(component.totalScore).toBe(42);
    });

    it('should save the assessment with correct parameters', () => {
        component['setPropertiesFromServerResponse'](participation);
        const handleFeedbackStub = jest.spyOn(submissionService, 'handleFeedbackCorrectionRoundTag');

        fixture.changeDetectorRef.detectChanges();

        const result = getLatestSubmissionResult(submission);
        result!.assessmentNote = { id: 1, note: 'Note Text' };
        const textBlockRef = component.textBlockRefs[1];
        textBlockRef.initFeedback();
        textBlockRef.feedback!.detailText = 'my feedback';
        textBlockRef.feedback!.credits = 42;

        const saveStub = jest.spyOn(textAssessmentService, 'save');
        saveStub.mockReturnValue(of(new HttpResponse({ body: result })));

        component.validateFeedback();
        component.save();
        expect(saveStub).toHaveBeenCalledWith(
            result?.submission?.participation?.id,
            result!.id!,
            [component.textBlockRefs[0].feedback!, textBlockRef.feedback!],
            [component.textBlockRefs[0].block!, textBlockRef.block!],
            result?.assessmentNote!.note,
        );
        expect(handleFeedbackStub).toHaveBeenCalledOnce();
    });

    it('should display error when submitting but assessment invalid', async () => {
        const alertService = TestBed.inject(AlertService);
        const errorStub = jest.spyOn(alertService, 'error');

        component.assessmentsAreValid = false;
        component.submit();

        expect(errorStub).toHaveBeenCalledOnce();
        expect(errorStub).toHaveBeenCalledWith('artemisApp.textAssessment.error.invalidAssessments');
    });

    it('should display error when complaint resolved but assessment invalid', () => {
        // would be called on receive of event
        let onSuccessCalled = false;
        let onErrorCalled = false;
        const assessmentAfterComplaint: AssessmentAfterComplaint = {
            complaintResponse: new ComplaintResponse(),
            onSuccess: () => (onSuccessCalled = true),
            onError: () => (onErrorCalled = true),
        };

        const alertService = TestBed.inject(AlertService);
        const errorStub = jest.spyOn(alertService, 'error');

        // add an unreferenced feedback to make the assessment invalid
        component.unreferencedFeedback = [new Feedback()];

        component.updateAssessmentAfterComplaint(assessmentAfterComplaint);

        expect(errorStub).toHaveBeenCalledOnce();
        expect(errorStub).toHaveBeenCalledWith('artemisApp.textAssessment.error.invalidAssessments');
        expect(onSuccessCalled).toBeFalse();
        expect(onErrorCalled).toBeTrue();
    });

    it.each([true, false])('should send update when complaint resolved and assessments are valid, serverReturnsError=%s', (serverReturnsError: boolean) => {
        const unreferencedFeedback = new Feedback();
        unreferencedFeedback.credits = 5;
        unreferencedFeedback.detailText = 'gj';
        unreferencedFeedback.type = FeedbackType.MANUAL_UNREFERENCED;
        unreferencedFeedback.id = 1;
        component.unreferencedFeedback = [unreferencedFeedback];

        const updateAssessmentAfterComplaintStub = jest.spyOn(textAssessmentService, 'updateAssessmentAfterComplaint');
        const serverResponse = serverReturnsError ? throwError(() => new HttpErrorResponse({ status: 400 })) : of(new HttpResponse({ body: new Result() }));
        updateAssessmentAfterComplaintStub.mockReturnValue(serverResponse);

        // would be called on receive of event
        let onSuccessCalled = false;
        let onErrorCalled = false;
        const assessmentAfterComplaint: AssessmentAfterComplaint = {
            complaintResponse: new ComplaintResponse(),
            onSuccess: () => (onSuccessCalled = true),
            onError: () => (onErrorCalled = true),
        };

        component.updateAssessmentAfterComplaint(assessmentAfterComplaint);
        expect(updateAssessmentAfterComplaintStub).toHaveBeenCalledOnce();
        expect(onSuccessCalled).toBe(!serverReturnsError);
        expect(onErrorCalled).toBe(serverReturnsError);
    });

    it('should submit the assessment with correct parameters', () => {
        component['setPropertiesFromServerResponse'](participation);
        fixture.changeDetectorRef.detectChanges();

        const result = getLatestSubmissionResult(submission);
        result!.assessmentNote = { id: 1, note: 'Note Text' };
        const textBlockRef = component.textBlockRefs[1];
        textBlockRef.initFeedback();
        textBlockRef.feedback!.detailText = 'my feedback';
        textBlockRef.feedback!.credits = 42;

        const submitStub = jest.spyOn(textAssessmentService, 'submit');
        submitStub.mockReturnValue(of(new HttpResponse({ body: result })));

        component.validateFeedback();
        component.submit();
        expect(submitStub).toHaveBeenCalledWith(
            participation.id!,
            result!.id!,
            [component.textBlockRefs[0].feedback!, textBlockRef.feedback!],
            [component.textBlockRefs[0].block!, textBlockRef.block!],
            result?.assessmentNote.note,
        );
    });

    it('should not submit if result was not saved', () => {
        const submitSpy = jest.spyOn(textAssessmentService, 'submit');
        component.result!.id = undefined;
        component.submit();
        expect(submitSpy).not.toHaveBeenCalled();
    });

    it('should handle error if saving fails', () => {
        component['setPropertiesFromServerResponse'](participation);
        component.assessmentsAreValid = true;
        fixture.changeDetectorRef.detectChanges();
        const error = new HttpErrorResponse({ status: 404 });
        const errorStub = jest.spyOn(textAssessmentService, 'save').mockReturnValue(throwError(() => error));

        component.save();

        expect(errorStub).toHaveBeenCalledOnce();
        expect(component.saveBusy).toBeFalse();
    });

    it('should invoke import example submission', () => {
        component.submission = submission;
        component.exercise = exercise;

        const importStub = jest.spyOn(exampleSubmissionService, 'import');
        importStub.mockReturnValue(of(new HttpResponse({ body: new ExampleSubmission() })));

        component.useStudentSubmissionAsExampleSubmission();

        expect(importStub).toHaveBeenCalledOnce();
        expect(importStub).toHaveBeenCalledWith(submission.id, exercise.id);
    });

    it('should cancel assessment', () => {
        component['setPropertiesFromServerResponse'](participation);
        fixture.changeDetectorRef.detectChanges();

        const navigateBackSpy = jest.spyOn(component, 'navigateBack');
        const cancelAssessmentStub = jest.spyOn(textAssessmentService, 'cancelAssessment').mockReturnValue(of(undefined));
        const windowConfirmStub = jest.spyOn(window, 'confirm').mockReturnValue(true);

        component.cancel();

        expect(windowConfirmStub).toHaveBeenCalledOnce();
        expect(navigateBackSpy).toHaveBeenCalledOnce();
        expect(cancelAssessmentStub).toHaveBeenCalledOnce();
        expect(cancelAssessmentStub).toHaveBeenCalledWith(participation?.id, submission.id);
    });

    it('should go to next submission', fakeAsync(() => {
        component['setPropertiesFromServerResponse'](participation);
        const routerSpy = jest.spyOn(router, 'navigate');

        component.ngOnInit();
        tick();

        const url = [
            '/course-management',
            component.courseId.toString(),
            'exams',
            component.examId.toString(),
            'exercise-groups',
            component.exerciseGroupId.toString(),
            'text-exercises',
            exercise.id!.toString(),
            'submissions',
            'new',
            'assessment',
        ];
        const queryParams = { queryParams: { 'correction-round': 0 } };

        component.nextSubmission();
        expect(routerSpy).toHaveBeenCalledOnce();
        expect(routerSpy).toHaveBeenCalledWith(url, queryParams);
    }));

    it('should always let instructors override', () => {
        component.exercise!.isAtLeastInstructor = true;
        expect(component.canOverride).toBeTrue();
    });

    it('should not allow tutors to override after the assessment due date', () => {
        component.exercise!.isAtLeastInstructor = false;
        component.exercise!.assessmentDueDate = dayjs().subtract(1, 'day');
        component.complaint = undefined;
        expect(component.canOverride).toBeFalse();
    });

    it('should recalculate text block refs correctly', () => {
        jest.useFakeTimers();
        component.recalculateTextBlockRefs();
        fixture.changeDetectorRef.detectChanges();
        jest.advanceTimersByTime(300);

        expect(component.textBlockRefs).toHaveLength(2);
        expect(component.unusedTextBlockRefs).toHaveLength(0);
    });

    it('should handle overlapping manual text blocks correctly', () => {
        const sortAndSetTextBlockRefsSpy = jest.spyOn(TextAssessmentBaseComponent.prototype as any, 'sortAndSetTextBlockRefs');

        // BEGIN: Adding a new block (with feedback) that overlaps with an existing block
        submission.blocks?.push({
            id: 'third text id',
            text: 'text.',
            startIndex: 19,
            endIndex: 24,
            type: TextBlockType.MANUAL,
            submissionId: submission.id,
        } as any as TextBlock);

        getLatestSubmissionResult(submission)?.feedbacks?.push({
            id: 3,
            detailText: 'Third Feedback',
            credits: 0,
            reference: 'third text id',
        } as Feedback);
        // END: Adding a new block (with feedback) that overlaps with an existing block

        component['setPropertiesFromServerResponse'](participation);
        fixture.changeDetectorRef.detectChanges();

        expect(sortAndSetTextBlockRefsSpy).toHaveBeenCalled();

        expect(component.textBlockRefs).toEqual(
            // Checking if sortAndSetTextBlockRefs selected the right TextBlockRef (the one having a feedback)
            // Performing partial match for { block: { text: ...}, feedback: { id: ... } }
            expect.arrayContaining([
                expect.objectContaining({
                    block: expect.objectContaining({ text: 'text.' }),
                    feedback: expect.objectContaining({ id: 3 }),
                }),
            ]),
        );

        // Checking if a new block was added to compensate for the loss of submitted text due to the overlap between blocks
        // Performing partial match for { block: { text: ...} }
        expect(component.textBlockRefs).toEqual(expect.arrayContaining([expect.objectContaining({ block: expect.objectContaining({ text: 'Second ' }) })]));
    });

    it('should load feedback suggestions', fakeAsync(() => {
        // preparation already added an assessment, but we need to remove it to test the loading
        component.textBlockRefs = [];
        component.unreferencedFeedback = [];
        const feedbackSuggestionTextBlockRef = createTextBlockRefWithFeedbackFromTo(0, 10);
        feedbackSuggestionTextBlockRef.feedback!.text = "I'm a feedback suggestion";
        const athenaServiceFeedbackSuggestionsStub = jest.spyOn(athenaService, 'getTextFeedbackSuggestions').mockReturnValue(of([feedbackSuggestionTextBlockRef]));
        component.loadFeedbackSuggestions();
        tick();
        expect(athenaServiceFeedbackSuggestionsStub).toHaveBeenCalled();
        expect(component.textBlockRefs[0].feedback?.text).toEqual(feedbackSuggestionTextBlockRef.feedback!.text);
    }));

    it.each([
        // No existing blocks
        { input: [], output: [] },
        // Only one block, no possibility for overlap
        { input: [[0, 10]], output: [[0, 10]] },
        // Two blocks, no overlap
        {
            input: [
                [0, 10],
                [10, 20],
            ],
            output: [
                [0, 10],
                [10, 20],
            ],
        },
        // Two blocks, no overlap
        {
            input: [
                [0, 10],
                [11, 20],
            ],
            output: [
                [0, 10],
                [11, 20],
            ],
        },
        // Two blocks, overlap
        {
            input: [
                [0, 10],
                [5, 15],
            ],
            output: [
                [0, 5],
                [5, 15],
            ],
        },
        // Two blocks, full overlap
        {
            input: [
                [0, 10],
                [5, 7],
            ],
            output: [
                [0, 5],
                [5, 7],
                [7, 10],
            ],
        },
        // Two blocks, wrong order
        {
            input: [
                [10, 20],
                [0, 5],
            ],
            output: [
                [0, 5],
                [10, 20],
            ],
        },
        // Two blocks, same start index
        {
            input: [
                [5, 15],
                [5, 10],
            ],
            output: [
                [5, 10],
                [10, 15],
            ],
        },
        // Two blocks, same end index
        {
            input: [
                [377, 719],
                [453, 719],
            ],
            output: [
                [377, 453],
                [453, 719],
            ],
        },
        // Two blocks, same end index, wrong order
        {
            input: [
                [453, 719],
                [377, 719],
            ],
            output: [
                [377, 453],
                [453, 719],
            ],
        },
        // Two blocks, shifted
        {
            input: [
                [6, 11],
                [5, 10],
            ],
            output: [
                [5, 10],
                [10, 11],
            ],
        },
        // Three blocks, overlap
        {
            input: [
                [0, 10],
                [5, 15],
                [15, 20],
            ],
            output: [
                [0, 5],
                [5, 15],
                [15, 20],
            ],
        },
        // Two blocks with exact overlap
        {
            input: [
                [3, 10],
                [3, 10],
            ],
            output: [[3, 10]],
        },
    ])('should never create overlapping blocks even with overlapping feedback suggestions', ({ input, output }: { input: number[][]; output: number[][] }) => {
        // preparation already added an assessment, but we need to remove it to test the loading
        component.textBlockRefs = [];
        component.unreferencedFeedback = [];

        // Set up initial state with an existing text block that doesn't overlap
        const feedbackSuggestions = input.map(([start, end]) => createTextBlockRefWithFeedbackFromTo(start, end));

        jest.spyOn(athenaService, 'getTextFeedbackSuggestions').mockReturnValue(of(feedbackSuggestions));

        component.loadFeedbackSuggestions();

        // No block should overlap with any other block
        const blocks = component.textBlockRefs.map((ref) => ref.block);
        let lastEndIndex = 0;
        for (const block of blocks) {
            expect(block!.startIndex).toBeGreaterThanOrEqual(lastEndIndex);
            lastEndIndex = block!.endIndex!;
        }

        // All blocks should be in the output
        expect(blocks).toHaveLength(output.length);
        for (const [index, block] of blocks.entries()) {
            expect(block!.startIndex).toEqual(output[index][0]);
            expect(block!.endIndex).toEqual(output[index][1]);
        }
    });

    it('should not load feedback suggestions if there already are assessments', fakeAsync(() => {
        // preparation already added an assessment
        const athenaServiceFeedbackSuggestionsSpy = jest.spyOn(athenaService, 'getTextFeedbackSuggestions');
        component.loadFeedbackSuggestions();
        tick();
        expect(athenaServiceFeedbackSuggestionsSpy).not.toHaveBeenCalled();
    }));

    it('should validate assessments on component init', async () => {
        component.assessmentsAreValid = false;
        await component.ngOnInit();
        expect(component.assessmentsAreValid).toBeTrue();
    });

    it('should allow overriding directly after submitting', async () => {
        component.isAssessor = true;
        component.submit();
        expect(component.canOverride).toBeTrue();
    });

    it('should not invalidate assessment after saving', async () => {
        component.save();
        expect(component.assessmentsAreValid).toBeTrue();
    });
});
