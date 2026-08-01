import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CodeEditorStudentContainerComponent } from 'app/programming/overview/code-editor-student-container/code-editor-student-container.component';
import { ResultService } from 'app/exercise/result/result.service';
import { MockResultService } from 'test/helpers/mocks/service/mock-result.service';
import { DomainService } from 'app/programming/shared/code-editor/services/code-editor-domain.service';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';
import { SubmissionPolicyService } from 'app/programming/manage/services/submission-policy.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { of } from 'rxjs';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ActivatedRoute } from '@angular/router';
import { SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { CodeEditorRepositoryIsLockedComponent } from 'app/programming/shared/code-editor/layout/code-editor-repository-is-locked.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/student/programming-exercise-student-trigger-build-button.component';
import { ProblemStatementRendererComponent } from 'app/programming/shared/instructions-render/ssr/problem-statement-renderer.component';
import { AdditionalFeedbackComponent } from 'app/exercise/additional-feedback/additional-feedback.component';

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
});

describe('CodeEditorStudentContainerComponent problem statement binding', () => {
    let comp: CodeEditorStudentContainerComponent;
    let fixture: ComponentFixture<CodeEditorStudentContainerComponent>;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let submissionPolicyService: SubmissionPolicyService;

    const studentParticipation: ProgrammingExerciseStudentParticipation = {
        id: 21,
        exercise: { id: 42, numberOfAssessmentsOfCorrectionRounds: [], secondCorrectionEnabled: false, studentAssignedTeamIdComputed: false },
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: ActivatedRoute, useValue: { params: of({ participationId: studentParticipation.id }) } },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(DomainService),
                MockProvider(SubmissionPolicyService),
                MockProvider(AlertService),
            ],
        })
            // The real children (code editor, updating result, ...) need infrastructure this spec does not set up
            // (Monaco, resize observers, ...); only the problem-statement-renderer binding under test stays real.
            .overrideComponent(CodeEditorStudentContainerComponent, {
                remove: {
                    imports: [
                        TranslateDirective,
                        CodeEditorContainerComponent,
                        IncludedInScoreBadgeComponent,
                        CodeEditorRepositoryIsLockedComponent,
                        UpdatingResultComponent,
                        ProgrammingExerciseStudentTriggerBuildButtonComponent,
                        ProblemStatementRendererComponent,
                        AdditionalFeedbackComponent,
                    ],
                },
                add: {
                    imports: [
                        MockDirective(TranslateDirective),
                        MockComponent(CodeEditorContainerComponent),
                        MockComponent(IncludedInScoreBadgeComponent),
                        MockComponent(CodeEditorRepositoryIsLockedComponent),
                        MockComponent(UpdatingResultComponent),
                        MockComponent(ProgrammingExerciseStudentTriggerBuildButtonComponent),
                        MockComponent(ProblemStatementRendererComponent),
                        MockComponent(AdditionalFeedbackComponent),
                    ],
                },
            })
            .compileComponents();
        fixture = TestBed.createComponent(CodeEditorStudentContainerComponent);
        comp = fixture.componentInstance;
        programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);
        submissionPolicyService = TestBed.inject(SubmissionPolicyService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("binds personal live updates for the student's own participation", () => {
        vi.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithLatestResult').mockReturnValue(of(studentParticipation));
        vi.spyOn(submissionPolicyService, 'getSubmissionPolicyOfProgrammingExercise').mockReturnValue(of(undefined));

        comp.ngOnInit();
        fixture.detectChanges();

        // The mocked component exposes signal inputs as plain values, not callables.
        const renderer = fixture.debugElement.query(By.directive(ProblemStatementRendererComponent)).componentInstance as unknown as { liveUpdates: string };
        expect(renderer.liveUpdates).toBe('personal');
    });
});
