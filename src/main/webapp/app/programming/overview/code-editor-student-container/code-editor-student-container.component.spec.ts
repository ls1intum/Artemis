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
import { Subject, of } from 'rxjs';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ActivatedRoute, Params } from '@angular/router';
import { SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { HttpResponse } from '@angular/common/http';
import { Feedback, FeedbackType, NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER } from 'app/assessment/shared/entities/feedback.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';

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
    describe('an earlier submission from the result history', () => {
        const commitHash = 'a'.repeat(40);
        let resultService: ResultService;

        const participationWithAllResults = (): ProgrammingExerciseStudentParticipation =>
            ({
                ...studentParticipation,
                submissions: [
                    { id: 8, commitHash: 'b'.repeat(40), results: [{ id: 81 }] } as ProgrammingSubmission,
                    { id: 7, commitHash, results: [{ id: 71 }, { id: 72 }] } as ProgrammingSubmission,
                ],
            }) as ProgrammingExerciseStudentParticipation;

        beforeEach(() => {
            resultService = TestBed.inject(ResultService);
            TestBed.inject(ActivatedRoute).params = of({ participationId: studentParticipation.id, submissionId: '7', resultId: '72' });
            vi.spyOn(submissionPolicyService, 'getSubmissionPolicyOfProgrammingExercise').mockReturnValue(of(undefined));
        });

        it('should load only the routed submission and result, with the feedback of that result', () => {
            const latestSpy = vi.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithLatestResult');
            vi.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithAllResults').mockReturnValue(of(participationWithAllResults()));
            const feedbacks: Feedback[] = [{ id: 5, detailText: 'Check the loop' }];
            const detailsSpy = vi.spyOn(resultService, 'getFeedbackDetailsForResult').mockReturnValue(of(new HttpResponse({ body: feedbacks })));

            comp.ngOnInit();

            expect(latestSpy).not.toHaveBeenCalled();
            expect(detailsSpy).toHaveBeenCalledExactlyOnceWith(studentParticipation.id, expect.objectContaining({ id: 72 }));
            expect(comp.viewedSubmission()?.commitHash).toBe(commitHash);
            expect(comp.participation().submissions).toHaveLength(1);
            expect(comp.participation().submissions![0].results).toEqual([expect.objectContaining({ id: 72, feedbacks })]);
            expect(comp.latestResult()?.id).toBe(72);
            expect(comp.loadingParticipation()).toBe(false);
            expect(comp.participationCouldNotBeFetched()).toBe(false);
        });

        it('should still load the next navigation after a submission could not be found', () => {
            const params = new Subject<Params>();
            TestBed.inject(ActivatedRoute).params = params;
            vi.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithAllResults').mockImplementation(() => of(participationWithAllResults()));
            vi.spyOn(resultService, 'getFeedbackDetailsForResult').mockReturnValue(of(new HttpResponse({ body: [] })));

            comp.ngOnInit();
            params.next({ participationId: studentParticipation.id, submissionId: '9', resultId: '72' });
            expect(comp.participationCouldNotBeFetched()).toBe(true);

            params.next({ participationId: studentParticipation.id, submissionId: '7', resultId: '72' });
            expect(comp.participationCouldNotBeFetched()).toBe(false);
            expect(comp.loadingParticipation()).toBe(false);
            expect(comp.viewedSubmission()?.id).toBe(7);
        });

        it('should list the AI feedback about a whole file of an earlier result, but not its feedback on a line or of the tests', () => {
            vi.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithAllResults').mockReturnValue(of(participationWithAllResults()));
            const fileWide: Feedback = { id: 1, type: FeedbackType.AUTOMATIC, text: `${NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER}File Main.java`, detailText: 'Split the class' };
            const onLine: Feedback = {
                id: 2,
                type: FeedbackType.AUTOMATIC,
                text: `${NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER}Rename the loop variable`,
                reference: 'file:Main.java_line:3',
            };
            const testFeedback: Feedback = { id: 3, type: FeedbackType.AUTOMATIC, text: 'testAdd', positive: false };
            vi.spyOn(resultService, 'getFeedbackDetailsForResult').mockReturnValue(of(new HttpResponse({ body: [fileWide, onLine, testFeedback] })));

            comp.ngOnInit();

            expect(comp.unreferencedFeedback).toEqual([fileWide]);
        });

        it('should not list AI feedback about a whole file for the latest result, whose result control opens it', () => {
            const fileWide: Feedback = { id: 1, type: FeedbackType.AUTOMATIC, text: `${NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER}File Main.java` };
            comp.latestResult.set({ id: 1, feedbacks: [fileWide] });

            expect(comp.unreferencedFeedback).toEqual([]);
        });

        it.each([
            { submissionId: '9', resultId: '72' },
            { submissionId: '7', resultId: '81' },
        ])('should report a submission or result the participation does not have as not found: $submissionId/$resultId', ({ submissionId, resultId }) => {
            TestBed.inject(ActivatedRoute).params = of({ participationId: studentParticipation.id, submissionId, resultId });
            vi.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithAllResults').mockReturnValue(of(participationWithAllResults()));

            comp.ngOnInit();

            expect(comp.participationCouldNotBeFetched()).toBe(true);
            expect(comp.viewedSubmission()).toBeUndefined();
        });
    });
});
