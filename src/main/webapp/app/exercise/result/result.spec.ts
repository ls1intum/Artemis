import { expect, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResultComponent } from 'app/exercise/result/result.component';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { cloneDeep } from 'lodash-es';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faCheckCircle, faQuestionCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { MissingResultInformation, ResultTemplateStatus } from 'app/exercise/result/result.utils';
import { ResultProgressBarComponent } from 'app/exercise/result/result-progress-bar/result-progress-bar.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';
import { MIN_SCORE_GREEN, MIN_SCORE_ORANGE } from 'app/app.constants';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('ResultComponent', () => {
    let fixture: ComponentFixture<ResultComponent>;
    let component: ResultComponent;

    const result: Result = { id: 0, submission: { id: 1, participation: { id: 1, exercise: { type: ExerciseType.PROGRAMMING } as Exercise } } };
    const programmingExercise: ProgrammingExercise = {
        id: 1,
        type: ExerciseType.PROGRAMMING,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };
    const programmingParticipation: ProgrammingExerciseStudentParticipation = { id: 2, type: ParticipationType.PROGRAMMING, exercise: programmingExercise };

    const modelingExercise: ModelingExercise = {
        id: 3,
        type: ExerciseType.MODELING,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };
    const modelingParticipation: StudentParticipation = { id: 4, type: ParticipationType.STUDENT, exercise: modelingExercise };

    const textExercise: TextExercise = {
        id: 5,
        type: ExerciseType.TEXT,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };

    const textParticipation: StudentParticipation = { id: 6, type: ParticipationType.STUDENT, exercise: textExercise };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ResultComponent],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(ResultComponent, {
                remove: { imports: [ResultProgressBarComponent, ArtemisTranslatePipe, ArtemisTimeAgoPipe, ArtemisDatePipe, TranslateDirective, NgbTooltip] },
                add: {
                    imports: [
                        MockComponent(ResultProgressBarComponent),
                        MockPipe(ArtemisTranslatePipe),
                        MockPipe(ArtemisTimeAgoPipe),
                        MockPipe(ArtemisDatePipe),
                        MockDirective(TranslateDirective),
                        MockDirective(NgbTooltip),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ResultComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });
    it('should set results for programming exercise', () => {
        const submission1: Submission = { id: 1, participation: programmingParticipation };
        const result1: Result = { id: 1, submission: submission1, score: 1 };
        const participation1 = cloneDeep(programmingParticipation);
        participation1.submissions = [submission1];
        submission1.results = [result1];

        fixture.componentRef.setInput('participation', participation1);
        fixture.componentRef.setInput('result', result1);
        fixture.componentRef.setInput('showUngradedResults', true);

        fixture.detectChanges();

        expect(component.result()).toEqual(result1);
        expect(component.textColorClass()).toBe('text-secondary');
        expect(component.resultIconClass()).toEqual(faQuestionCircle);
        expect(component.resultString()).toBe('artemisApp.result.resultString.programmingShort (artemisApp.result.preliminary)');
    });

    it('should set results for modeling exercise', () => {
        const submission1: Submission = { id: 1, participation: modelingParticipation };
        const result1: Result = { id: 1, submission: submission1, score: 1 };
        const participation1 = cloneDeep(modelingParticipation);
        participation1.submissions = [submission1];
        submission1.results = [result1];
        fixture.componentRef.setInput('participation', participation1);
        fixture.componentRef.setInput('result', result1);
        fixture.componentRef.setInput('showUngradedResults', true);

        fixture.detectChanges();

        expect(component.result()).toEqual(result1);
        expect(component.textColorClass()).toBe('text-danger');
        expect(component.resultIconClass()).toEqual(faTimesCircle);
        expect(component.resultString()).toBe('artemisApp.result.resultString.short');
        expect(component.templateStatus()).toBe(ResultTemplateStatus.HAS_RESULT);
    });

    it('should set (automatic athena) results for modeling exercise', () => {
        const submission1: Submission = { id: 1, participation: modelingParticipation };
        const result1: Result = { id: 1, submission: submission1, score: 0.8, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: true };
        const participation1 = cloneDeep(modelingParticipation);
        participation1.submissions = [submission1];
        submission1.results = [result1];
        fixture.componentRef.setInput('participation', participation1);
        fixture.componentRef.setInput('result', result1);
        fixture.componentRef.setInput('showUngradedResults', true);

        fixture.detectChanges();

        expect(component.result()).toEqual(result1);
        expect(component.textColorClass()).toBe('text-secondary');
        expect(component.resultIconClass()).toEqual(faCheckCircle);
        expect(component.resultString()).toBe('artemisApp.result.resultString.short (artemisApp.result.preliminary)');
        expect(component.templateStatus()).toBe(ResultTemplateStatus.HAS_RESULT);
    });

    it('should set (automatic athena) results for programming exercise', () => {
        const submission1: Submission = { id: 1, participation: programmingParticipation };
        const result1: Result = { id: 1, submission: submission1, score: 0.8, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: true };
        const participation1 = cloneDeep(programmingParticipation);
        participation1.submissions = [submission1];
        submission1.results = [result1];
        fixture.componentRef.setInput('participation', participation1);
        fixture.componentRef.setInput('result', result1);
        fixture.componentRef.setInput('showUngradedResults', true);

        fixture.detectChanges();

        expect(component.result()).toEqual(result1);
        expect(component.textColorClass()).toBe('text-secondary');
        expect(component.resultIconClass()).toEqual(faCheckCircle);
        expect(component.resultString()).toBe('artemisApp.result.resultString.automaticAIFeedbackSuccessful (artemisApp.result.preliminary)');
        expect(component.templateStatus()).toBe(ResultTemplateStatus.HAS_RESULT);
    });

    it('should set (automatic athena) results for text exercise', () => {
        const submission1: Submission = { id: 1, participation: textParticipation };
        const result1: Result = { id: 1, submission: submission1, score: 1, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: true };
        const participation1 = cloneDeep(textParticipation);
        participation1.submissions = [submission1];
        submission1.results = [result1];
        fixture.componentRef.setInput('participation', participation1);
        fixture.componentRef.setInput('result', result1);
        fixture.componentRef.setInput('showUngradedResults', true);

        fixture.detectChanges();

        expect(component.result()).toEqual(result1);
        expect(component.textColorClass()).toBe('text-secondary');
        expect(component.resultIconClass()).toEqual(faCheckCircle);
        expect(component.resultString()).toBe('artemisApp.result.resultString.short (artemisApp.result.preliminary)');
    });

    it.each([
        // never show icon in long format, the text already contains the relevant information
        { short: false, score: MIN_SCORE_ORANGE - 3, codeIssues: 1, iconShown: false },
        { short: false, score: MIN_SCORE_ORANGE, codeIssues: 1, iconShown: false },
        { short: false, score: MIN_SCORE_GREEN, codeIssues: 2, iconShown: false },
        // show independent of score
        { short: true, score: MIN_SCORE_ORANGE - 3, codeIssues: 1, iconShown: true },
        { short: true, score: MIN_SCORE_ORANGE, codeIssues: 1, iconShown: true },
        { short: true, score: MIN_SCORE_GREEN, codeIssues: 2, iconShown: true },
        // show only if code issues exist
        { short: true, score: MIN_SCORE_GREEN, codeIssues: undefined, iconShown: false },
        { short: true, score: MIN_SCORE_GREEN, codeIssues: 0, iconShown: false },
        { short: true, score: MIN_SCORE_GREEN, codeIssues: 10, iconShown: true },
    ])('should show a warning icon if code issues exist (%s)', ({ short, score, codeIssues, iconShown }) => {
        let submission: Submission = { id: 1 };
        const result: Result = {
            id: 3,
            submission,
            score,
            testCaseCount: 2,
            codeIssueCount: codeIssues,
            completionDate: dayjs().subtract(2, 'minutes'),
        };
        submission = { ...submission, results: [result] };
        const participation = cloneDeep(programmingParticipation);
        participation.submissions = [submission];
        fixture.componentRef.setInput('short', short);
        fixture.componentRef.setInput('participation', participation);
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();

        const warningIcon = fixture.debugElement.nativeElement.querySelector('#code-issue-warnings-icon');
        if (iconShown) {
            expect(warningIcon).toBeDefined();
        } else {
            expect(warningIcon).toBeNull();
        }
    });

    // The `isInSidebarCard` placement (course-overview sidebar, rendered via jhi-updating-result) is the one
    // jhi-result mode that is not exercised by the code-editor / exercise-header E2E flows, so it is covered here:
    // the score badge still renders, but it must be non-clickable (no navigation to the result detail dialog).
    it('should render a non-clickable score badge in the sidebar-card placement', () => {
        const submission: Submission = { id: 1, participation: programmingParticipation };
        const ratedResult: Result = { id: 7, submission, score: 100, rated: true, successful: true, completionDate: dayjs().subtract(2, 'minutes') };
        submission.results = [ratedResult];
        const participation = cloneDeep(programmingParticipation);
        participation.submissions = [submission];

        fixture.componentRef.setInput('participation', participation);
        fixture.componentRef.setInput('result', ratedResult);
        fixture.componentRef.setInput('showCompletion', true);
        fixture.componentRef.setInput('isInSidebarCard', true);
        fixture.detectChanges();

        expect(component.templateStatus()).toBe(ResultTemplateStatus.HAS_RESULT);
        const resultScore = fixture.debugElement.nativeElement.querySelector('#result-score');
        expect(resultScore).not.toBeNull();
        expect(resultScore.classList.contains('clickable-result')).toBe(false);
    });

    it('should render the score for a result-only input (e.g. build queue), resolving context from result.submission.participation', () => {
        // The admin/instructor build-queue (finished-jobs-table) passes [result] only — no [participation]/[exercise].
        // The component must resolve the exercise/participation by navigating result.submission.participation and
        // render the score badge (HAS_RESULT). This is the only placement that relies on that navigation.
        const submission: Submission = { id: 9, participation: programmingParticipation };
        const standaloneResult: Result = { id: 9, submission, score: 100, rated: true, successful: true, completionDate: dayjs().subtract(1, 'minute') };

        fixture.componentRef.setInput('result', standaloneResult);
        fixture.componentRef.setInput('showBadge', true);
        fixture.detectChanges();

        expect(component.resolvedExercise()?.type).toBe(ExerciseType.PROGRAMMING);
        expect(component.templateStatus()).toBe(ResultTemplateStatus.HAS_RESULT);
        expect(fixture.debugElement.nativeElement.querySelector('#result-score')).not.toBeNull();
    });

    it('should render a clickable score badge outside a sidebar card', () => {
        const submission: Submission = { id: 1, participation: programmingParticipation };
        const ratedResult: Result = { id: 8, submission, score: 100, rated: true, successful: true, completionDate: dayjs().subtract(2, 'minutes') };
        submission.results = [ratedResult];
        const participation = cloneDeep(programmingParticipation);
        participation.submissions = [submission];

        fixture.componentRef.setInput('participation', participation);
        fixture.componentRef.setInput('result', ratedResult);
        fixture.componentRef.setInput('showCompletion', true);
        fixture.componentRef.setInput('isInSidebarCard', false);
        fixture.detectChanges();

        expect(component.templateStatus()).toBe(ResultTemplateStatus.HAS_RESULT);
        const resultScore = fixture.debugElement.nativeElement.querySelector('#result-score');
        expect(resultScore).not.toBeNull();
        expect(resultScore.classList.contains('clickable-result')).toBe(true);
    });

    // Exhaustive rendering coverage: for every ResultTemplateStatus the presentational component can compute, drive
    // the inputs that produce it and assert the corresponding DOM branch renders. This guards every visible state of
    // this critical, widely-reused component against template/zoneless regressions.
    describe('renders every template status', () => {
        const query = (selector: string) => fixture.debugElement.nativeElement.querySelector(selector);

        it('IS_QUEUED → queued indicator', () => {
            fixture.componentRef.setInput('participation', programmingParticipation);
            fixture.componentRef.setInput('isQueued', true);
            fixture.detectChanges();
            expect(component.templateStatus()).toBe(ResultTemplateStatus.IS_QUEUED);
            expect(query('#test-queued')).not.toBeNull();
        });

        it('IS_QUEUED with progress bar → progress bar component', () => {
            fixture.componentRef.setInput('participation', programmingParticipation);
            fixture.componentRef.setInput('isQueued', true);
            fixture.componentRef.setInput('showProgressBar', true);
            fixture.detectChanges();
            expect(component.templateStatus()).toBe(ResultTemplateStatus.IS_QUEUED);
            expect(query('jhi-result-progress-bar')).not.toBeNull();
        });

        it('IS_BUILDING → building indicator', () => {
            fixture.componentRef.setInput('participation', programmingParticipation);
            fixture.componentRef.setInput('isBuilding', true);
            fixture.detectChanges();
            expect(component.templateStatus()).toBe(ResultTemplateStatus.IS_BUILDING);
            expect(query('#test-building')).not.toBeNull();
        });

        it('IS_BUILDING with progress bar → progress bar component', () => {
            fixture.componentRef.setInput('participation', programmingParticipation);
            fixture.componentRef.setInput('isBuilding', true);
            fixture.componentRef.setInput('showProgressBar', true);
            fixture.detectChanges();
            expect(component.templateStatus()).toBe(ResultTemplateStatus.IS_BUILDING);
            expect(query('jhi-result-progress-bar')).not.toBeNull();
        });

        it('IS_GENERATING_FEEDBACK (Athena being processed) → generating indicator', () => {
            const result: Result = { id: 1, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: undefined, completionDate: dayjs().add(1, 'hour') };
            fixture.componentRef.setInput('participation', programmingParticipation);
            fixture.componentRef.setInput('result', result);
            fixture.detectChanges();
            expect(component.templateStatus()).toBe(ResultTemplateStatus.IS_GENERATING_FEEDBACK);
            expect(query('#preliminary-feedback-generating')).not.toBeNull();
        });

        it('FEEDBACK_GENERATION_FAILED (Athena failed) → score still rendered', () => {
            const result: Result = { id: 1, score: 50, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: false };
            fixture.componentRef.setInput('participation', programmingParticipation);
            fixture.componentRef.setInput('result', result);
            fixture.detectChanges();
            expect(component.templateStatus()).toBe(ResultTemplateStatus.FEEDBACK_GENERATION_FAILED);
            expect(query('#result-score')).not.toBeNull();
        });

        it('FEEDBACK_GENERATION_TIMED_OUT (Athena timed out) → score still rendered', () => {
            const result: Result = { id: 1, score: 50, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: undefined, completionDate: dayjs().subtract(1, 'hour') };
            fixture.componentRef.setInput('participation', programmingParticipation);
            fixture.componentRef.setInput('result', result);
            fixture.detectChanges();
            expect(component.templateStatus()).toBe(ResultTemplateStatus.FEEDBACK_GENERATION_TIMED_OUT);
            expect(query('#result-score')).not.toBeNull();
        });

        it('MISSING (failed programming submission) → missing message', () => {
            fixture.componentRef.setInput('participation', programmingParticipation);
            fixture.componentRef.setInput('missingResultInfo', MissingResultInformation.FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE);
            fixture.detectChanges();
            expect(component.templateStatus()).toBe(ResultTemplateStatus.MISSING);
            expect(query('.text-danger')).not.toBeNull();
        });

        it('NO_RESULT → renders the no-result text and no score', () => {
            fixture.componentRef.setInput('participation', programmingParticipation);
            fixture.detectChanges();
            expect(component.templateStatus()).toBe(ResultTemplateStatus.NO_RESULT);
            expect(query('#result-score')).toBeNull();
            expect(query('.text-body-secondary')).not.toBeNull();
        });

        it('NO_RESULT → still rendered as no-result (no score) when ungraded results are shown', () => {
            fixture.componentRef.setInput('participation', programmingParticipation);
            fixture.componentRef.setInput('showUngradedResults', true);
            fixture.detectChanges();
            expect(component.templateStatus()).toBe(ResultTemplateStatus.NO_RESULT);
            expect(query('#result-score')).toBeNull();
            expect(query('.text-body-secondary')).not.toBeNull();
        });

        it('HAS_RESULT with a rated result and showBadge → graded badge', () => {
            const submission: Submission = { id: 1, participation: programmingParticipation };
            const result: Result = { id: 1, submission, score: 100, rated: true, successful: true, completionDate: dayjs().subtract(1, 'minute') };
            submission.results = [result];
            const participation = cloneDeep(programmingParticipation);
            participation.submissions = [submission];
            fixture.componentRef.setInput('participation', participation);
            fixture.componentRef.setInput('result', result);
            fixture.componentRef.setInput('showBadge', true);
            fixture.detectChanges();
            expect(component.templateStatus()).toBe(ResultTemplateStatus.HAS_RESULT);
            expect(query('#result-score')).not.toBeNull();
            expect(query('#result-score-badge')).not.toBeNull();
        });

        it('demotes a non-displayable SUBMITTED status to NO_RESULT (component only renders LATE, MISSING, or a displayable result)', () => {
            // evaluateTemplateStatus computes SUBMITTED here, but with no displayable result the component renders the
            // generic "no (graded) result" instead — the "Submitted" wording comes from jhi-submission-result-status.
            const exercise = cloneDeep(textExercise);
            exercise.dueDate = dayjs().add(1, 'day');
            const submission: Submission = { id: 1, submissionDate: dayjs().subtract(1, 'hour') };
            const participation = cloneDeep(textParticipation);
            participation.exercise = exercise;
            participation.submissions = [submission];
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('participation', participation);
            fixture.detectChanges();
            expect(component.templateStatus()).toBe(ResultTemplateStatus.NO_RESULT);
            expect(query('#test-submitted')).toBeNull();
        });

        it('SUBMITTED_WAITING_FOR_GRADING (text, manual result, assessment period active) → waiting text', () => {
            const exercise = cloneDeep(textExercise);
            exercise.dueDate = dayjs().add(1, 'day');
            exercise.assessmentDueDate = dayjs().add(2, 'day');
            const submission: Submission = { id: 1, submissionDate: dayjs().subtract(1, 'hour') };
            const result: Result = { id: 1, submission, score: 80, assessmentType: AssessmentType.MANUAL };
            submission.results = [result];
            const participation = cloneDeep(textParticipation);
            participation.exercise = exercise;
            participation.submissions = [submission];
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('participation', participation);
            fixture.componentRef.setInput('result', result);
            fixture.detectChanges();
            expect(component.templateStatus()).toBe(ResultTemplateStatus.SUBMITTED_WAITING_FOR_GRADING);
            expect(query('#test-submitted-waiting-grading')).not.toBeNull();
        });

        it('LATE (text, submitted after due date, has result) → late text', () => {
            const exercise = cloneDeep(textExercise);
            exercise.dueDate = dayjs().subtract(1, 'day');
            const submission: Submission = { id: 1, submissionDate: dayjs().subtract(2, 'hour') };
            const result: Result = { id: 1, submission, score: 80, assessmentType: AssessmentType.MANUAL };
            submission.results = [result];
            const participation = cloneDeep(textParticipation);
            participation.exercise = exercise;
            participation.submissions = [submission];
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('participation', participation);
            fixture.componentRef.setInput('result', result);
            fixture.detectChanges();
            expect(component.templateStatus()).toBe(ResultTemplateStatus.LATE);
            expect(query('#test-late')).not.toBeNull();
        });

        it('demotes a non-displayable LATE_NO_FEEDBACK status to NO_RESULT', () => {
            // evaluateTemplateStatus computes LATE_NO_FEEDBACK (late submission, no feedback); with no displayable
            // result the component renders the generic "no (graded) result".
            const exercise = cloneDeep(textExercise);
            exercise.dueDate = dayjs().subtract(1, 'day');
            const submission: Submission = { id: 1, submissionDate: dayjs().subtract(2, 'hour') };
            const participation = cloneDeep(textParticipation);
            participation.exercise = exercise;
            participation.submissions = [submission];
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('participation', participation);
            fixture.detectChanges();
            expect(component.templateStatus()).toBe(ResultTemplateStatus.NO_RESULT);
            expect(query('#test-late-no-feedback')).toBeNull();
        });
    });
});
