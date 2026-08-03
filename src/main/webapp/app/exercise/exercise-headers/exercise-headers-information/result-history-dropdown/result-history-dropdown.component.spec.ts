import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import { ResultHistoryDropdownComponent } from './result-history-dropdown.component';
import { MockProvider } from 'ng-mocks';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';
import { ResultService } from 'app/exercise/result/result.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { DialogService } from 'primeng/dynamicdialog';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import dayjs from 'dayjs/esm';

describe('ResultHistoryDropdownComponent', () => {
    let component: ResultHistoryDropdownComponent;
    let fixture: ComponentFixture<ResultHistoryDropdownComponent>;
    let mockRouter: MockRouter;

    const defaultExercise: Exercise = { id: 1, type: ExerciseType.PROGRAMMING, course: { id: 1 } } as Exercise;

    const createResult = (id: number, score: number): Result => ({ id, score, submission: { id }, completionDate: undefined }) as unknown as Result;

    beforeEach(async () => {
        mockRouter = new MockRouter();

        await TestBed.configureTestingModule({
            imports: [ResultHistoryDropdownComponent],
            providers: [
                MockProvider(ResultService),
                MockProvider(ExerciseService),
                { provide: DialogService, useClass: MockDialogService },
                { provide: Router, useValue: mockRouter },
                provideHttpClient(),
                provideHttpClientTesting(),
                provideTranslateService(),
            ],
        })
            .compileComponents()
            .then(() => {
                const translateService = TestBed.inject(TranslateService);
                translateService.setTranslation('en', {
                    artemisApp: {
                        result: {
                            resultString: {
                                automaticAIFeedbackInProgress: 'AI feedback request is being processed',
                                automaticAIFeedbackSuccessfulTooltip: 'AI-based feedback can include mistakes. Consider checking important information.',
                                automaticAIFeedbackFailed: 'AI feedback generation failed.',
                                automaticAIFeedbackFailedTooltip: 'AI feedback generation failed.',
                                automaticAIFeedbackTimedOut: 'AI feedback generation timed out.',
                                automaticAIFeedbackInProgressTooltip: 'AI feedback is being generated.',
                            },
                        },
                    },
                });
                translateService.use('en');
                fixture = TestBed.createComponent(ResultHistoryDropdownComponent);
                component = fixture.componentInstance;
                fixture.componentRef.setInput('exercise', defaultExercise);
                fixture.componentRef.setInput('sortedHistoryResults', []);
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('displayedResults', () => {
        it('should return the sorted history results', () => {
            const results = [createResult(1, 50), createResult(2, 75), createResult(3, 100)];
            fixture.componentRef.setInput('sortedHistoryResults', results);
            fixture.detectChanges();

            const displayed = component.displayedResults();
            expect(displayed).toHaveLength(3);
            expect(displayed[0].id).toBe(1);
            expect(displayed[1].id).toBe(2);
            expect(displayed[2].id).toBe(3);
        });

        it('should return empty array when no results', () => {
            expect(component.displayedResults()).toHaveLength(0);
        });
    });

    describe('activeResultId', () => {
        it('should return undefined when no student participation', () => {
            expect(component.activeResultId()).toBeUndefined();
        });

        it('should return undefined when participation has no submissions', () => {
            fixture.componentRef.setInput('studentParticipation', { id: 1, submissions: [] } as StudentParticipation);
            fixture.detectChanges();

            expect(component.activeResultId()).toBeUndefined();
        });

        it('should return the highest result id from participation submissions', () => {
            const result1: Result = { id: 10 } as Result;
            const result2: Result = { id: 20 } as Result;
            fixture.componentRef.setInput('studentParticipation', {
                id: 1,
                submissions: [{ results: [result1] }, { results: [result2] }],
            } as StudentParticipation);
            fixture.detectChanges();

            expect(component.activeResultId()).toBe(20);
        });
    });

    describe('getResultFeedbackMessage', () => {
        it('should return build failed message when submission build failed', () => {
            const participation: Participation = { id: 1, type: 'student' } as unknown as Participation;
            const programmingSub = { buildFailed: true, participation } as unknown as ProgrammingSubmission;
            const result = { id: 1, score: 0, submission: programmingSub } as unknown as Result;

            expect(component.getResultFeedbackMessage(result)).toBe('artemisApp.result.progressString.buildFailed');
        });

        it('should return goal reached message for 100% score', () => {
            const result = createResult(1, 100);
            fixture.componentRef.setInput('sortedHistoryResults', [result]);
            fixture.detectChanges();

            expect(component.getResultFeedbackMessage(result)).toBe('artemisApp.result.progressString.goalReached');
        });

        it('should return progress message for first result (index 0)', () => {
            const result = createResult(1, 50);
            fixture.componentRef.setInput('sortedHistoryResults', [result]);
            fixture.detectChanges();

            expect(component.getResultFeedbackMessage(result)).toBe('artemisApp.result.progressString.niceProgress');
        });

        it('should return stuck message for first result with score 0', () => {
            const result = createResult(1, 0);
            fixture.componentRef.setInput('sortedHistoryResults', [result]);
            fixture.detectChanges();

            expect(component.getResultFeedbackMessage(result)).toBe('artemisApp.result.progressString.stuck');
        });

        it('should return progress message when score increased', () => {
            const result1 = createResult(1, 50);
            const result2 = createResult(2, 75);
            fixture.componentRef.setInput('sortedHistoryResults', [result2, result1]);
            fixture.detectChanges();

            expect(component.getResultFeedbackMessage(result2)).toBe('artemisApp.result.progressString.niceProgress');
        });

        it('should return score dropped message when score decreased', () => {
            const result1 = createResult(1, 75);
            const result2 = createResult(2, 50);
            fixture.componentRef.setInput('sortedHistoryResults', [result2, result1]);
            fixture.detectChanges();

            expect(component.getResultFeedbackMessage(result2)).toBe('artemisApp.result.progressString.scoreDrop');
        });

        it('should return stuck message when score stayed the same', () => {
            const result1 = createResult(1, 50);
            const result2 = createResult(2, 50);
            fixture.componentRef.setInput('sortedHistoryResults', [result2, result1]);
            fixture.detectChanges();

            expect(component.getResultFeedbackMessage(result2)).toBe('artemisApp.result.progressString.stuck');
        });

        it('should return progress message when previous result has no score and current score is positive', () => {
            const result1 = createResult(1, 0);
            result1.score = undefined;
            const result2 = createResult(2, 50);
            fixture.componentRef.setInput('sortedHistoryResults', [result2, result1]);
            fixture.detectChanges();

            expect(component.getResultFeedbackMessage(result2)).toBe('artemisApp.result.progressString.niceProgress');
        });

        it('should return stuck message when previous result has no score and current score is 0', () => {
            const result1 = createResult(1, 0);
            result1.score = undefined;
            const result2 = createResult(2, 0);
            fixture.componentRef.setInput('sortedHistoryResults', [result2, result1]);
            fixture.detectChanges();

            expect(component.getResultFeedbackMessage(result2)).toBe('artemisApp.result.progressString.stuck');
        });

        it('should prioritize build failed over score 100', () => {
            const participation: Participation = { id: 1, type: 'student' } as unknown as Participation;
            const programmingSub = { buildFailed: true, participation } as unknown as ProgrammingSubmission;
            const result = { id: 1, score: 100, submission: programmingSub } as unknown as Result;

            expect(component.getResultFeedbackMessage(result)).toBe('artemisApp.result.progressString.buildFailed');
        });

        it('should show in-progress AI feedback message instead of score progress for unfinished Athena results', () => {
            const result = { id: 1, score: 0, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: undefined } as Result;

            expect(component.getResultFeedbackMessage(result)).toBe('AI feedback request is being processed');
        });

        it('should show failed AI feedback message instead of score progress for failed Athena results', () => {
            const result = { id: 1, score: 0, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: false } as Result;

            expect(component.getResultFeedbackMessage(result)).toBe('AI feedback generation failed.');
        });

        it('should show timed out AI feedback message instead of score progress for timed out Athena results', () => {
            const result = {
                id: 1,
                score: 0,
                assessmentType: AssessmentType.AUTOMATIC_ATHENA,
                successful: undefined,
                completionDate: dayjs().subtract(5, 'minutes'),
            } as Result;

            expect(component.getResultFeedbackMessage(result)).toBe('AI feedback generation timed out.');
        });
    });

    describe('AI feedback indicator', () => {
        it('should render an accessible indicator for Athena results', () => {
            const result = createResult(1, 50);
            result.assessmentType = AssessmentType.AUTOMATIC_ATHENA;
            result.successful = true;
            fixture.componentRef.setInput('sortedHistoryResults', [result]);
            fixture.detectChanges();

            component.resultsPopover()?.show(new Event('click'));
            fixture.detectChanges();

            const indicator = document.querySelector<HTMLElement>('[data-testid="ai-feedback-indicator"]');
            expect(indicator).toBeTruthy();
            expect(indicator?.getAttribute('aria-label')).toBe('AI-based feedback can include mistakes. Consider checking important information.');
        });

        it('should use the failed tooltip for failed Athena results', () => {
            const result = createResult(1, 50);
            result.assessmentType = AssessmentType.AUTOMATIC_ATHENA;
            result.successful = false;
            fixture.componentRef.setInput('sortedHistoryResults', [result]);
            fixture.detectChanges();

            component.resultsPopover()?.show(new Event('click'));
            fixture.detectChanges();

            const indicator = document.querySelector<HTMLElement>('[data-testid="ai-feedback-indicator"]');
            expect(indicator?.getAttribute('aria-label')).toBe('AI feedback generation failed.');
        });

        it('should use the in-progress tooltip for Athena results still being generated', () => {
            const result = createResult(1, 50);
            result.assessmentType = AssessmentType.AUTOMATIC_ATHENA;
            result.successful = undefined;
            fixture.componentRef.setInput('sortedHistoryResults', [result]);
            fixture.detectChanges();

            component.resultsPopover()?.show(new Event('click'));
            fixture.detectChanges();

            const indicator = document.querySelector<HTMLElement>('[data-testid="ai-feedback-indicator"]');
            expect(indicator?.getAttribute('aria-label')).toBe('AI feedback is being generated.');
        });

        it('should not render an indicator for normal automatic results', () => {
            const result = createResult(1, 50);
            result.assessmentType = AssessmentType.AUTOMATIC;
            fixture.componentRef.setInput('sortedHistoryResults', [result]);
            fixture.detectChanges();

            component.resultsPopover()?.show(new Event('click'));
            fixture.detectChanges();

            expect(document.querySelector('[data-testid="ai-feedback-indicator"]')).toBeNull();
        });
    });

    describe('getResultColorClass', () => {
        it('should return text-muted-color when no participation on submission', () => {
            const result = { id: 1, score: 50, submission: { id: 1 } } as unknown as Result;
            expect(component.getResultColorClass(result)).toBe('text-muted-color');
        });
    });

    describe('getResultIcon', () => {
        it('should return faQuestionCircle when no participation', () => {
            const result = { id: 1, score: 50, submission: { id: 1 } } as unknown as Result;
            const icon = component.getResultIcon(result);
            expect(icon).toBeTruthy();
        });
    });

    describe('getResultIconAnimation', () => {
        it('should spin while Athena feedback is being generated', () => {
            const participation: Participation = { id: 1, exercise: defaultExercise } as Participation;
            const result = {
                id: 1,
                score: 50,
                assessmentType: AssessmentType.AUTOMATIC_ATHENA,
                successful: undefined,
                completionDate: dayjs().add(5, 'minutes'),
                submission: { id: 1, participation },
            } as unknown as Result;

            expect(component.getResultIconAnimation(result)).toBe('spin');
        });

        it('should not spin for completed Athena feedback', () => {
            const participation: Participation = { id: 1, exercise: defaultExercise } as Participation;
            const result = {
                id: 1,
                score: 50,
                assessmentType: AssessmentType.AUTOMATIC_ATHENA,
                successful: true,
                completionDate: dayjs().subtract(5, 'minutes'),
                submission: { id: 1, participation },
            } as unknown as Result;

            expect(component.getResultIconAnimation(result)).toBeUndefined();
        });

        it('should not spin for timed-out text Athena feedback', () => {
            const textExercise = { id: 1, type: ExerciseType.TEXT, dueDate: dayjs().add(1, 'day'), course: { id: 1 } } as Exercise;
            const participation: Participation = {
                id: 1,
                exercise: textExercise,
                submissions: [{ id: 1, submissionDate: dayjs().subtract(1, 'hour') }],
            } as Participation;
            const result = {
                id: 1,
                score: 0,
                assessmentType: AssessmentType.AUTOMATIC_ATHENA,
                successful: undefined,
                completionDate: dayjs().subtract(5, 'minutes'),
                submission: { id: 1, participation },
            } as unknown as Result;
            fixture.componentRef.setInput('exercise', textExercise);
            fixture.detectChanges();

            expect(component.getResultIconAnimation(result)).toBeUndefined();
        });

        it('should not spin when the result has no participation', () => {
            const result = { id: 1, score: 50, submission: { id: 1 } } as unknown as Result;

            expect(component.getResultIconAnimation(result)).toBeUndefined();
        });
    });

    describe('pending Athena feedback display', () => {
        it('should hide score and metadata for unfinished Athena results', () => {
            const result = { id: 1, score: 0, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: undefined } as Result;

            expect(component.shouldShowResultScore(result)).toBe(false);
            expect(component.shouldShowResultMetadata(result)).toBe(false);
        });

        it('should show score and metadata for completed Athena results', () => {
            const result = { id: 1, score: 75, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: true } as Result;

            expect(component.shouldShowResultScore(result)).toBe(true);
            expect(component.shouldShowResultMetadata(result)).toBe(true);
        });

        it('should hide score when a result has no score', () => {
            const result = { id: 1, score: undefined, assessmentType: AssessmentType.AUTOMATIC } as Result;

            expect(component.shouldShowResultScore(result)).toBe(false);
            expect(component.shouldShowResultMetadata(result)).toBe(true);
        });
    });

    describe('getResultText', () => {
        it('should return empty string when no participation', () => {
            const result = { id: 1, score: 50, submission: { id: 1 } } as unknown as Result;
            expect(component.getResultText(result)).toBe('');
        });
    });

    describe('isRowClickable', () => {
        it('should return true for TEXT exercises', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.TEXT, course: { id: 1 } } as Exercise);
            fixture.detectChanges();

            expect(component.isRowClickable()).toBe(true);
        });

        it('should return true for MODELING exercises', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.MODELING, course: { id: 1 } } as Exercise);
            fixture.detectChanges();

            expect(component.isRowClickable()).toBe(true);
        });

        it('should return true for QUIZ exercises', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.QUIZ, course: { id: 1 } } as Exercise);
            fixture.detectChanges();

            expect(component.isRowClickable()).toBe(true);
        });

        it('should return false for unfinished Athena feedback placeholders', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.TEXT, course: { id: 1 } } as Exercise);
            fixture.detectChanges();

            const result = { score: 0, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: undefined } as Result;

            expect(component.isRowClickable(result)).toBe(false);
        });

        it('should return true for persisted completed text results', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.TEXT, course: { id: 1 } } as Exercise);
            fixture.detectChanges();

            const result = { id: 1, score: 75, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: true } as Result;

            expect(component.isRowClickable(result)).toBe(true);
        });

        it('should return false for PROGRAMMING exercises', () => {
            expect(component.isRowClickable()).toBe(false);
        });

        it('should return false for FILE_UPLOAD exercises', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.FILE_UPLOAD, course: { id: 1 } } as Exercise);
            fixture.detectChanges();

            expect(component.isRowClickable()).toBe(false);
        });
    });

    describe('navigateToSubmission', () => {
        it('should prevent default page scrolling when activating a clickable row with space', () => {
            fixture.componentRef.setInput('exercise', { id: 10, type: ExerciseType.TEXT, course: { id: 5 } } as Exercise);
            fixture.detectChanges();

            const participation: Participation = { id: 2 } as Participation;
            const result = { id: 1, submission: { id: 7, participation } } as unknown as Result;
            const event = { preventDefault: vi.fn(), stopPropagation: vi.fn() } as unknown as KeyboardEvent;

            component.handleRowSpaceKeydown(result, event);

            expect(event.preventDefault).toHaveBeenCalledOnce();
            expect(event.stopPropagation).toHaveBeenCalledOnce();
            expect(mockRouter.navigate).toHaveBeenCalledWith(['/courses', 5, 'exercises', 'text-exercises', 10, 'participate', 2, 'submission', 7, 'result', 1]);
        });

        it('should not prevent default page scrolling when space is pressed on a non-clickable row', () => {
            const participation: Participation = { id: 2 } as Participation;
            const result = { id: 1, submission: { id: 7, participation } } as unknown as Result;
            const event = { preventDefault: vi.fn(), stopPropagation: vi.fn() } as unknown as KeyboardEvent;

            component.handleRowSpaceKeydown(result, event);

            expect(event.preventDefault).not.toHaveBeenCalled();
            expect(event.stopPropagation).not.toHaveBeenCalled();
            expect(mockRouter.navigate).not.toHaveBeenCalled();
        });

        it('should not navigate when result has no participation', () => {
            const result = { id: 1, submission: { id: 1 } } as unknown as Result;
            const event = new Event('click');

            component.navigateToSubmission(result, event);

            expect(mockRouter.navigate).not.toHaveBeenCalled();
        });

        it('should navigate to quiz live mode for graded participation', () => {
            fixture.componentRef.setInput('exercise', { id: 10, type: ExerciseType.QUIZ, course: { id: 5 } } as Exercise);
            fixture.detectChanges();

            const participation: StudentParticipation = { id: 2, testRun: false } as StudentParticipation;
            const result = { id: 1, submission: { id: 1, participation } } as unknown as Result;
            const event = new Event('click');
            vi.spyOn(event, 'stopPropagation');

            component.navigateToSubmission(result, event);

            expect(event.stopPropagation).toHaveBeenCalled();
            expect(mockRouter.navigate).toHaveBeenCalledWith(['/courses', 5, 'exercises', 'quiz-exercises', 10, 'live']);
        });

        it('should navigate to quiz practice mode for practice participation', () => {
            fixture.componentRef.setInput('exercise', { id: 10, type: ExerciseType.QUIZ, course: { id: 5 } } as Exercise);
            fixture.detectChanges();

            const participation: StudentParticipation = { id: 3, testRun: true } as StudentParticipation;
            const result = { id: 1, submission: { id: 7, participation } } as unknown as Result;
            const event = new Event('click');

            component.navigateToSubmission(result, event);

            expect(mockRouter.navigate).toHaveBeenCalledWith(['/courses', 5, 'exercises', 'quiz-exercises', 10, 'practice', 3, 'submission', 7]);
        });

        it('should navigate to text exercise submission', () => {
            fixture.componentRef.setInput('exercise', { id: 10, type: ExerciseType.TEXT, course: { id: 5 } } as Exercise);
            fixture.detectChanges();

            const participation: Participation = { id: 2 } as Participation;
            const result = { id: 1, submission: { id: 7, participation } } as unknown as Result;
            const event = new Event('click');

            component.navigateToSubmission(result, event);

            expect(mockRouter.navigate).toHaveBeenCalledWith(['/courses', 5, 'exercises', 'text-exercises', 10, 'participate', 2, 'submission', 7, 'result', 1]);
        });

        it('should navigate to modeling exercise submission', () => {
            fixture.componentRef.setInput('exercise', { id: 10, type: ExerciseType.MODELING, course: { id: 5 } } as Exercise);
            fixture.detectChanges();

            const participation: Participation = { id: 2 } as Participation;
            const result = { id: 1, submission: { id: 7, participation } } as unknown as Result;
            const event = new Event('click');

            component.navigateToSubmission(result, event);

            expect(mockRouter.navigate).toHaveBeenCalledWith(['/courses', 5, 'exercises', 'modeling-exercises', 10, 'participate', 2, 'submission', 7, 'result', 1]);
        });
    });

    describe('showFeedback', () => {
        it('should not open modal when result has no participation', () => {
            const dialogService = TestBed.inject(DialogService);
            const openSpy = vi.spyOn(dialogService, 'open');

            const result = { id: 1, submission: { id: 1 } } as unknown as Result;
            const event = new Event('click');

            component.showFeedback(result, event);

            expect(openSpy).not.toHaveBeenCalled();
        });

        it('should open feedback modal when result has participation', () => {
            const dialogService = TestBed.inject(DialogService);
            const openSpy = vi.spyOn(dialogService, 'open');

            const participation: Participation = { id: 1 } as Participation;
            const result = { id: 1, score: 80, submission: { id: 1, participation } } as unknown as Result;
            const event = new Event('click');
            vi.spyOn(event, 'stopPropagation');

            component.showFeedback(result, event);

            expect(event.stopPropagation).toHaveBeenCalled();
            expect(component.isViewingSubmission()).toBe(false);
            expect(openSpy).toHaveBeenCalledWith(
                FeedbackComponent,
                expect.objectContaining({
                    header: 'artemisApp.result.detail.feedback',
                    width: '80rem',
                    breakpoints: {
                        '1400px': '75vw',
                        '1200px': '85vw',
                        '992px': '95vw',
                    },
                    modal: true,
                    closable: true,
                    closeOnEscape: true,
                    dismissableMask: true,
                    focusOnShow: false,
                    inputValues: expect.objectContaining({ exercise: defaultExercise, result, participation }),
                }),
            );
        });
    });

    describe('template rendering', () => {
        it('should not render dropdown arrow when no results', () => {
            const compiled = fixture.nativeElement as HTMLElement;
            const arrow = compiled.querySelector('fa-icon');
            expect(arrow).toBeNull();
        });

        it('should render dropdown arrow when results exist', () => {
            fixture.componentRef.setInput('sortedHistoryResults', [createResult(1, 50)]);
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            const arrow = compiled.querySelector('fa-icon');
            expect(arrow).toBeTruthy();
        });

        it('should render unfinished Athena feedback as pending instead of a scored result', () => {
            const exercise = { id: 1, type: ExerciseType.TEXT, course: { id: 1 } } as Exercise;
            const participation = { id: 1, exercise } as Participation;
            const result = {
                score: 0,
                assessmentType: AssessmentType.AUTOMATIC_ATHENA,
                successful: undefined,
                submission: { id: 1, participation },
            } as unknown as Result;

            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('sortedHistoryResults', [result]);
            fixture.detectChanges();

            component.resultsPopover()?.show(new Event('click'));
            fixture.detectChanges();

            const row = document.querySelector<HTMLElement>('[data-testid="result-history-row"]');
            expect(row?.textContent).toContain('AI feedback request is being processed');
            expect(row?.textContent).not.toContain('0%');
            expect(row?.textContent).not.toContain('artemisApp.result.progressString.stuck');
            expect(row?.querySelector('p-tag')).toBeNull();
            expect(row?.getAttribute('role')).toBeNull();
        });
    });
});
