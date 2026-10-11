import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CodeEditorStudentContainerComponent } from 'app/programming/overview/code-editor-student-container/code-editor-student-container.component';
import { ResultService } from 'app/exercise/result/result.service';
import { MockResultService } from 'test/helpers/mocks/service/mock-result.service';
import { DomainService } from 'app/programming/shared/code-editor/services/code-editor-domain.service';
import { MockProvider } from 'ng-mocks';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';
import { SubmissionPolicyService } from 'app/programming/manage/services/submission-policy.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { BehaviorSubject, of } from 'rxjs';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ActivatedRoute } from '@angular/router';
import { SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';

describe('CodeEditorStudentContainerComponent', () => {
    let comp: CodeEditorStudentContainerComponent;
    let fixture: ComponentFixture<CodeEditorStudentContainerComponent>;

    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let submissionPolicyService: SubmissionPolicyService;
    let participationWebsocketService: ParticipationWebsocketService;

    const studentParticipation: ProgrammingExerciseStudentParticipation = {
        id: 21,
        exercise: { id: 42, numberOfAssessmentsOfCorrectionRounds: [], secondCorrectionEnabled: false, studentAssignedTeamIdComputed: false },
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                {
                    provide: ActivatedRoute,
                    useValue: { params: of({ participationId: studentParticipation.id }) },
                },
                MockProvider(DomainService),
                MockProvider(SubmissionPolicyService),
                MockProvider(AlertService),
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
            ],
        });
        fixture = TestBed.createComponent(CodeEditorStudentContainerComponent);
        comp = fixture.componentInstance;
        programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);
        submissionPolicyService = TestBed.inject(SubmissionPolicyService);
        participationWebsocketService = TestBed.inject(ParticipationWebsocketService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should correctly initialize the number of submissions for submission policy', () => {
        vi.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithLatestResult').mockReturnValue(of(studentParticipation));
        vi.spyOn(submissionPolicyService, 'getSubmissionPolicyOfProgrammingExercise').mockReturnValue(of({ active: true }));
        const getParticipationSubmissionCountSpy = vi.spyOn(submissionPolicyService, 'getParticipationSubmissionCount').mockReturnValue(of(5));

        comp.ngOnInit();

        expect(getParticipationSubmissionCountSpy).toHaveBeenCalledOnce();
        expect(comp.numberOfSubmissionsForSubmissionPolicy()).toBe(5);
    });

    it.each([undefined, { active: false } as SubmissionPolicy])(
        'should not calculate the number of submissions for no or inactive submission policy',
        (submissionPolicy: SubmissionPolicy | undefined) => {
            vi.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithLatestResult').mockReturnValue(of(studentParticipation));
            vi.spyOn(submissionPolicyService, 'getSubmissionPolicyOfProgrammingExercise').mockReturnValue(of(submissionPolicy));
            const getParticipationSubmissionCountSpy = vi.spyOn(submissionPolicyService, 'getParticipationSubmissionCount');

            comp.ngOnInit();

            expect(getParticipationSubmissionCountSpy).not.toHaveBeenCalled();
        },
    );

    describe('preliminary AI feedback', () => {
        const inlineAIFeedback: Feedback = { id: 1, reference: 'file:src/Sort.java_line:3', type: FeedbackType.AUTOMATIC, detailText: 'Check the loop' };
        const generalAIFeedback: Feedback = { id: 2, type: FeedbackType.AUTOMATIC, detailText: 'Add comments' };
        const testFeedback: Feedback = { id: 3, type: FeedbackType.AUTOMATIC, text: 'testSort' };

        let results$: BehaviorSubject<Result | undefined>;

        const athenaResult = (id: number, submissionId: number, feedbacks: Feedback[]): Result => ({
            id,
            assessmentType: AssessmentType.AUTOMATIC_ATHENA,
            successful: true,
            submission: { id: submissionId },
            feedbacks,
        });
        const buildResult = (id: number, submissionId: number): Result => ({
            id,
            assessmentType: AssessmentType.AUTOMATIC,
            submission: { id: submissionId },
            feedbacks: [testFeedback],
        });

        const loadWith = (results: Result[]) => {
            results$ = new BehaviorSubject<Result | undefined>(undefined);
            vi.spyOn(participationWebsocketService, 'subscribeForLatestResultOfParticipation').mockReturnValue(results$);
            vi.spyOn(submissionPolicyService, 'getSubmissionPolicyOfProgrammingExercise').mockReturnValue(of(undefined));
            vi.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithLatestResult').mockReturnValue(
                of({ ...studentParticipation, submissions: [{ id: 7, results }] } as ProgrammingExerciseStudentParticipation),
            );
            comp.ngOnInit();
        };

        it('should show the AI feedback loaded next to the latest result', () => {
            loadWith([buildResult(16, 7), athenaResult(15, 7, [inlineAIFeedback, generalAIFeedback])]);

            expect(comp.latestResult()?.id).toBe(16);
            expect(comp.editorFeedback()).toEqual([testFeedback, inlineAIFeedback, generalAIFeedback]);
            expect(comp.unreferencedFeedback).toEqual([generalAIFeedback]);
        });

        it('should show AI feedback that arrives over the websocket after the page was loaded', () => {
            loadWith([buildResult(16, 7)]);

            results$.next(athenaResult(17, 7, [inlineAIFeedback]));

            expect(comp.editorFeedback()).toEqual([testFeedback, inlineAIFeedback]);
        });

        it('should keep the AI feedback when the build result of the same submission arrives after it', () => {
            loadWith([athenaResult(15, 7, [inlineAIFeedback])]);

            results$.next(buildResult(16, 7));

            expect(comp.latestResult()?.id).toBe(16);
            expect(comp.athenaResult()?.id).toBe(15);
        });

        it('should drop the AI feedback of the previous submission once a new submission gets a result', () => {
            loadWith([buildResult(16, 7), athenaResult(15, 7, [inlineAIFeedback])]);

            results$.next(buildResult(20, 8));

            expect(comp.athenaResult()).toBeUndefined();
            expect(comp.latestResult()?.id).toBe(20);
        });

        it('should ignore AI feedback that is still being generated', () => {
            loadWith([buildResult(16, 7)]);

            results$.next({ ...athenaResult(17, 7, []), successful: undefined });

            expect(comp.athenaResult()).toBeUndefined();
        });
    });
});
