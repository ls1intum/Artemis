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
import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
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
        // The template is not under test here; an empty one keeps change detection from instantiating the full editor
        TestBed.overrideComponent(CodeEditorStudentContainerComponent, { set: { template: '', imports: [] } });
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

    describe('early repository access phase', () => {
        beforeEach(() => {
            vi.useFakeTimers();
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        const earlyExercise = (startInMs: number) =>
            ({
                id: 42,
                type: ExerciseType.PROGRAMMING,
                allowRepositoryAccessBeforeStartDate: true,
                releaseDate: dayjs().subtract(1, 'hour'),
                startDate: dayjs().add(startInMs, 'millisecond'),
            }) as ProgrammingExercise;

        const loadExercise = (exercise: ProgrammingExercise) => {
            const participation = { id: 21, exercise } as ProgrammingExerciseStudentParticipation;
            vi.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithLatestResult').mockReturnValue(of(participation));
            vi.spyOn(submissionPolicyService, 'getSubmissionPolicyOfProgrammingExercise').mockReturnValue(of(undefined));
            comp.ngOnInit();
        };

        it('should stop reporting the phase once the start date is reached', () => {
            loadExercise(earlyExercise(60_000));
            TestBed.tick();
            expect(comp.inEarlyRepositoryAccessPhase()).toBe(true);

            vi.advanceTimersByTime(59_000);
            TestBed.tick();
            expect(comp.inEarlyRepositoryAccessPhase()).toBe(true);

            vi.advanceTimersByTime(2_000);
            TestBed.tick();
            expect(comp.inEarlyRepositoryAccessPhase()).toBe(false);
        });

        it('should reschedule when the start date exceeds the maximum timeout', () => {
            const maxTimeout = 2 ** 31 - 1;
            loadExercise(earlyExercise(maxTimeout + 60_000));
            TestBed.tick();

            vi.advanceTimersByTime(maxTimeout + 1);
            TestBed.tick();
            expect(comp.inEarlyRepositoryAccessPhase()).toBe(true);

            vi.advanceTimersByTime(60_000);
            TestBed.tick();
            expect(comp.inEarlyRepositoryAccessPhase()).toBe(false);
        });

        it('should clear the timer on destroy', () => {
            loadExercise(earlyExercise(60_000));
            TestBed.tick();
            expect(vi.getTimerCount()).toBe(1);

            fixture.destroy();
            expect(vi.getTimerCount()).toBe(0);
        });
    });
});
