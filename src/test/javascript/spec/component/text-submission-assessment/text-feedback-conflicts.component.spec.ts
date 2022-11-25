import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { By } from '@angular/platform-browser';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TextFeedbackConflictsComponent } from 'app/exercises/text/assess/conflicts/text-feedback-conflicts.component';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { TextAssessmentAreaComponent } from 'app/exercises/text/assess/text-assessment-area/text-assessment-area.component';
import { TextblockAssessmentCardComponent } from 'app/exercises/text/assess/textblock-assessment-card/textblock-assessment-card.component';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess/textblock-feedback-editor/textblock-feedback-editor.component';
import { ManualTextblockSelectionComponent } from 'app/exercises/text/assess/manual-textblock-selection/manual-textblock-selection.component';
import { TextFeedbackConflictsHeaderComponent } from 'app/exercises/text/assess/conflicts/conflicts-header/text-feedback-conflicts-header.component';
import { SubmissionExerciseType, SubmissionType, getLatestSubmissionResult } from 'app/entities/submission.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Result } from 'app/entities/result.model';
import { Feedback } from 'app/entities/feedback.model';
import { TextBlock } from 'app/entities/text-block.model';
import dayjs from 'dayjs/esm';
import { FeedbackConflict, FeedbackConflictType } from 'app/entities/feedback-conflict';
import { ExerciseType } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('TextFeedbackConflictsComponent', () => {
    let component: TextFeedbackConflictsComponent;
    let fixture: ComponentFixture<TextFeedbackConflictsComponent>;
    let textAssessmentService: TextAssessmentService;

    const exercise = {
        id: 20,
        type: ExerciseType.TEXT,
        assessmentType: AssessmentType.MANUAL,
        course: { id: 123, isAtLeastInstructor: true } as Course,
    } as TextExercise;
    const participation: StudentParticipation = {
        type: ParticipationType.STUDENT,
        exercise,
    } as unknown as StudentParticipation;
    const textSubmission = {
        submissionExerciseType: SubmissionExerciseType.TEXT,
        id: 2278,
        submitted: true,
        type: SubmissionType.MANUAL,
        submissionDate: dayjs('2019-07-09T10:47:33.244Z'),
        text: 'First text. Second text.',
        participation,
    } as unknown as TextSubmission;
    textSubmission.results = [
        {
            id: 2374,
            completionDate: dayjs('2019-07-09T11:51:23.251Z'),
            successful: false,
            score: 8,
            rated: true,
            hasComplaint: false,
            textSubmission,
        } as unknown as Result,
    ];
    textSubmission.latestResult = getLatestSubmissionResult(textSubmission);

    textSubmission.latestResult!.feedbacks = [
        {
            id: 1,
            detailText: 'First Feedback',
            credits: 1.5,
            reference: 'First text id',
        } as Feedback,
    ];
    textSubmission.blocks = [
        {
            id: 'First text id',
            text: 'First text.',
            startIndex: 0,
            endIndex: 11,
            textSubmission,
        } as unknown as TextBlock,
        {
            id: 'second text id',
            text: 'Second text.',
            startIndex: 12,
            endIndex: 24,
            textSubmission,
        } as unknown as TextBlock,
    ];
    textSubmission.latestResult!.feedbacks![0].conflictingTextAssessments = [
        {
            id: 1,
            conflict: true,
            conflictingFeedbackId: 5,
            createdAt: dayjs('2019-07-09T11:51:23.251Z'),
            type: FeedbackConflictType.INCONSISTENT_COMMENT,
            markedAsNoConflict: false,
        } as FeedbackConflict,
    ];

    const conflictingSubmission = {
        submissionExerciseType: SubmissionExerciseType.TEXT,
        id: 2280,
        submitted: true,
        type: SubmissionType.MANUAL,
        submissionDate: dayjs('2019-07-09T10:47:33.244Z'),
        text: 'First Conflicting Submission Text.',
    } as unknown as TextSubmission;
    conflictingSubmission.results = [
        {
            id: 2375,
            completionDate: dayjs('2020-02-10T11:51:23.251Z'),
            successful: false,
            score: 3,
            rated: true,
            hasComplaint: false,
            conflictingSubmission,
        } as unknown as Result,
    ];
    conflictingSubmission.latestResult = getLatestSubmissionResult(conflictingSubmission);
    conflictingSubmission.latestResult!.feedbacks = [
        {
            id: 5,
            detailText: 'Conflicting feedback',
            credits: 1.5,
            reference: 'Conflicting text id',
        } as Feedback,
    ];
    conflictingSubmission.blocks = [
        {
            id: 'Conflicting text id',
            text: 'First Conflicting Submission Text.',
            startIndex: 0,
            endIndex: 34,
            conflictingSubmission,
        } as unknown as TextBlock,
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                TextFeedbackConflictsHeaderComponent,
                TextFeedbackConflictsComponent,
                TextAssessmentAreaComponent,
                TextblockAssessmentCardComponent,
                MockComponent(TextblockFeedbackEditorComponent),
                MockComponent(ManualTextblockSelectionComponent),
                MockComponent(ScoreDisplayComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ feedbackId: 1 }) } } },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                MockProvider(Router),
            ],
        }).compileComponents();
    });

    beforeEach(() => {
        jest.spyOn(TestBed.inject(Router), 'getCurrentNavigation').mockReturnValue({ extras: { state: { submission: textSubmission } } } as any);
        fixture = TestBed.createComponent(TextFeedbackConflictsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set passed parameters correctly in constructor', () => {
        expect(component.leftSubmission).toBe(textSubmission);
        expect(component.leftFeedbackId).toBe(1);
        expect(component.exercise).toBe(exercise);
    });

    it('should use jhi-text-feedback-conflicts-header', () => {
        const headerComponent = fixture.debugElement.query(By.directive(TextFeedbackConflictsHeaderComponent));
        expect(headerComponent).not.toBeNull();
    });

    it('should set conflicting submission correctly', () => {
        component['setPropertiesFromServerResponse']([conflictingSubmission]);
        fixture.detectChanges();
        expect(component.rightSubmission).toBe(conflictingSubmission);
        expect(component.rightTotalScore).toBe(1.5);
        expect(component.feedbackConflicts).toBe(textSubmission.latestResult!.feedbacks![0].conflictingTextAssessments!);
        expect(component.rightTextBlockRefs[0].feedback).toBe(conflictingSubmission.latestResult!.feedbacks![0]);
    });

    it('should use jhi-text-assessment-area', () => {
        component['setPropertiesFromServerResponse']([conflictingSubmission]);
        fixture.detectChanges();
        const textAssessmentAreaComponent = fixture.debugElement.query(By.directive(TextAssessmentAreaComponent));
        expect(textAssessmentAreaComponent).not.toBeNull();
    });

    it('should solve conflict by overriding left submission', () => {
        textAssessmentService = TestBed.inject(TextAssessmentService);
        component['setPropertiesFromServerResponse']([conflictingSubmission]);
        fixture.detectChanges();

        expect(component.isOverrideDisabled).toBeTrue();

        const textAssessmentArea = fixture.debugElement.query(By.directive(TextAssessmentAreaComponent));
        const textAssessmentAreaComponent = textAssessmentArea.componentInstance as TextAssessmentAreaComponent;
        const textBlockRef = textAssessmentAreaComponent.textBlockRefs[0];
        textBlockRef.feedback!.detailText = 'Correcting feedback text.';
        textBlockRef.feedback!.credits = 2;
        textAssessmentAreaComponent.textBlockRefsChangeEmit();

        expect(component.leftTotalScore).toBe(2);
        expect(component.isOverrideDisabled).toBeFalse();

        jest.spyOn(textAssessmentService, 'submit').mockReturnValue(
            of(
                new HttpResponse({
                    body: component.leftSubmission!.latestResult,
                }),
            ),
        );
        component.overrideLeftSubmission();
        expect(textAssessmentService.submit).toHaveBeenCalledWith(
            participation.id!,
            textSubmission.latestResult!.id!,
            [component.leftTextBlockRefs[0].feedback!],
            [component.leftTextBlockRefs[0].block!],
        );
    });

    it('should be able to select conflicting feedback', () => {
        component['setPropertiesFromServerResponse']([conflictingSubmission]);
        fixture.detectChanges();

        const textBlockAssessmentAreas = fixture.debugElement.queryAll(By.directive(TextblockAssessmentCardComponent));
        textBlockAssessmentAreas.forEach((textBlockAssessmentCardArea) => {
            const textBlockAssessmentCardComponent = textBlockAssessmentCardArea.componentInstance as TextblockAssessmentCardComponent;
            if (textBlockAssessmentCardComponent.textBlockRef === component.rightTextBlockRefs[0]) {
                textBlockAssessmentCardComponent.select();
            }
        });

        expect(component.selectedRightFeedbackId).toBeDefined();
        expect(component.selectedRightFeedbackId).toBe(conflictingSubmission.latestResult!.feedbacks![0].id);
    });

    it('should be able to un-select conflicting feedback', () => {
        component['setPropertiesFromServerResponse']([conflictingSubmission]);
        fixture.detectChanges();

        const textBlockAssessmentAreas = fixture.debugElement.queryAll(By.directive(TextblockAssessmentCardComponent));
        textBlockAssessmentAreas.forEach((textBlockAssessmentCardArea) => {
            const textBlockAssessmentCardComponent = textBlockAssessmentCardArea.componentInstance as TextblockAssessmentCardComponent;
            if (textBlockAssessmentCardComponent.textBlockRef === component.rightTextBlockRefs[0]) {
                textBlockAssessmentCardComponent.select();
                fixture.detectChanges();
                textBlockAssessmentCardComponent.select();
            }
        });

        expect(component.selectedRightFeedbackId).toBeUndefined();
    });

    it('should not be able to select conflicting feedback for left submission', () => {
        component['setPropertiesFromServerResponse']([conflictingSubmission]);
        fixture.detectChanges();

        const textBlockAssessmentAreas = fixture.debugElement.queryAll(By.directive(TextblockAssessmentCardComponent));
        textBlockAssessmentAreas.forEach((textBlockAssessmentCardArea) => {
            const textBlockAssessmentCardComponent = textBlockAssessmentCardArea.componentInstance as TextblockAssessmentCardComponent;
            if (textBlockAssessmentCardComponent.textBlockRef === component.leftTextBlockRefs[0]) {
                jest.spyOn(textBlockAssessmentCardComponent.didSelect, 'emit');
                textBlockAssessmentCardComponent.select();
                expect(textBlockAssessmentCardComponent.didSelect.emit).not.toHaveBeenCalled();
            }
        });

        expect(component.selectedRightFeedbackId).toBeUndefined();
    });

    it('should discard conflict', () => {
        textAssessmentService = TestBed.inject(TextAssessmentService);
        component['setPropertiesFromServerResponse']([conflictingSubmission]);
        fixture.detectChanges();

        component.didSelectConflictingFeedback(conflictingSubmission.latestResult!.feedbacks![0].id!);

        const feedbackConflict = textSubmission.latestResult!.feedbacks![0].conflictingTextAssessments![0];
        feedbackConflict.conflict = false;
        feedbackConflict.discard = true;
        jest.spyOn(textAssessmentService, 'solveFeedbackConflict').mockReturnValue(of(feedbackConflict));
        component.discardConflict();
        expect(textAssessmentService.solveFeedbackConflict).toHaveBeenCalledWith(exercise!.id!, feedbackConflict.id!);
    });

    it('should handle error when solving conflicts', () => {
        textAssessmentService = TestBed.inject(TextAssessmentService);
        component['setPropertiesFromServerResponse']([conflictingSubmission]);
        fixture.detectChanges();

        component.didSelectConflictingFeedback(conflictingSubmission.latestResult!.feedbacks![0].id!);
        const errorResponse = new HttpErrorResponse({ status: 403 });
        const solveConflictStub = jest.spyOn(textAssessmentService, 'solveFeedbackConflict').mockReturnValue(throwError(() => errorResponse));
        component.discardConflict();

        expect(solveConflictStub).toHaveBeenCalledOnce();
        expect(component.isMarkingDisabled).toBeTrue();
        expect(component.markBusy).toBeFalse();
    });

    it('should switch submissions when it changed in the header', () => {
        const secondConflictingSubmission = Object.assign({}, conflictingSubmission);
        secondConflictingSubmission.id! += 1;
        secondConflictingSubmission.latestResult!.feedbacks![0].id! += 1;
        secondConflictingSubmission.latestResult!.id! += 1;

        component['setPropertiesFromServerResponse']([conflictingSubmission, secondConflictingSubmission]);
        fixture.detectChanges();

        const textFeedbackConflictsHeader = fixture.debugElement.query(By.directive(TextFeedbackConflictsHeaderComponent));
        const textFeedbackConflictsHeaderComponent = textFeedbackConflictsHeader.componentInstance as TextFeedbackConflictsHeaderComponent;
        expect(textFeedbackConflictsHeaderComponent.numberOfConflicts).toBe(2);
        textFeedbackConflictsHeaderComponent.onNextConflict();
        expect(component.rightSubmission).toBe(secondConflictingSubmission);
        textFeedbackConflictsHeaderComponent.onPrevConflict();
        expect(component.rightSubmission).toBe(conflictingSubmission);
    });

    it('should go back if there is no conflict', () => {
        const clickSpy = jest.spyOn(component, 'didClickedButtonNoConflict');
        fixture.detectChanges();
        const button = fixture.debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        button.triggerEventHandler('click', null);

        expect(clickSpy).toHaveBeenCalledOnce();
    });

    it('should handle error correctly when submitting left submission', () => {
        textAssessmentService = TestBed.inject(TextAssessmentService);
        const errorResponse = new HttpErrorResponse({ status: 403 });
        const errorStub = jest.spyOn(textAssessmentService, 'submit').mockReturnValue(throwError(() => errorResponse));

        component.overrideLeftSubmission();

        expect(errorStub).toHaveBeenCalledOnce();
        expect(component.isOverrideDisabled).toBeTrue();
    });
});
