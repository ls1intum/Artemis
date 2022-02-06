import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TextSubmissionAssessmentComponent } from 'app/exercises/text/assess/text-submission-assessment.component';
import { ArtemisTestModule } from '../../test.module';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';
import { AssessmentLayoutComponent } from 'app/assessment/assessment-layout/assessment-layout.component';
import { TextAssessmentAreaComponent } from 'app/exercises/text/assess/text-assessment-area/text-assessment-area.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TextblockAssessmentCardComponent } from 'app/exercises/text/assess/textblock-assessment-card/textblock-assessment-card.component';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess/textblock-feedback-editor/textblock-feedback-editor.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { getLatestSubmissionResult, SubmissionExerciseType, SubmissionType } from 'app/entities/submission.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs/esm';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { Course } from 'app/entities/course.model';
import { ManualTextblockSelectionComponent } from 'app/exercises/text/assess/manual-textblock-selection/manual-textblock-selection.component';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { TextBlock } from 'app/entities/text-block.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { AlertService } from 'app/core/util/alert.service';
import { RouterTestingModule } from '@angular/router/testing';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { GradingInstructionLinkIconComponent } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import { AssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { UnreferencedFeedbackComponent } from 'app/exercises/shared/unreferenced-feedback/unreferenced-feedback.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

describe('TextSubmissionAssessmentComponent', () => {
    let component: TextSubmissionAssessmentComponent;
    let fixture: ComponentFixture<TextSubmissionAssessmentComponent>;
    let textAssessmentService: TextAssessmentService;
    let submissionService: SubmissionService;
    let exampleSubmissionService: ExampleSubmissionService;

    const exercise = {
        id: 20,
        type: ExerciseType.TEXT,
        assessmentType: AssessmentType.MANUAL,
        problemStatement: '',
        course: { id: 123, isAtLeastInstructor: true } as Course,
    } as TextExercise;
    const participation: StudentParticipation = {
        type: ParticipationType.STUDENT,
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
            resultString: '1 of 12 points',
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
    const route = {
        snapshot: { path: '' },
        paramMap: of(
            convertToParamMap({
                exerciseId: '1',
            }),
        ),
        queryParams: of({
            testRun: 'false',
        }),
        data: of({
            studentParticipation: participation,
        }),
    } as unknown as ActivatedRoute;
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

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).not.toBeNull();
    });

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

        expect(component.totalScore).toEqual(42);
    });

    it('should save the assessment with correct parameters', () => {
        textAssessmentService = fixture.debugElement.injector.get(TextAssessmentService);
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
        expect(handleFeedbackStub).toHaveBeenCalled();
    });

    it('should display error when complaint resolved but assessment invalid', () => {
        // would be called on receive of event
        const complaintResponse = new ComplaintResponse();
        const alertService = fixture.debugElement.injector.get(AlertService);
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
        textAssessmentService = fixture.debugElement.injector.get(TextAssessmentService);

        const updateAssessmentAfterComplaintStub = jest.spyOn(textAssessmentService, 'updateAssessmentAfterComplaint');
        updateAssessmentAfterComplaintStub.mockReturnValue(of(new HttpResponse({ body: new Result() })));

        // would be called on receive of event
        const complaintResponse = new ComplaintResponse();
        component.updateAssessmentAfterComplaint(complaintResponse);
        expect(updateAssessmentAfterComplaintStub).toHaveBeenCalled();
    });
    it('should submit the assessment with correct parameters', () => {
        textAssessmentService = fixture.debugElement.injector.get(TextAssessmentService);
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
    it('should invoke import example submission', () => {
        component.submission = submission;
        component.exercise = exercise;

        const importStub = jest.spyOn(exampleSubmissionService, 'import');
        importStub.mockReturnValue(of(new HttpResponse({ body: new ExampleSubmission() })));

        component.useStudentSubmissionAsExampleSubmission();

        expect(importStub).toHaveBeenCalledTimes(1);
        expect(importStub).toHaveBeenCalledWith(submission.id, exercise.id);
    });
});
