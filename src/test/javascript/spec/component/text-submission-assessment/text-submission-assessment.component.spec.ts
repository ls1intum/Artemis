import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TextSubmissionAssessmentComponent } from 'app/exercises/text/assess/text-submission-assessment.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisTestModule } from '../../test.module';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';
import { stub } from 'sinon';
import { HttpResponse } from '@angular/common/http';
import { AssessmentLayoutComponent } from 'app/assessment/assessment-layout/assessment-layout.component';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TextAssessmentAreaComponent } from 'app/exercises/text/assess/text-assessment-area/text-assessment-area.component';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TextblockAssessmentCardComponent } from 'app/exercises/text/assess/textblock-assessment-card/textblock-assessment-card.component';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess/textblock-feedback-editor/textblock-feedback-editor.component';
import { TranslateModule } from '@ngx-translate/core';
import { ExerciseType } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { getLatestSubmissionResult, SubmissionExerciseType, SubmissionType } from 'app/entities/submission.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Result } from 'app/entities/result.model';
import * as moment from 'moment';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';
import { Course } from 'app/entities/course.model';
import { ManualTextblockSelectionComponent } from 'app/exercises/text/assess/manual-textblock-selection/manual-textblock-selection.component';
import { TextSharedModule } from 'app/exercises/text/shared/text-shared.module';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { TextBlock } from 'app/entities/text-block.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { JhiAlertService } from 'ng-jhipster';
import { RouterTestingModule } from '@angular/router/testing';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { ArtemisGradingInstructionLinkIconModule } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.module';

describe('TextSubmissionAssessmentComponent', () => {
    let component: TextSubmissionAssessmentComponent;
    let fixture: ComponentFixture<TextSubmissionAssessmentComponent>;
    let textAssessmentService: TextAssessmentService;
    let submissionService: SubmissionService;

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
        submissionDate: moment('2019-07-09T10:47:33.244Z'),
        text: 'First text. Second text.',
        participation,
    } as unknown as TextSubmission;
    submission.results = [
        {
            id: 2374,
            resultString: '1 of 12 points',
            completionDate: moment('2019-07-09T11:51:23.251Z'),
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
    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                ArtemisSharedModule,
                ArtemisAssessmentSharedModule,
                AssessmentInstructionsModule,
                TranslateModule.forRoot(),
                ArtemisConfirmIconModule,
                TextSharedModule,
                RouterTestingModule,
                ArtemisGradingInstructionLinkIconModule,
            ],
            declarations: [
                TextSubmissionAssessmentComponent,
                TextAssessmentAreaComponent,
                TextblockAssessmentCardComponent,
                TextblockFeedbackEditorComponent,
                ManualTextblockSelectionComponent,
            ],
            providers: [{ provide: ActivatedRoute, useValue: route }],
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

        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should show jhi-text-assessment-area', () => {
        component['setPropertiesFromServerResponse'](participation);
        fixture.detectChanges();

        const textAssessmentArea = fixture.debugElement.query(By.directive(TextAssessmentAreaComponent));
        expect(textAssessmentArea).toBeTruthy();
    });

    it('should use jhi-assessment-layout', () => {
        const sharedLayout = fixture.debugElement.query(By.directive(AssessmentLayoutComponent));
        expect(sharedLayout).toBeTruthy();
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

    it('should save the assessment with correct parameters', function () {
        textAssessmentService = fixture.debugElement.injector.get(TextAssessmentService);
        component['setPropertiesFromServerResponse'](participation);
        const handleFeedbackStub = stub(submissionService, 'handleFeedbackCorrectionRoundTag');

        fixture.detectChanges();

        const result = getLatestSubmissionResult(submission);
        const textBlockRef = component.textBlockRefs[1];
        textBlockRef.initFeedback();
        textBlockRef.feedback!.detailText = 'my feedback';
        textBlockRef.feedback!.credits = 42;

        spyOn(textAssessmentService, 'save').and.returnValue(
            of(
                new HttpResponse({
                    body: result,
                }),
            ),
        );

        component.validateFeedback();
        component.save();
        expect(textAssessmentService.save).toHaveBeenCalledWith(
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
        const alertService = fixture.debugElement.injector.get(JhiAlertService);
        spyOn(alertService, 'error');

        component.updateAssessmentAfterComplaint(complaintResponse);
        expect(alertService.error).toHaveBeenCalledWith('artemisApp.textAssessment.error.invalidAssessments');
    });

    it('should send update when complaint resolved and assessments are valid', () => {
        const unreferencedFeedback = new Feedback();
        unreferencedFeedback.credits = 5;
        unreferencedFeedback.detailText = 'gj';
        unreferencedFeedback.type = FeedbackType.MANUAL_UNREFERENCED;
        unreferencedFeedback.id = 1;
        component.unreferencedFeedback = [unreferencedFeedback];
        textAssessmentService = fixture.debugElement.injector.get(TextAssessmentService);
        spyOn(textAssessmentService, 'updateAssessmentAfterComplaint').and.returnValue(
            of(
                new HttpResponse({
                    body: new Result(),
                }),
            ),
        );

        // would be called on receive of event
        const complaintResponse = new ComplaintResponse();
        component.updateAssessmentAfterComplaint(complaintResponse);
        expect(textAssessmentService.updateAssessmentAfterComplaint).toHaveBeenCalled();
    });
    it('should submit the assessment with correct parameters', function () {
        textAssessmentService = fixture.debugElement.injector.get(TextAssessmentService);
        component['setPropertiesFromServerResponse'](participation);
        fixture.detectChanges();

        const result = getLatestSubmissionResult(submission);
        const textBlockRef = component.textBlockRefs[1];
        textBlockRef.initFeedback();
        textBlockRef.feedback!.detailText = 'my feedback';
        textBlockRef.feedback!.credits = 42;

        spyOn(textAssessmentService, 'submit').and.returnValue(
            of(
                new HttpResponse({
                    body: result,
                }),
            ),
        );

        component.validateFeedback();
        component.submit();
        expect(textAssessmentService.submit).toHaveBeenCalledWith(
            participation.id!,
            result!.id!,
            [component.textBlockRefs[0].feedback!, textBlockRef.feedback!],
            [component.textBlockRefs[0].block!, textBlockRef.block!],
        );
    });
});
