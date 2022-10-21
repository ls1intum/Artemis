import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { AssessmentLayoutComponent } from 'app/assessment/assessment-layout/assessment-layout.component';
import { AlertService } from 'app/core/util/alert.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Course } from 'app/entities/course.model';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { SubmissionExerciseType, SubmissionType, getLatestSubmissionResult } from 'app/entities/submission.model';
import { TextBlock } from 'app/entities/text-block.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { UnreferencedFeedbackComponent } from 'app/exercises/shared/unreferenced-feedback/unreferenced-feedback.component';
import { ManualTextblockSelectionComponent } from 'app/exercises/text/assess/manual-textblock-selection/manual-textblock-selection.component';
import { TextAssessmentAreaComponent } from 'app/exercises/text/assess/text-assessment-area/text-assessment-area.component';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { TextSubmissionAssessmentComponent } from 'app/exercises/text/assess/text-submission-assessment.component';
import { TextblockAssessmentCardComponent } from 'app/exercises/text/assess/textblock-assessment-card/textblock-assessment-card.component';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess/textblock-feedback-editor/textblock-feedback-editor.component';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { GradingInstructionLinkIconComponent } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import dayjs from 'dayjs/esm';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, throwError } from 'rxjs';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';

describe('TextSubmissionAssessmentComponent', () => {
    let component: TextSubmissionAssessmentComponent;
    let fixture: ComponentFixture<TextSubmissionAssessmentComponent>;
    let textAssessmentService: TextAssessmentService;
    let submissionService: SubmissionService;
    let exampleSubmissionService: ExampleSubmissionService;
    let router: Router;

    const exercise = {
        id: 1,
        type: ExerciseType.TEXT,
        assessmentType: AssessmentType.MANUAL,
        problemStatement: '',
        course: { id: 123, isAtLeastInstructor: true } as Course,
    } as TextExercise;
    const participation: StudentParticipation = {
        type: ParticipationType.STUDENT,
        id: 2,
        exercise,
    } as unknown as StudentParticipation;
    const submission = {
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
            hasFeedback: true,
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
            submission,
        } as TextBlock,
        {
            id: 'second text id',
            text: 'Second text.',
            startIndex: 12,
            endIndex: 24,
            submission,
        } as TextBlock,
    ];
    submission.participation!.submissions = [submission];
    submission.participation!.results = [getLatestSubmissionResult(submission)!];

    const route = (): ActivatedRoute =>
        ({
            paramMap: of(convertToParamMap({ courseId: 123, exerciseId: 1, examId: 2 })),
            queryParamMap: of(convertToParamMap({ testRun: 'false', correctionRound: 2 })),
            data: of({
                studentParticipation: participation,
            }),
        } as any as ActivatedRoute);

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule],
            declarations: [
                TextSubmissionAssessmentComponent,
                TextAssessmentAreaComponent,
                MockComponent(TextblockAssessmentCardComponent),
                MockComponent(TextblockFeedbackEditorComponent),
                MockComponent(ManualTextblockSelectionComponent),
                MockComponent(GradingInstructionLinkIconComponent),
                MockComponent(ConfirmIconComponent),
                MockComponent(AssessmentLayoutComponent),
                MockComponent(ScoreDisplayComponent),
                MockComponent(FaIconComponent),
                MockComponent(AssessmentInstructionsComponent),
                MockComponent(ResizeableContainerComponent),
                MockComponent(UnreferencedFeedbackComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(Router),
            ],
        })
            .overrideModule(ArtemisTestModule, {
                remove: {
                    declarations: [MockComponent(FaIconComponent)],
                    exports: [MockComponent(FaIconComponent)],
                },
            })
            .compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(TextSubmissionAssessmentComponent);
        component = fixture.componentInstance;
        submissionService = TestBed.inject(SubmissionService);
        exampleSubmissionService = TestBed.inject(ExampleSubmissionService);
        textAssessmentService = TestBed.inject(TextAssessmentService);
        router = TestBed.inject(Router);

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create and set parameters correctly', fakeAsync(() => {
        expect(component).not.toBeNull();
        component['route'] = route();
        component['activatedRoute'] = route();
        component.ngOnInit();
        tick();
        expect(component.isTestRun).toBeFalse();
        expect(component.exerciseId).toBe(1);
        expect(component.examId).toBe(2);
    }));

    it('should show jhi-text-assessment-area', () => {
        component['setPropertiesFromServerResponse'](participation);
        fixture.detectChanges();

        const textAssessmentArea = fixture.debugElement.query(By.directive(TextAssessmentAreaComponent));
        expect(textAssessmentArea).not.toBeNull();
    });

    it('should use jhi-assessment-layout', () => {
        const sharedLayout = fixture.debugElement.query(By.directive(AssessmentLayoutComponent));
        expect(sharedLayout).not.toBeNull();
    });

    it('should update score', () => {
        component['setPropertiesFromServerResponse'](participation);
        fixture.detectChanges();

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

        fixture.detectChanges();

        const result = getLatestSubmissionResult(submission);
        const textBlockRef = component.textBlockRefs[1];
        textBlockRef.initFeedback();
        textBlockRef.feedback!.detailText = 'my feedback';
        textBlockRef.feedback!.credits = 42;

        const saveStub = jest.spyOn(textAssessmentService, 'save');
        saveStub.mockReturnValue(of(new HttpResponse({ body: result })));

        component.validateFeedback();
        component.save();
        expect(saveStub).toHaveBeenCalledWith(
            result?.participation?.id,
            result!.id!,
            [component.textBlockRefs[0].feedback!, textBlockRef.feedback!],
            [component.textBlockRefs[0].block!, textBlockRef.block!],
        );
        expect(handleFeedbackStub).toHaveBeenCalledOnce();
    });

    it('should display error when saving but assessment invalid', () => {
        component.validateFeedback();
        const alertService = TestBed.inject(AlertService);
        const errorStub = jest.spyOn(alertService, 'error');

        fixture.detectChanges();
        component.save();
        expect(errorStub).toHaveBeenCalledWith('artemisApp.textAssessment.error.invalidAssessments');
    });

    it('should display error when submitting but assessment invalid', () => {
        component.validateFeedback();
        const alertService = TestBed.inject(AlertService);
        const errorStub = jest.spyOn(alertService, 'error');
        component.result = getLatestSubmissionResult(submission);

        component.submit();
        expect(errorStub).toHaveBeenCalledWith('artemisApp.textAssessment.error.invalidAssessments');
    });

    it('should display error when complaint resolved but assessment invalid', () => {
        // would be called on receive of event
        const complaintResponse = new ComplaintResponse();
        const alertService = TestBed.inject(AlertService);
        const errorStub = jest.spyOn(alertService, 'error');

        component.updateAssessmentAfterComplaint(complaintResponse);
        expect(errorStub).toHaveBeenCalledWith('artemisApp.textAssessment.error.invalidAssessments');
    });

    it('should send update when complaint resolved and assessments are valid', () => {
        const unreferencedFeedback = new Feedback();
        unreferencedFeedback.credits = 5;
        unreferencedFeedback.detailText = 'gj';
        unreferencedFeedback.type = FeedbackType.MANUAL_UNREFERENCED;
        unreferencedFeedback.id = 1;
        component.unreferencedFeedback = [unreferencedFeedback];

        const updateAssessmentAfterComplaintStub = jest.spyOn(textAssessmentService, 'updateAssessmentAfterComplaint');
        updateAssessmentAfterComplaintStub.mockReturnValue(of(new HttpResponse({ body: new Result() })));

        // would be called on receive of event
        const complaintResponse = new ComplaintResponse();
        component.updateAssessmentAfterComplaint(complaintResponse);
        expect(updateAssessmentAfterComplaintStub).toHaveBeenCalledOnce();
    });

    it('should submit the assessment with correct parameters', () => {
        component['setPropertiesFromServerResponse'](participation);
        fixture.detectChanges();

        const result = getLatestSubmissionResult(submission);
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
        );
    });

    it('should not submit if result was not saved', () => {
        const submitSpy = jest.spyOn(textAssessmentService, 'submit');
        component.submit();
        expect(submitSpy).not.toHaveBeenCalled();
    });

    it('should handle error if saving fails', () => {
        component['setPropertiesFromServerResponse'](participation);
        component.assessmentsAreValid = true;
        fixture.detectChanges();
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
        fixture.detectChanges();

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
        component['route'] = route();
        component['activatedRoute'] = route();

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

    it('should navigate to conflicting submission', () => {
        const routerSpy = jest.spyOn(router, 'navigate');
        component['setPropertiesFromServerResponse'](participation);
        fixture.detectChanges();
        const feedback = getLatestSubmissionResult(submission)!.feedbacks!;
        const url = [
            '/course-management',
            component.courseId,
            'text-exercises',
            component.exerciseId,
            'participations',
            submission.participation!.id,
            'submissions',
            component.submission!.id,
            'text-feedback-conflict',
            feedback[0].id,
        ];

        component.navigateToConflictingSubmissions(1);

        expect(routerSpy).toHaveBeenCalledOnce();
        expect(routerSpy).toHaveBeenCalledWith(url, { state: { submission } });
    });
});
