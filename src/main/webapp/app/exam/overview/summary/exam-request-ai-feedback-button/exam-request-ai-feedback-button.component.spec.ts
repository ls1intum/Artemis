import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { BehaviorSubject, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';

import { ExamRequestAiFeedbackButtonComponent } from 'app/exam/overview/summary/exam-request-ai-feedback-button/exam-request-ai-feedback-button.component';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AccountService } from 'app/core/auth/account.service';
import { UserService } from 'app/core/user/shared/user.service';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { User } from 'app/core/user/user.model';

import { MockExamParticipationService } from 'test/helpers/mocks/service/mock-exam-participation.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExamRequestAiFeedbackButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExamRequestAiFeedbackButtonComponent>;
    let component: ExamRequestAiFeedbackButtonComponent;
    let examParticipationService: ExamParticipationService;

    const user = { id: 1, name: 'Test User' } as User;
    const course = { id: 1, accuracyOfScores: 2 } as Course;

    const exam = { id: 1, title: 'ExamForTesting', testExam: false, course } as Exam;
    const testExam = { id: 2, title: 'TestExam for Testing', testExam: true, course } as Exam;
    const exerciseGroup = { exam, title: 'exercise group' } as ExerciseGroup;

    const textSubmission = { id: 1, submitted: true } as TextSubmission;
    const textParticipation = { id: 1, student: user, submissions: [textSubmission] } as StudentParticipation;
    const textExercise = {
        id: 1,
        type: ExerciseType.TEXT,
        studentParticipations: [textParticipation],
        exerciseGroup,
        feedbackSuggestionModule: 'module_text_test',
    } as TextExercise;

    const studentExam = { id: 1, exam, user, exercises: [textExercise] } as StudentExam;
    const studentExamForTestExam = { id: 2, exam: testExam, user, exercises: [textExercise] } as StudentExam;

    const feedbackRequestedKey = `artemis_exam_ai_feedback_requested_${studentExamForTestExam.id}`;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamRequestAiFeedbackButtonComponent],
            providers: [
                { provide: ExamParticipationService, useClass: MockExamParticipationService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: UserService, useValue: { updateLLMSelectionDecision: vi.fn().mockReturnValue(of(undefined)) } },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamRequestAiFeedbackButtonComponent);
        component = fixture.componentInstance;
        examParticipationService = TestBed.inject(ExamParticipationService);

        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.componentRef.setInput('testExamConduction', false);
    });

    afterEach(() => {
        localStorage.removeItem(feedbackRequestedKey);
        vi.restoreAllMocks();
    });

    function setStudentExam(exam: StudentExam): void {
        fixture.componentRef.setInput('studentExam', exam);
    }

    function enableAthena(): void {
        const profileService = TestBed.inject(ProfileService);
        vi.spyOn(profileService, 'isProfileActive').mockReturnValue(true);
    }

    function acceptLLMUsage(): void {
        const accountService = TestBed.inject(AccountService);
        vi.spyOn(accountService, 'userIdentity').mockReturnValue({ selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as any);
    }

    describe('button visibility and behavior', () => {
        it('should show the button for a submitted test exam when Athena is active', () => {
            enableAthena();
            acceptLLMUsage();

            setStudentExam({ ...studentExamForTestExam, submitted: true });
            component.ngOnInit();
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('#requestAIFeedbackButton'));
            expect(button).not.toBeNull();
        });

        it('should hide the button for a real exam', () => {
            enableAthena();

            setStudentExam({ ...studentExam, submitted: true });
            component.ngOnInit();
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('#requestAIFeedbackButton'));
            expect(button).toBeNull();
        });

        it('should hide the button when no exercise has a feedback suggestion module configured', () => {
            enableAthena();

            const exerciseWithoutModule = {
                id: 1,
                type: ExerciseType.TEXT,
                studentParticipations: [textParticipation],
                exerciseGroup,
            } as TextExercise;

            setStudentExam({ ...studentExamForTestExam, submitted: true, exercises: [exerciseWithoutModule] });
            component.ngOnInit();
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('#requestAIFeedbackButton'));
            expect(button).toBeNull();
        });

        it('should hide the button when Athena is not active', () => {
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'isProfileActive').mockReturnValue(false);

            setStudentExam({ ...studentExamForTestExam, submitted: true });
            component.ngOnInit();
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('#requestAIFeedbackButton'));
            expect(button).toBeNull();
        });

        it('should disable the button while feedback is being requested', () => {
            enableAthena();
            acceptLLMUsage();

            setStudentExam({ ...studentExamForTestExam, submitted: true });
            component.ngOnInit();
            component.isRequestingFeedback.set(true);
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('#requestAIFeedbackButton'));
            expect(button.nativeElement.disabled).toBe(true);
        });

        it('should call examParticipationService.requestAthenaFeedback on click', () => {
            enableAthena();
            acceptLLMUsage();

            const requestSpy = vi.spyOn(examParticipationService, 'requestAthenaFeedback').mockReturnValue(of(undefined));

            setStudentExam({ ...studentExamForTestExam, submitted: true });
            component.ngOnInit();
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('#requestAIFeedbackButton'));
            button.nativeElement.click();

            expect(requestSpy).toHaveBeenCalledOnce();
            expect(component.feedbackRequested()).toBe(true);
            expect(component.isRequestingFeedback()).toBe(false);
            expect(localStorage.getItem(feedbackRequestedKey)).toBe('true');
        });

        it('should start with the button disabled when local storage says feedback was already requested', () => {
            localStorage.setItem(feedbackRequestedKey, 'true');

            enableAthena();
            acceptLLMUsage();

            setStudentExam({ ...studentExamForTestExam, submitted: true });
            component.ngOnInit();
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('#requestAIFeedbackButton'));
            expect(component.feedbackRequested()).toBe(true);
            expect(button.nativeElement.disabled).toBe(true);
        });

        it('should not persist to local storage when the feedback request fails', () => {
            enableAthena();
            acceptLLMUsage();

            vi.spyOn(examParticipationService, 'requestAthenaFeedback').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));

            setStudentExam({ ...studentExamForTestExam, submitted: true });
            component.ngOnInit();
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('#requestAIFeedbackButton'));
            button.nativeElement.click();

            expect(localStorage.getItem(feedbackRequestedKey)).toBeNull();
        });

        it('should handle error when feedback request fails', () => {
            enableAthena();
            acceptLLMUsage();

            vi.spyOn(examParticipationService, 'requestAthenaFeedback').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));

            setStudentExam({ ...studentExamForTestExam, submitted: true });
            component.ngOnInit();
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('#requestAIFeedbackButton'));
            button.nativeElement.click();

            expect(component.feedbackRequested()).toBe(false);
            expect(component.isRequestingFeedback()).toBe(false);
        });
    });

    describe('loadAthenaFeedbackUsage', () => {
        const athenaUsage = { used: 3, limit: 10 };

        it('should fetch usage and populate used/limit for a submitted test exam', () => {
            enableAthena();
            const usageSpy = vi.spyOn(examParticipationService, 'getAthenaFeedbackUsage').mockReturnValue(of(athenaUsage));

            setStudentExam({ ...studentExamForTestExam, submitted: true });
            component.ngOnInit();

            expect(usageSpy).toHaveBeenCalledOnce();
            expect(usageSpy).toHaveBeenCalledWith(1, studentExamForTestExam.exam!.id, studentExamForTestExam.id);
            expect(component.athenaFeedbackUsed()).toBe(3);
            expect(component.athenaFeedbackLimit()).toBe(10);
        });

        it('should treat used = 0 as a real value (not undefined) and keep the button enabled', () => {
            enableAthena();
            acceptLLMUsage();
            vi.spyOn(examParticipationService, 'getAthenaFeedbackUsage').mockReturnValue(of({ used: 0, limit: 10 }));

            setStudentExam({ ...studentExamForTestExam, submitted: true });
            component.ngOnInit();
            fixture.detectChanges();

            expect(component.athenaFeedbackUsed()).toBe(0);
            expect(component.athenaFeedbackUsed()).not.toBeUndefined();
            expect(component.athenaFeedbackLimit()).toBe(10);

            const button = fixture.debugElement.query(By.css('#requestAIFeedbackButton'));
            expect(button).not.toBeNull();
            expect(button.nativeElement.disabled).toBe(false);
        });

        it('should skip fetching usage for a real (non-test) exam', () => {
            enableAthena();
            const usageSpy = vi.spyOn(examParticipationService, 'getAthenaFeedbackUsage');

            setStudentExam({ ...studentExam, submitted: true });
            component.ngOnInit();

            expect(usageSpy).not.toHaveBeenCalled();
        });

        it('should skip fetching usage when Athena is not active', () => {
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'isProfileActive').mockReturnValue(false);
            const usageSpy = vi.spyOn(examParticipationService, 'getAthenaFeedbackUsage');

            setStudentExam({ ...studentExamForTestExam, submitted: true });
            component.ngOnInit();

            expect(usageSpy).not.toHaveBeenCalled();
        });

        it('should skip fetching usage for an unsubmitted test exam (still in conduction)', () => {
            enableAthena();
            const usageSpy = vi.spyOn(examParticipationService, 'getAthenaFeedbackUsage');

            setStudentExam({ ...studentExamForTestExam, submitted: false });
            component.ngOnInit();

            expect(usageSpy).not.toHaveBeenCalled();
        });

        it('should mark the current attempt as counted when it already has an Athena result', () => {
            enableAthena();
            vi.spyOn(examParticipationService, 'getAthenaFeedbackUsage').mockReturnValue(of(athenaUsage));

            const athenaTextSubmission = {
                id: 1,
                submitted: true,
                submissionDate: dayjs(),
                results: [{ assessmentType: AssessmentType.AUTOMATIC_ATHENA, rated: true } as Result],
            } as TextSubmission;
            const athenaTextParticipation = { id: 1, student: user, submissions: [athenaTextSubmission] } as StudentParticipation;
            const athenaTextExercise = {
                id: 1,
                type: ExerciseType.TEXT,
                studentParticipations: [athenaTextParticipation],
                exerciseGroup,
                feedbackSuggestionModule: 'module_text_test',
            } as TextExercise;

            setStudentExam({ ...studentExamForTestExam, submitted: true, exercises: [athenaTextExercise] });
            component.ngOnInit();

            // handleAthenaResult should now be a no-op for this attempt.
            (component as any).handleAthenaResult({ successful: true, completionDate: dayjs(), assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result);
            expect(component.athenaFeedbackUsed()).toBe(3);
        });
    });

    describe('handleAthenaResult', () => {
        function primeCounter(initial: number): void {
            component.athenaFeedbackUsed.set(initial);
            (component as any).currentAttemptCounted = false;
        }

        it('should increment used and mark attempt as counted for a successful Athena result', () => {
            primeCounter(1);

            (component as any).handleAthenaResult({ successful: true, completionDate: dayjs(), assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result);

            expect(component.athenaFeedbackUsed()).toBe(2);
            expect((component as any).currentAttemptCounted).toBe(true);
        });

        it('should not increment when the same attempt has already been counted', () => {
            component.athenaFeedbackUsed.set(1);
            (component as any).currentAttemptCounted = true;

            (component as any).handleAthenaResult({ successful: true, completionDate: dayjs(), assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result);

            expect(component.athenaFeedbackUsed()).toBe(1);
        });

        it('should not increment for an unsuccessful result', () => {
            primeCounter(1);

            (component as any).handleAthenaResult({ successful: false, completionDate: dayjs(), assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result);

            expect(component.athenaFeedbackUsed()).toBe(1);
            expect((component as any).currentAttemptCounted).toBe(false);
        });

        it('should not increment when completionDate is missing', () => {
            primeCounter(1);

            (component as any).handleAthenaResult({ successful: true, completionDate: undefined, assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result);

            expect(component.athenaFeedbackUsed()).toBe(1);
        });

        it('marks the exercise as resolved when the result is final, even on failure', () => {
            primeCounter(1);

            (component as any).handleAthenaResult({ successful: false, completionDate: undefined, assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result, 42);

            expect(component.receivedAthenaResultExerciseIds().has(42)).toBe(true);
            // Failure must not consume a usage slot.
            expect(component.athenaFeedbackUsed()).toBe(1);
            expect((component as any).currentAttemptCounted).toBe(false);
        });

        it('ignores in-progress broadcasts (successful=null/undefined)', () => {
            primeCounter(1);

            (component as any).handleAthenaResult({ successful: undefined, completionDate: undefined, assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result, 42);

            expect(component.receivedAthenaResultExerciseIds().has(42)).toBe(false);
            expect(component.athenaFeedbackUsed()).toBe(1);
        });
    });

    describe('eligibleExerciseIds (empty submission filter)', () => {
        it('excludes text exercises whose latest submission has no text', () => {
            enableAthena();
            vi.spyOn(examParticipationService, 'getAthenaFeedbackUsage').mockReturnValue(of({ used: 0, limit: 10 }));

            setStudentExam({ ...studentExamForTestExam, submitted: true, exercises: [textExercise] });
            component.ngOnInit();
            // Simulate a post-click state to exercise the spinner gate.
            component.feedbackRequested.set(true);

            // textSubmission in the shared fixture has no `text` field set → filtered out → no eligibles
            // → hasAllAthenaResultsForCurrentAttempt resolves to true (nothing to wait for) so the spinner
            // doesn't get stuck on a result the server will never emit.
            expect(component.hasAllAthenaResultsForCurrentAttempt()).toBe(true);
            expect(component.isGenerating()).toBe(false);
        });

        it('includes a text exercise once its latest submission has text', () => {
            enableAthena();
            vi.spyOn(examParticipationService, 'getAthenaFeedbackUsage').mockReturnValue(of({ used: 0, limit: 10 }));

            const filledSubmission = { id: 1, submitted: true, text: 'student answer' } as TextSubmission;
            const filledParticipation = { id: 1, student: user, submissions: [filledSubmission] } as StudentParticipation;
            const filledExercise = {
                id: 1,
                type: ExerciseType.TEXT,
                studentParticipations: [filledParticipation],
                exerciseGroup,
                feedbackSuggestionModule: 'module_text_test',
            } as TextExercise;

            setStudentExam({ ...studentExamForTestExam, submitted: true, exercises: [filledExercise] });
            component.ngOnInit();
            component.feedbackRequested.set(true);

            // One eligible exercise, no result yet → spinner active.
            expect(component.hasAllAthenaResultsForCurrentAttempt()).toBe(false);
            expect(component.isGenerating()).toBe(true);
        });
    });

    describe('hasAnyAthenaResultForCurrentAttempt', () => {
        it('should return false when no participations have Athena results', () => {
            setStudentExam(studentExamForTestExam);
            expect(component.hasAnyAthenaResultForCurrentAttempt).toBe(false);
        });

        it('should return true when a text participation has an Athena result on its latest submission', () => {
            const submissionWithAthena = {
                id: 10,
                submitted: true,
                submissionDate: dayjs(),
                results: [{ assessmentType: AssessmentType.AUTOMATIC_ATHENA, rated: true } as Result],
            } as TextSubmission;
            const participation = { id: 42, student: user, submissions: [submissionWithAthena] } as StudentParticipation;
            const exercise = { id: 99, type: ExerciseType.TEXT, studentParticipations: [participation], exerciseGroup } as TextExercise;

            setStudentExam({ ...studentExamForTestExam, exercises: [exercise] });
            expect(component.hasAnyAthenaResultForCurrentAttempt).toBe(true);
        });

        it('should ignore Athena results on quiz or programming exercises', () => {
            const quizSubmissionWithAthena = { id: 11, results: [{ assessmentType: AssessmentType.AUTOMATIC_ATHENA, rated: true } as Result] } as QuizSubmission;
            const quizParticipationWithAthena = { id: 42, student: user, submissions: [quizSubmissionWithAthena] } as StudentParticipation;
            const quizExerciseWithAthena = {
                id: 99,
                type: ExerciseType.QUIZ,
                studentParticipations: [quizParticipationWithAthena],
                exerciseGroup,
            } as QuizExercise;

            setStudentExam({ ...studentExamForTestExam, exercises: [quizExerciseWithAthena] });
            expect(component.hasAnyAthenaResultForCurrentAttempt).toBe(false);
        });
    });

    describe('isGenerating spinner state', () => {
        const nonEmptyTextSubmission = { id: 1, submitted: true, text: 'student answer' } as TextSubmission;
        const nonEmptyTextParticipation = { id: 1, student: user, submissions: [nonEmptyTextSubmission] } as StudentParticipation;
        const nonEmptyTextExercise = {
            id: 1,
            type: ExerciseType.TEXT,
            studentParticipations: [nonEmptyTextParticipation],
            exerciseGroup,
            feedbackSuggestionModule: 'module_text_test',
        } as TextExercise;
        const modelingSubmission = { id: 5, submitted: true, model: '{"version":"3.0.0","type":"ClassDiagram","elements":{"a":{}}}' } as ModelingSubmission;
        const modelingExercise = {
            id: 2,
            type: ExerciseType.MODELING,
            studentParticipations: [{ id: 2, student: user, submissions: [modelingSubmission] } as StudentParticipation],
            exerciseGroup,
            feedbackSuggestionModule: 'module_modeling_test',
        } as TextExercise;

        function configureMultiExercise(): BehaviorSubject<Result | undefined>[] {
            enableAthena();
            acceptLLMUsage();
            vi.spyOn(examParticipationService, 'getAthenaFeedbackUsage').mockReturnValue(of({ used: 0, limit: 10 }));
            vi.spyOn(examParticipationService, 'requestAthenaFeedback').mockReturnValue(of(undefined));

            const textSubject = new BehaviorSubject<Result | undefined>(undefined);
            const modelingSubject = new BehaviorSubject<Result | undefined>(undefined);
            const websocketService = TestBed.inject(ParticipationWebsocketService);
            vi.spyOn(websocketService, 'subscribeForLatestResultOfParticipation').mockImplementation((participationId: number) => {
                return participationId === nonEmptyTextParticipation.id! ? textSubject : modelingSubject;
            });

            setStudentExam({ ...studentExamForTestExam, submitted: true, exercises: [nonEmptyTextExercise, modelingExercise] });
            component.ngOnInit();
            return [textSubject, modelingSubject];
        }

        it('keeps spinning after the POST resolves while results are still pending', () => {
            const [textSubject] = configureMultiExercise();
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('#requestAIFeedbackButton'));
            button.nativeElement.click();
            fixture.detectChanges();

            // Synchronous mock observable already completed → isRequestingFeedback is false again.
            expect(component.isRequestingFeedback()).toBe(false);
            expect(component.feedbackRequested()).toBe(true);
            // Spinner stays on because no Athena results have arrived yet.
            expect(component.isGenerating()).toBe(true);
            expect(component.hasAllAthenaResultsForCurrentAttempt()).toBe(false);

            // Only one of two exercises has produced its Athena result so far.
            textSubject.next({ successful: true, completionDate: dayjs(), assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result);
            expect(component.isGenerating()).toBe(true);
            expect(component.hasAllAthenaResultsForCurrentAttempt()).toBe(false);
        });

        it('stops spinning once every eligible exercise has an Athena result', () => {
            const [textSubject, modelingSubject] = configureMultiExercise();
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('#requestAIFeedbackButton'));
            button.nativeElement.click();
            fixture.detectChanges();

            textSubject.next({ successful: true, completionDate: dayjs(), assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result);
            modelingSubject.next({ successful: true, completionDate: dayjs(), assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result);

            expect(component.hasAllAthenaResultsForCurrentAttempt()).toBe(true);
            expect(component.isGenerating()).toBe(false);
        });

        it('hydrates the received-set on init from already-present Athena results so no spinner runs', () => {
            enableAthena();
            vi.spyOn(examParticipationService, 'getAthenaFeedbackUsage').mockReturnValue(of({ used: 1, limit: 10 }));

            const submissionWithAthena = {
                id: 10,
                submitted: true,
                submissionDate: dayjs(),
                text: 'student answer',
                results: [{ assessmentType: AssessmentType.AUTOMATIC_ATHENA, rated: true } as Result],
            } as TextSubmission;
            const seededParticipation = { id: 1, student: user, submissions: [submissionWithAthena] } as StudentParticipation;
            const seededExercise = {
                id: 1,
                type: ExerciseType.TEXT,
                studentParticipations: [seededParticipation],
                exerciseGroup,
                feedbackSuggestionModule: 'module_text_test',
            } as TextExercise;

            localStorage.setItem(feedbackRequestedKey, 'true');
            setStudentExam({ ...studentExamForTestExam, submitted: true, exercises: [seededExercise] });
            component.ngOnInit();

            expect(component.feedbackRequested()).toBe(true);
            expect(component.hasAllAthenaResultsForCurrentAttempt()).toBe(true);
            expect(component.isGenerating()).toBe(false);
        });
    });

    describe('subscribeToAthenaResultsForCurrentAttempt via websocket', () => {
        it('should forward Athena websocket results to handleAthenaResult and increment usage once', () => {
            enableAthena();
            vi.spyOn(examParticipationService, 'getAthenaFeedbackUsage').mockReturnValue(of({ used: 0, limit: 10 }));

            const resultSubject = new BehaviorSubject<Result | undefined>(undefined);
            const websocketService = TestBed.inject(ParticipationWebsocketService);
            const subscribeSpy = vi.spyOn(websocketService, 'subscribeForLatestResultOfParticipation').mockReturnValue(resultSubject);

            setStudentExam({ ...studentExamForTestExam, submitted: true });
            component.ngOnInit();

            expect(subscribeSpy).toHaveBeenCalled();

            // The stream skips its initial value (per component's `skip(1)`), so only the push below should count.
            resultSubject.next({ successful: true, completionDate: dayjs(), assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result);

            expect(component.athenaFeedbackUsed()).toBe(1);

            // A second Athena result for the same attempt must not double-count.
            resultSubject.next({ successful: true, completionDate: dayjs(), assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result);
            expect(component.athenaFeedbackUsed()).toBe(1);
        });

        it('should unsubscribe websocket subscriptions on destroy', () => {
            enableAthena();
            vi.spyOn(examParticipationService, 'getAthenaFeedbackUsage').mockReturnValue(of({ used: 0, limit: 10 }));

            const websocketService = TestBed.inject(ParticipationWebsocketService);
            vi.spyOn(websocketService, 'subscribeForLatestResultOfParticipation').mockReturnValue(new BehaviorSubject<Result | undefined>(undefined));

            setStudentExam({ ...studentExamForTestExam, submitted: true });
            component.ngOnInit();

            const subscriptions: { unsubscribe: () => void }[] = (component as any).athenaResultSubscriptions;
            expect(subscriptions.length).toBeGreaterThan(0);
            const unsubscribeSpies = subscriptions.map((sub) => vi.spyOn(sub, 'unsubscribe'));

            component.ngOnDestroy();

            unsubscribeSpies.forEach((spy) => expect(spy).toHaveBeenCalled());
        });
    });
});
