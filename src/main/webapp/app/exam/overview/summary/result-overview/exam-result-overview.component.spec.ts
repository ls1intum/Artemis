import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { User } from 'app/account/user/user.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamResultOverviewComponent } from 'app/exam/overview/summary/result-overview/exam-result-overview.component';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { BonusStrategy } from 'app/assessment/shared/entities/bonus.model';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { Course } from 'app/course/shared/entities/course.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExerciseResult, StudentExamWithGradeDTO } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { GradingKeyTableComponent } from 'app/assessment/manage/grading/grading-key/grading-key-table.component';
import { CollapsibleCardComponent } from 'app/exam/overview/summary/collapsible-card/collapsible-card.component';
import { NoDataComponent } from 'app/shared-ui/components/no-data/no-data-component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiTableDirective } from '@tumaet/ui-angular';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockDirective } from 'ng-mocks';

let fixture: ComponentFixture<ExamResultOverviewComponent>;
let component: ExamResultOverviewComponent;
let studentExamWithGrade: StudentExamWithGradeDTO;

const visibleDate = dayjs().subtract(7, 'hours');
const startDate = dayjs().subtract(6, 'hours');
const endDate = dayjs().subtract(5, 'hours');
const publishResultsDate = dayjs().subtract(3, 'hours');
const reviewStartDate = dayjs().subtract(2, 'hours');
const reviewEndDate = dayjs().add(1, 'hours');

const exam = {
    id: 1,
    title: 'Test Exam',
    visibleDate,
    startDate,
    endDate,
    publishResultsDate,
    reviewStartDate,
    reviewEndDate,
} as Exam;

const textResult = { id: 1, score: 200 } as Result;
const notIncludedTextResult = { id: 99, score: 100 } as Result;
const bonusTextResult = { id: 100, score: 100 } as Result;
const quizResult = { id: 2, score: 20 } as Result;
const modelingResult = { id: 3, score: 33.33 } as Result;
const programmingResult = { id: 4 } as Result;

const user = { id: 1, name: 'Test User' } as User;

const textParticipation = { id: 1, student: user, results: [textResult] } as StudentParticipation;
const notIncludedTextParticipation = { id: 99, student: user, results: [notIncludedTextResult] } as StudentParticipation;
const bonusTextParticipation = { id: 100, student: user, results: [bonusTextResult] } as StudentParticipation;
const quizParticipation = { id: 2, student: user, results: [quizResult] } as StudentParticipation;
const modelingParticipation = { id: 3, student: user, results: [modelingResult] } as StudentParticipation;
const programmingParticipation = { id: 4, student: user, results: [programmingResult] } as StudentParticipation;
const programmingParticipationTwo = { id: 5, student: user } as StudentParticipation;

const textExercise = {
    id: 1,
    includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
    title: 'Text Exercise',
    type: ExerciseType.TEXT,
    studentParticipations: [textParticipation],
    maxPoints: 10,
    bonusPoints: 10,
} as TextExercise;
const notIncludedTextExercise = {
    id: 99,
    includedInOverallScore: IncludedInOverallScore.NOT_INCLUDED,
    type: ExerciseType.TEXT,
    maxPoints: 10,
    studentParticipations: [notIncludedTextParticipation],
} as TextExercise;
const bonusTextExercise = {
    id: 100,
    includedInOverallScore: IncludedInOverallScore.INCLUDED_AS_BONUS,
    type: ExerciseType.TEXT,
    maxPoints: 10,
    studentParticipations: [bonusTextParticipation],
} as TextExercise;
const quizExercise = {
    id: 2,
    includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
    title: 'Quiz Exercise',
    type: ExerciseType.QUIZ,
    studentParticipations: [quizParticipation],
    maxPoints: 10,
} as QuizExercise;
const modelingExercise = {
    id: 3,
    includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
    title: 'Modeling Exercise',
    type: ExerciseType.MODELING,
    studentParticipations: [modelingParticipation],
    maxPoints: 10,
} as ModelingExercise;
const programmingExercise = {
    id: 4,
    includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
    title: 'Programming Exercise',
    type: ExerciseType.PROGRAMMING,
    studentParticipations: [programmingParticipation],
    maxPoints: 10,
} as ProgrammingExercise;
const programmingExerciseTwo = {
    id: 5,
    includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
    title: 'Programming Exercise',
    type: ExerciseType.PROGRAMMING,
    studentParticipations: [programmingParticipationTwo],
} as ProgrammingExercise;
const exercises = [textExercise, quizExercise, modelingExercise, programmingExercise, programmingExerciseTwo, notIncludedTextExercise, bonusTextExercise];

const textExerciseResult = {
    exerciseId: textExercise.id,
    achievedScore: 60,
    achievedPoints: 6,
    maxScore: textExercise.maxPoints,
} as ExerciseResult;

describe('ExamResultOverviewComponent', () => {
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamResultOverviewComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(ExamResultOverviewComponent, {
                set: {
                    imports: [
                        MockComponent(FaIconComponent),
                        MockPipe(ArtemisTranslatePipe, (key: string) => key),
                        MockDirective(TranslateDirective),
                        TumAetUiTableDirective,
                        MockComponent(NoDataComponent),
                        MockComponent(GradingKeyTableComponent),
                        MockComponent(CollapsibleCardComponent),
                    ],
                },
            })
            .compileComponents();
        studentExamWithGrade = {
            maxPoints: 40,
            maxBonusPoints: 20,
            studentExam: { exercises, exam, numberOfExamSessions: 0 },
            studentResult: {
                userId: 1,
                name: 'user1',
                login: 'user1',
                email: 'user1@tum.de',
                registrationNumber: '111',
                overallPointsAchieved: 35.33,
                overallScoreAchieved: (35.33 / 40) * 100,
                overallPointsAchievedInFirstCorrection: 45,
                overallGrade: '1.7',
                hasPassed: true,
                submitted: true,
                exerciseGroupIdToExerciseResult: {
                    [textExercise.id!]: textExerciseResult,
                },
            },
            achievedPointsPerExercise: {
                [programmingExerciseTwo.id!]: 0,
                [textExercise.id!]: 20,
                [notIncludedTextExercise.id!]: 10,
                [bonusTextExercise.id!]: 10,
                [quizExercise.id!]: 2,
                [modelingExercise.id!]: 3.33,
                [programmingExercise.id!]: 0,
            },
        };

        const course = new Course();
        course.id = 1;
        course.accuracyOfScores = 2;

        fixture = TestBed.createComponent(ExamResultOverviewComponent);
        component = fixture.componentInstance;
        exam.course = course;
        fixture.componentRef.setInput('studentExamWithGrade', studentExamWithGrade);
        fixture.componentRef.setInput('exerciseInfos', {});
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should handle error correctly', () => {
        component.studentExamWithGrade().studentExam = undefined as any;
        fixture.detectChanges();

        expect(fixture).not.toBeNull();
        expect(component.studentExamWithGrade().studentExam).toBeUndefined();
        expect(component.gradingScaleExists()).toBe(false);
    });

    it('should retrieve exam grade correctly', () => {
        fixture.detectChanges();

        expect(fixture).not.toBeNull();
        expect(component.gradingScaleExists()).toBe(true);
        expect(component.isBonus()).toBe(false);
        expect(component.grade()).toEqual(studentExamWithGrade.studentResult.overallGrade);
        expect(component.isBonus()).toEqual(studentExamWithGrade.gradeType === GradeType.BONUS);
        expect(component.hasPassed()).toEqual(studentExamWithGrade.studentResult.hasPassed);
    });

    describe('overallScoreTextColorClass', () => {
        function setResult(overrides: Partial<StudentExamWithGradeDTO['studentResult']>) {
            fixture.componentRef.setInput('studentExamWithGrade', {
                ...studentExamWithGrade,
                studentResult: { ...studentExamWithGrade.studentResult, ...overrides },
            });
            fixture.detectChanges();
        }

        it('should use the pass color when a grading scale exists and the student passed', () => {
            setResult({ overallGrade: '1.7', hasPassed: true });
            expect(component.gradingScaleExists()).toBe(true);
            expect(component.overallScoreTextColorClass()).toBe('text-state-success');
        });

        it('should use the fail color when a grading scale exists and the student did not pass', () => {
            setResult({ overallGrade: '5.0', hasPassed: false });
            expect(component.gradingScaleExists()).toBe(true);
            expect(component.overallScoreTextColorClass()).toBe('text-state-danger');
        });

        it('should color a full score green even without a grading scale', () => {
            setResult({ overallGrade: undefined, hasPassed: false, overallScoreAchieved: 100 });
            expect(component.gradingScaleExists()).toBe(false);
            expect(component.overallScoreTextColorClass()).toBe('text-state-success');
        });

        it('should color a mid score orange without a grading scale', () => {
            setResult({ overallGrade: undefined, hasPassed: false, overallScoreAchieved: 50 });
            expect(component.overallScoreTextColorClass()).toBe('result-orange');
        });

        it('should color a low score red without a grading scale', () => {
            setResult({ overallGrade: undefined, hasPassed: false, overallScoreAchieved: 30 });
            expect(component.overallScoreTextColorClass()).toBe('text-state-danger');
        });
    });

    it('should initialize and calculate scores correctly', () => {
        fixture.detectChanges();
        expect(fixture).not.toBeNull();

        expect(component.studentExamWithGrade()?.achievedPointsPerExercise?.[programmingExerciseTwo.id!]).toBe(0);
        expect(component.studentExamWithGrade()?.achievedPointsPerExercise?.[textExercise.id!]).toBe(20);
        expect(component.studentExamWithGrade()?.achievedPointsPerExercise?.[notIncludedTextExercise.id!]).toBe(10);
        expect(component.studentExamWithGrade()?.achievedPointsPerExercise?.[bonusTextExercise.id!]).toBe(10);
        expect(component.studentExamWithGrade()?.achievedPointsPerExercise?.[quizExercise.id!]).toBe(2);
        expect(component.studentExamWithGrade()?.achievedPointsPerExercise?.[modelingExercise.id!]).toBe(3.33);
        expect(component.studentExamWithGrade()?.achievedPointsPerExercise?.[programmingExercise.id!]).toBe(0);

        expect(component.overallAchievedPoints()).toBe(35.33);
        expect(component.maxPoints()).toBe(40);
        expect(component.studentExamWithGrade()?.maxBonusPoints).toBe(20);
        expect(component.getMaxNormalAndBonusPointsSum()).toBe(60);
    });

    it('should display 0 if no exercises are present', () => {
        component.studentExamWithGrade().studentExam!.exercises = [];
        component.studentExamWithGrade().maxPoints = 0;
        component.studentExamWithGrade().studentResult.overallPointsAchieved = 0;

        fixture.detectChanges();
        expect(fixture).not.toBeNull();

        expect(component.overallAchievedPoints()).toBe(0);
        expect(component.maxPoints()).toBe(0);
        expect(component.studentExamWithGrade()?.maxBonusPoints).toBe(20);
        expect(component.getMaxNormalAndBonusPointsSum()).toBe(20);
    });

    describe('should evaluate showIncludedInScoreColumn', () => {
        it('to false if all exercises are included in the score', () => {
            const onlyIncludedExercises = [textExercise, quizExercise, modelingExercise, programmingExercise];
            component.studentExamWithGrade().studentExam!.exercises = onlyIncludedExercises;

            expect(component.containsExerciseThatIsNotIncludedCompletely()).toBe(false);
        });

        it('to true if exercise is excluded', () => {
            const onlyIncludedExercises = [textExercise, quizExercise, modelingExercise, programmingExercise, notIncludedTextExercise];
            component.studentExamWithGrade().studentExam!.exercises = onlyIncludedExercises;

            expect(component.containsExerciseThatIsNotIncludedCompletely()).toBe(true);
        });

        it('to true if bonus exercise is included', () => {
            const onlyIncludedExercises = [textExercise, quizExercise, modelingExercise, programmingExercise, bonusTextExercise];
            component.studentExamWithGrade().studentExam!.exercises = onlyIncludedExercises;

            expect(component.containsExerciseThatIsNotIncludedCompletely()).toBe(true);
        });
    });

    describe('scrollToExercise', () => {
        it('should scroll to the target exercise dom element', () => {
            const mockElement = document.createElement('div');
            mockElement.id = 'exercise-1';
            document.body.appendChild(mockElement);
            mockElement.scrollIntoView = vi.fn();

            component.scrollToExercise(1);

            expect(mockElement.scrollIntoView).toHaveBeenCalledWith({
                behavior: 'smooth',
                block: 'start',
                inline: 'nearest',
            });
        });

        it('should log an error when the target exercise dom element does not exist', () => {
            const INVALID_EXERCISE_ID = 999;
            component.scrollToExercise(INVALID_EXERCISE_ID);
        });

        it('should return immediately when exerciseId is undefined', () => {
            component.scrollToExercise(undefined);
        });
    });

    describe('summedAchievedExerciseScorePercentage', () => {
        it('should be called when overallScoreAchieved is not defined in DTO from server', () => {
            //@ts-ignore spying on private method
            const summedAchievedExerciseScorePercentageSpy = vi.spyOn(component, 'summedAchievedExerciseScorePercentage');
            component.studentExamWithGrade().studentResult.overallScoreAchieved = undefined;
            fixture.componentRef.setInput('exerciseInfos', {});

            component.ngOnInit();

            expect(summedAchievedExerciseScorePercentageSpy).toHaveBeenCalledOnce();
        });

        it('should be called when overallScoreAchieved is 0 (default value, might be set as initial value because not defined from server DTO)', () => {
            //@ts-ignore spying on private method
            const summedAchievedExerciseScorePercentageSpy = vi.spyOn(component, 'summedAchievedExerciseScorePercentage');
            component.studentExamWithGrade().studentResult.overallScoreAchieved = 0;
            fixture.componentRef.setInput('exerciseInfos', {});

            component.ngOnInit();

            expect(summedAchievedExerciseScorePercentageSpy).toHaveBeenCalledOnce();
        });

        it('should calculate achieved percentage from exercise info properly', () => {
            //@ts-ignore spying on private method
            const summedAchievedExerciseScorePercentageSpy = vi.spyOn(component, 'summedAchievedExerciseScorePercentage');
            const exerciseInfosWithAchievedPercentage = {
                1: { achievedPercentage: 80 },
                2: { achievedPercentage: 60 },
                3: { achievedPercentage: 90 },
            };
            //@ts-ignore missing attributes
            fixture.componentRef.setInput('exerciseInfos', exerciseInfosWithAchievedPercentage);
            component.studentExamWithGrade().studentResult.overallScoreAchieved = undefined;

            component.ngOnInit();

            expect(summedAchievedExerciseScorePercentageSpy).toHaveBeenCalledOnce();
            expect(component.overallAchievedPercentageRoundedByCourseSettings()).toBe(76.67);
        });
    });

    describe('rowClass', () => {
        it('should dim and color exercises that are not included in the score', () => {
            const classes = component.rowClass({ includedInOverallScore: IncludedInOverallScore.NOT_INCLUDED } as Exercise);
            expect(classes).toContain('opacity-50');
            expect(classes).toContain('text-state-danger');
        });

        it('should color bonus exercises', () => {
            const classes = component.rowClass({ includedInOverallScore: IncludedInOverallScore.INCLUDED_AS_BONUS } as Exercise);
            expect(classes).toContain('text-state-warning');
            expect(classes).not.toContain('opacity-50');
        });

        it('should not style regular exercises', () => {
            expect(component.rowClass({ includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY } as Exercise)).toBe('');
        });
    });

    describe('rendering', () => {
        const textOf = (testId: string) => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`)?.textContent?.replace(/\s+/g, ' ').trim();
        const sentences = () => Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('[data-testid="exam-result-points-sentence"]'));

        /** Renders the overview for an exam with one published, assessed exercise. */
        function render(resultOverrides: Partial<StudentExamWithGradeDTO['studentResult']> = {}, dtoOverrides: Partial<StudentExamWithGradeDTO> = {}) {
            const submission = { id: 1, results: [{ id: 1, score: 50 } as Result] } as Submission;
            const assessedExercise = { ...textExercise, studentParticipations: [{ ...textParticipation, submissions: [submission] }] } as TextExercise;
            fixture.componentRef.setInput('studentExamWithGrade', {
                ...studentExamWithGrade,
                studentExam: { exercises: [assessedExercise], exam, numberOfExamSessions: 0 },
                ...dtoOverrides,
                studentResult: { ...studentExamWithGrade.studentResult, ...resultOverrides },
            });
            fixture.detectChanges();
        }

        describe('points and grade summary', () => {
            it('should show only the bonus sentence when the exam has bonus points', () => {
                render();

                expect(sentences()).toHaveLength(1);
                expect(sentences()[0].textContent?.trim()).toBe('artemisApp.exam.examSummary.points.youAchievedWithBonus');
            });

            it('should show the plain sentence when the exam has no bonus points', () => {
                render({}, { maxBonusPoints: 0 });

                expect(sentences()).toHaveLength(1);
                expect(sentences()[0].textContent?.trim()).toBe('artemisApp.exam.examSummary.points.youAchieved');
            });

            it('should show the plain sentence when the max bonus points are not set', () => {
                render({}, { maxBonusPoints: undefined });

                expect(sentences()).toHaveLength(1);
                expect(sentences()[0].textContent?.trim()).toBe('artemisApp.exam.examSummary.points.youAchieved');
            });

            it('should show the grade in a single semibold line when a grading scale exists', () => {
                render({ overallGrade: '1.7' });

                const grade = fixture.nativeElement.querySelector('[data-testid="exam-result-grade"]') as HTMLElement;
                expect(grade.querySelector('[jhiTranslate="artemisApp.exam.examSummary.grade"]')).not.toBeNull();
                expect(textOf('exam-result-grade')).toContain('1.7');
                expect(grade.classList).toContain('font-semibold');
                expect(fixture.nativeElement.querySelector('[data-testid="exam-result-grade-before-bonus"]')).toBeNull();
            });

            it('should not show a dangling grade label when there is no grading scale', () => {
                render({ overallGrade: undefined });

                expect(component.gradingScaleExists()).toBe(false);
                expect(fixture.nativeElement.querySelector('[data-testid="exam-result-grade"]')).toBeNull();
                expect(fixture.nativeElement.querySelector('[jhiTranslate="artemisApp.exam.examSummary.grade"]')).toBeNull();
                // the points sentence is still there
                expect(sentences()).toHaveLength(1);
            });

            it('should show the grades before and after the bonus when a bonus was applied', () => {
                render({
                    overallGrade: '2.0',
                    gradeWithBonus: { bonusStrategy: BonusStrategy.GRADES_DISCRETE, bonusGrade: '1.0', bonusFromTitle: 'Bonus Exam', finalGrade: '1.7' },
                });

                expect(fixture.nativeElement.querySelector('[data-testid="exam-result-grade"]')).toBeNull();
                expect(textOf('exam-result-grade-before-bonus')).toContain('2.0');
                expect(textOf('exam-result-grade-after-bonus')).toContain('1.7');
                expect(textOf('exam-result-bonus-sentence')).toBe('artemisApp.exam.examSummary.points.youAchievedFromBonus.GRADES_DISCRETE');
            });

            it('should style the sentences small and line the block up with the table text', () => {
                render();

                const block = fixture.nativeElement.querySelector('[data-testid="exam-result-points-summary"]') as HTMLElement;
                expect(block.classList).toContain('px-2');
                expect(block.classList).not.toContain('mx-4');
                expect(sentences()[0].classList).toContain('text-sm');
                expect(sentences()[0].classList).not.toContain('text-xl');
            });
        });

        describe('result table', () => {
            const table = () => fixture.nativeElement.querySelector('#result-overview-table') as HTMLTableElement;

            it('should scroll horizontally inside the card instead of being clipped', () => {
                render();

                const wrapper = fixture.nativeElement.querySelector('[data-testid="result-overview-table-scroll"]') as HTMLElement;
                expect(wrapper.classList).toContain('overflow-x-auto');
                expect(wrapper.contains(table())).toBe(true);
                expect(fixture.nativeElement.querySelector('.exam-points-summary-container').classList).not.toContain('max-w-[1140px]');
            });

            it('should use the compact table size and let the headers wrap', () => {
                render();

                expect(table().classList).toContain('tumaet:[&_tbody_td]:py-1.5');
                expect(table().classList).toContain('[&_thead_th]:whitespace-normal!');
            });

            it('should not make the rows look clickable', () => {
                render();

                const classes = table().className;
                expect(classes).not.toContain('cursor-pointer');
                expect(classes).not.toContain('scale-');
                expect(table().classList).toContain('tumaet:[&_tbody_tr:hover]:bg-hover-background');
            });

            it('should pad the row header and footer cells like the other cells', () => {
                render();

                for (const cell of ['tbody_th', 'tfoot_th', 'tfoot_td']) {
                    expect(table().classList).toContain(`[&_${cell}]:px-2`);
                    expect(table().classList).toContain(`[&_${cell}]:py-1.5`);
                }
                // the reboot resets the borders of th and tfoot in an unlayered rule, so the utilities have to be important
                expect(table().classList).toContain('[&_tbody_th]:border-b!');
                expect(table().querySelector('tfoot')!.classList).toContain('border-t-2!');
            });

            it('should centre the numeric headers over their centred values and keep the text columns start aligned', () => {
                render();

                const headers = Array.from<HTMLElement>(table().querySelectorAll('thead th'));
                // #, Exercise, Your Points, Achievable Points, Achieved Percentage, Achievable Bonus Points
                expect(headers).toHaveLength(6);
                expect(headers.map((header) => header.classList.contains('text-center!'))).toEqual([false, false, true, true, true, true]);

                const bodyCells = Array.from<HTMLElement>(table().querySelectorAll('tbody tr:first-child td'));
                expect(bodyCells.slice(1).every((cell) => cell.classList.contains('text-center'))).toBe(true);
                const footerCells = Array.from<HTMLElement>(table().querySelectorAll('tfoot td'));
                expect(footerCells.every((cell) => cell.classList.contains('text-center'))).toBe(true);
            });

            it('should render the exercise as an icon next to a title button in one row', () => {
                render();

                const button = table().querySelector('[data-testid="exam-summary-open-exercise"]') as HTMLButtonElement;
                const row = button.parentElement as HTMLElement;
                expect(row.classList).toContain('flex');
                expect(row.classList).toContain('items-center');
                expect(button.classList).toContain('min-w-0');
                expect(button.textContent?.trim()).toBe('-');
                expect(table().querySelector('tbody td')!.innerHTML).not.toContain('&nbsp;');
            });

            it('should drop the bonus column when the exam has no bonus points', () => {
                render({}, { maxBonusPoints: 0 });

                expect(table().querySelectorAll('thead th')).toHaveLength(5);
                expect(table().querySelectorAll('tfoot td')).toHaveLength(3);
            });
        });
    });
});
