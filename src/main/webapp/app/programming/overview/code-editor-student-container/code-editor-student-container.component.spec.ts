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
import { of } from 'rxjs';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ActivatedRoute } from '@angular/router';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';

describe('CodeEditorStudentContainerComponent', () => {
    let comp: CodeEditorStudentContainerComponent;
    let fixture: ComponentFixture<CodeEditorStudentContainerComponent>;

    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let submissionPolicyService: SubmissionPolicyService;

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
            ],
        });
        fixture = TestBed.createComponent(CodeEditorStudentContainerComponent);
        comp = fixture.componentInstance;
        programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);
        submissionPolicyService = TestBed.inject(SubmissionPolicyService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should display arriving AI feedback and replace its pending result without losing the build result', () => {
        const buildResult: Result = { id: 1, assessmentType: AssessmentType.AUTOMATIC };
        const participation: ProgrammingExerciseStudentParticipation = {
            ...studentParticipation,
            submissions: [{ id: 10, results: [buildResult] }],
        };
        const submission = { id: 10, participation: { id: participation.id } };
        const pendingResult: Result = { id: 2, assessmentType: AssessmentType.AUTOMATIC_ATHENA, submission };
        vi.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithLatestResult').mockReturnValue(of(participation));
        vi.spyOn(submissionPolicyService, 'getSubmissionPolicyOfProgrammingExercise').mockReturnValue(of(undefined));
        vi.spyOn(submissionPolicyService, 'getParticipationSubmissionCount').mockReturnValue(of(1));
        comp.ngOnInit();

        comp.receivedNewResult(pendingResult);
        expect(comp.latestResult()?.id).toBe(2);

        const feedback = { id: 3, type: FeedbackType.AUTOMATIC, reference: 'file:Main.java_line:0', detailText: 'AI feedback' };
        comp.receivedNewResult({ ...pendingResult, successful: true, feedbacks: [feedback] });

        const updatedSubmission = comp.participation().submissions![0];
        expect(updatedSubmission.results).toHaveLength(2);
        expect(updatedSubmission.results).toContainEqual(buildResult);
        expect(getLatestSubmissionResult(updatedSubmission)?.feedbacks).toEqual([feedback]);
        expect(comp.latestResult()?.feedbacks).toEqual([feedback]);
        expect(participation.submissions![0].results).toEqual([buildResult]);
    });

    it('should ignore a result belonging to a previously opened participation', () => {
        comp.participation.set(studentParticipation);
        comp.receivedNewResult({ id: 2, submission: { id: 10, participation: { id: 99 } } });
        expect(comp.participation()).toBe(studentParticipation);
        expect(comp.latestResult()).toBeUndefined();
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
});
