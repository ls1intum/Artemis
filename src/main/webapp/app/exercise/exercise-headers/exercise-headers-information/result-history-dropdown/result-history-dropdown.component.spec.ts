import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateModule } from '@ngx-translate/core';
import { ResultHistoryDropdownComponent } from './result-history-dropdown.component';
import { MockProvider } from 'ng-mocks';
import { Badge, ResultService } from 'app/exercise/result/result.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';

describe('ResultHistoryDropdownComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ResultHistoryDropdownComponent;
    let fixture: ComponentFixture<ResultHistoryDropdownComponent>;
    let mockRouter: MockRouter;

    const defaultExercise: Exercise = { id: 1, type: ExerciseType.PROGRAMMING, course: { id: 1 } } as Exercise;

    const createResult = (id: number, score: number, submission?: Partial<Submission>): Result => {
        const participation: Participation = { id: 1 } as Participation;
        const sub = { id: id, participation, ...submission } as Submission;
        return { id, score, submission: sub, completionDate: undefined } as unknown as Result;
    };

    beforeEach(async () => {
        mockRouter = new MockRouter();

        await TestBed.configureTestingModule({
            imports: [ResultHistoryDropdownComponent, TranslateModule.forRoot()],
            providers: [
                MockProvider(ResultService),
                MockProvider(ExerciseService),
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: Router, useValue: mockRouter },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
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
            fixture.componentRef.setInput('sortedHistoryResults', [result]);
            fixture.detectChanges();

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

        it('should return progress message when score increased', () => {
            const result1 = createResult(1, 50);
            const result2 = createResult(2, 75);
            fixture.componentRef.setInput('sortedHistoryResults', [result1, result2]);
            fixture.detectChanges();

            expect(component.getResultFeedbackMessage(result2)).toBe('artemisApp.result.progressString.niceProgress');
        });

        it('should return score dropped message when score decreased', () => {
            const result1 = createResult(1, 75);
            const result2 = createResult(2, 50);
            fixture.componentRef.setInput('sortedHistoryResults', [result1, result2]);
            fixture.detectChanges();

            expect(component.getResultFeedbackMessage(result2)).toBe('artemisApp.result.progressString.scoreDrop');
        });

        it('should return stuck message when score stayed the same', () => {
            const result1 = createResult(1, 50);
            const result2 = createResult(2, 50);
            fixture.componentRef.setInput('sortedHistoryResults', [result1, result2]);
            fixture.detectChanges();

            expect(component.getResultFeedbackMessage(result2)).toBe('artemisApp.result.progressString.stuck');
        });

        it('should prioritize build failed over score 100', () => {
            const participation: Participation = { id: 1, type: 'student' } as unknown as Participation;
            const programmingSub = { buildFailed: true, participation } as unknown as ProgrammingSubmission;
            const result = { id: 1, score: 100, submission: programmingSub } as unknown as Result;
            fixture.componentRef.setInput('sortedHistoryResults', [result]);
            fixture.detectChanges();

            expect(component.getResultFeedbackMessage(result)).toBe('artemisApp.result.progressString.buildFailed');
        });
    });

    describe('getResultColorClass', () => {
        it('should return text-secondary when no participation on submission', () => {
            const result = { id: 1, score: 50, submission: { id: 1 } } as unknown as Result;
            expect(component.getResultColorClass(result)).toBe('text-secondary');
        });
    });

    describe('getResultIcon', () => {
        it('should return faQuestionCircle when no participation', () => {
            const result = { id: 1, score: 50, submission: { id: 1 } } as unknown as Result;
            const icon = component.getResultIcon(result);
            expect(icon).toBeTruthy();
        });
    });

    describe('getResultText', () => {
        it('should return empty string when no participation', () => {
            const result = { id: 1, score: 50, submission: { id: 1 } } as unknown as Result;
            expect(component.getResultText(result)).toBe('');
        });
    });

    describe('getBadgeSeverity', () => {
        it('should return success for bg-success class', () => {
            const result = createResult(1, 100);
            vi.spyOn(ResultService, 'evaluateBadge').mockReturnValue({ class: 'bg-success', text: 'graded', tooltip: '' } as Badge);

            expect(component.getBadgeSeverity(result)).toBe('success');
        });

        it('should return info for bg-info class', () => {
            const result = createResult(1, 50);
            vi.spyOn(ResultService, 'evaluateBadge').mockReturnValue({ class: 'bg-info', text: 'practice', tooltip: '' } as Badge);

            expect(component.getBadgeSeverity(result)).toBe('info');
        });

        it('should return secondary for bg-secondary class', () => {
            const result = createResult(1, 50);
            vi.spyOn(ResultService, 'evaluateBadge').mockReturnValue({ class: 'bg-secondary', text: 'ungraded', tooltip: '' } as Badge);

            expect(component.getBadgeSeverity(result)).toBe('secondary');
        });

        it('should return undefined for unknown badge class', () => {
            const result = createResult(1, 50);
            vi.spyOn(ResultService, 'evaluateBadge').mockReturnValue({ class: 'bg-warning', text: 'other', tooltip: '' } as Badge);

            expect(component.getBadgeSeverity(result)).toBeUndefined();
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
            const modalService = TestBed.inject(NgbModal);
            const openSpy = vi.spyOn(modalService, 'open');

            const result = { id: 1, submission: { id: 1 } } as unknown as Result;
            const event = new Event('click');

            component.showFeedback(result, event);

            expect(openSpy).not.toHaveBeenCalled();
        });

        it('should open feedback modal when result has participation', () => {
            const modalService = TestBed.inject(NgbModal);
            const openSpy = vi.spyOn(modalService, 'open');

            const participation: Participation = { id: 1 } as Participation;
            const result = { id: 1, score: 80, submission: { id: 1, participation } } as unknown as Result;
            const event = new Event('click');
            vi.spyOn(event, 'stopPropagation');

            component.showFeedback(result, event);

            expect(event.stopPropagation).toHaveBeenCalled();
            expect(openSpy).toHaveBeenCalled();
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
    });
});
