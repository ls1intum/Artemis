import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { User } from 'app/account/user/user.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { faCheckCircle, faKeyboard } from '@fortawesome/free-solid-svg-icons';
import { ExamResultSummaryExerciseCardHeaderComponent } from 'app/exam/overview/summary/exercises/header/exam-result-summary-exercise-card-header.component';
import { ResultSummaryExerciseInfo } from 'app/exam/overview/summary/exam-result-summary.component';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

let fixture: ComponentFixture<ExamResultSummaryExerciseCardHeaderComponent>;
let component: ExamResultSummaryExerciseCardHeaderComponent;

const user = { id: 1, name: 'Test User' } as User;

const exam = {
    id: 1,
    title: 'ExamForTesting',
} as Exam;

const exerciseGroup = {
    exam,
    title: 'exercise group',
} as ExerciseGroup;

const programmingSubmission = { id: 1 } as ProgrammingSubmission;

const programmingParticipation = { id: 4, student: user, submissions: [programmingSubmission] } as StudentParticipation;

const programmingExercise = {
    id: 4,
    type: ExerciseType.PROGRAMMING,
    studentParticipations: [programmingParticipation],
    exerciseGroup,
} as ProgrammingExercise;

describe('ExamResultSummaryExerciseCardHeaderComponent', () => {
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamResultSummaryExerciseCardHeaderComponent],
        })
            .overrideComponent(ExamResultSummaryExerciseCardHeaderComponent, {
                set: {
                    imports: [MockComponent(FaIconComponent), MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe, (key: string) => key)],
                },
            })
            .compileComponents();
        fixture = TestBed.createComponent(ExamResultSummaryExerciseCardHeaderComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('index', 3);
        fixture.componentRef.setInput('exercise', programmingExercise);
        fixture.componentRef.setInput('exerciseInfo', { isCollapsed: false } as ResultSummaryExerciseInfo);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should show exercise group title', () => {
        fixture.detectChanges();

        const exerciseTitleElement: HTMLElement = fixture.nativeElement.querySelector('#exercise-group-title-' + programmingExercise.id);
        expect(exerciseTitleElement.textContent).toContain('#' + (component.index() + 1));
        expect(exerciseTitleElement.textContent).toContain(programmingExercise.exerciseGroup?.title);
    });

    describe('layout', () => {
        const title = () => fixture.nativeElement.querySelector('#exercise-group-title-' + programmingExercise.id) as HTMLElement;

        it('should use a compact semibold title without spacer characters', () => {
            fixture.detectChanges();

            expect(title().tagName).toBe('H5');
            expect(title().classList).toContain('m-0!');
            expect(title().classList).toContain('text-base!');
            expect(title().classList).toContain('font-semibold!');
            expect(title().innerHTML).not.toContain('&nbsp;');
            expect(title().textContent).not.toContain('\u00a0');
        });

        it('should render the number, the icon and the title as separate parts so that the title can truncate', () => {
            fixture.componentRef.setInput('exerciseInfo', {
                icon: faKeyboard,
                isCollapsed: false,
                displayExampleSolution: false,
                releaseTestsWithExampleSolution: false,
            } as ResultSummaryExerciseInfo);
            fixture.detectChanges();

            const icon = title().querySelector('fa-icon') as HTMLElement;
            expect(icon).not.toBeNull();
            // a fixed width keeps the titles of all cards aligned whatever the icon is
            const iconInstance = fixture.debugElement.query(By.directive(FaIconComponent)).componentInstance as FaIconComponent;
            expect(iconInstance.fixedWidth()).toBe(true);
            const titleText = title().querySelector('span.truncate') as HTMLElement;
            expect(titleText.textContent?.trim()).toBe(programmingExercise.exerciseGroup!.title);
            expect(titleText.classList).toContain('min-w-0');
            expect(title().classList).toContain('min-w-0');
            expect(title().querySelector('span.shrink-0')?.textContent?.trim()).toBe('#4');
        });

        it('should not reserve space for an icon when the exercise has none', () => {
            fixture.componentRef.setInput('exerciseInfo', { isCollapsed: false } as ResultSummaryExerciseInfo);
            fixture.detectChanges();

            expect(title().querySelector('fa-icon')).toBeNull();
        });

        it('should keep the points and the percentage intact when the title shrinks', () => {
            fixture.componentRef.setInput('resultsPublished', true);
            fixture.componentRef.setInput('exercise', { ...programmingExercise, maxPoints: 10 });
            fixture.componentRef.setInput('exerciseInfo', {
                icon: faKeyboard,
                isCollapsed: false,
                displayExampleSolution: false,
                releaseTestsWithExampleSolution: false,
                achievedPoints: 5,
                achievedPercentage: 50,
                colorClass: 'result-orange',
                resultIconClass: faCheckCircle,
            } as ResultSummaryExerciseInfo);
            fixture.detectChanges();

            const score = fixture.nativeElement.querySelector('[data-testid="exercise-result-score"]') as HTMLElement;
            expect(score.classList).toContain('shrink-0');
            expect(score.textContent).toContain('[5 / 10');
            expect(fixture.nativeElement.querySelector('[data-testid="achieved-percentage"]').textContent.trim()).toBe('50%');
        });

        it('should show only the maximum points before the results are published', () => {
            fixture.componentRef.setInput('resultsPublished', false);
            fixture.detectChanges();

            const score = fixture.nativeElement.querySelector('[data-testid="exercise-result-score"]') as HTMLElement;
            expect(score.textContent).toContain('artemisApp.examParticipation.exercisePoints');
            expect(fixture.nativeElement.querySelector('[data-testid="achieved-percentage"]')).toBeNull();
        });
    });
});
