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
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { HttpResponse } from '@angular/common/http';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
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
    describe('picking a result from the result history', () => {
        const commitHash = '0123456789abcdef0123456789abcdef01234567';
        const aiFeedback = { reference: 'file:src/A.java_line:3', text: 'Check this' } as Feedback;
        // The results of the all-results endpoint come without feedback, which is loaded separately
        const aiResult = { id: 55 } as Result;
        const testResult = { id: 60, feedbacks: [] as Feedback[] } as Result;
        let getFeedbackDetailsSpy: ReturnType<typeof vi.spyOn>;
        const submission = (id: number, hash: string, results: Result[]) => ({ id, commitHash: hash, results }) as ProgrammingSubmission;

        const createWithRoute = (params: Record<string, number>) => {
            // The component of the outer beforeEach has not run ngOnInit yet, so it reads the params set here
            TestBed.inject(ActivatedRoute).params = of(params);
            vi.spyOn(TestBed.inject(ProgrammingExerciseParticipationService), 'getStudentParticipationWithLatestResult').mockReturnValue(
                of({ ...studentParticipation, submissions: [submission(41, 'latest', [testResult])] } as ProgrammingExerciseStudentParticipation),
            );
            vi.spyOn(TestBed.inject(ProgrammingExerciseParticipationService), 'getStudentParticipationWithAllResults').mockReturnValue(
                of({
                    ...studentParticipation,
                    submissions: [submission(41, 'latest', [testResult]), submission(36, commitHash, [{ id: 54 } as Result, aiResult])],
                } as ProgrammingExerciseStudentParticipation),
            );
            vi.spyOn(TestBed.inject(SubmissionPolicyService), 'getSubmissionPolicyOfProgrammingExercise').mockReturnValue(of({ active: false }));
            getFeedbackDetailsSpy = vi.spyOn(TestBed.inject(ResultService), 'getFeedbackDetailsForResult').mockReturnValue(of(new HttpResponse({ body: [aiFeedback] })));
        };

        it('shows the submission of the picked result, with just that result, read-only at its commit', () => {
            createWithRoute({ participationId: 21, submissionId: 36, resultId: 55 });

            comp.ngOnInit();

            expect(comp.viewedResultId()).toBe(55);
            expect(comp.viewedCommitHash()).toBe(commitHash);
            expect(comp.participation().submissions).toHaveLength(1);
            expect(comp.participation().submissions![0].id).toBe(36);
            expect(getFeedbackDetailsSpy).toHaveBeenCalledWith(21, aiResult);
            // The feedback is on the result the editor reads from the start, so inline feedback and file badges can be derived at once
            expect(comp.participation().submissions![0].results).toEqual([{ id: 55, feedbacks: [aiFeedback] }]);
            expect(comp.latestResult()?.feedbacks).toEqual([aiFeedback]);
            expect(comp.canSubmitExercise()).toBe(false);
        });

        it('keeps the latest result, editable, when no result is picked', () => {
            createWithRoute({ participationId: 21 });

            comp.ngOnInit();

            expect(comp.viewedResultId()).toBeUndefined();
            expect(comp.viewedCommitHash()).toBeUndefined();
            expect(comp.latestResult()).toBe(testResult);
            expect(comp.canSubmitExercise()).toBe(true);
        });

        it('falls back to the latest result when the picked result is not found', () => {
            createWithRoute({ participationId: 21, submissionId: 36, resultId: 999 });

            comp.ngOnInit();

            expect(comp.viewedResultId()).toBeUndefined();
            expect(comp.latestResult()).toBe(testResult);
            expect(comp.canSubmitExercise()).toBe(true);
        });
    });
});
