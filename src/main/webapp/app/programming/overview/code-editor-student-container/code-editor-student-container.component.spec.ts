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
import { SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';

describe('CodeEditorStudentContainerComponent', () => {
    let comp: CodeEditorStudentContainerComponent;
    let fixture: ComponentFixture<CodeEditorStudentContainerComponent>;

    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let submissionPolicyService: SubmissionPolicyService;
    let routeData: Record<string, unknown>;

    const studentParticipation: ProgrammingExerciseStudentParticipation = {
        id: 21,
        exercise: { id: 42, numberOfAssessmentsOfCorrectionRounds: [], secondCorrectionEnabled: false, studentAssignedTeamIdComputed: false },
    };

    beforeEach(() => {
        routeData = {};
        TestBed.configureTestingModule({
            providers: [
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ participationId: studentParticipation.id }),
                        snapshot: {
                            get data() {
                                return routeData;
                            },
                        },
                    },
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

    describe('layout', () => {
        beforeEach(() => {
            vi.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithLatestResult').mockReturnValue(of(studentParticipation));
            vi.spyOn(submissionPolicyService, 'getSubmissionPolicyOfProgrammingExercise').mockReturnValue(of(undefined));
        });

        it('should leave out the instructions and the navbar when the exercise page shows them next to the editor', () => {
            comp.ngOnInit();

            expect(comp.fullPage()).toBe(false);
            expect(comp.lightweight()).toBe(true);
        });

        it('should show the instructions and the navbar when the editor is the whole page', () => {
            routeData = { fullPageEditor: true };

            comp.ngOnInit();

            expect(comp.fullPage()).toBe(true);
            expect(comp.lightweight()).toBe(false);
        });
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
