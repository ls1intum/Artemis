import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { DialogService } from 'primeng/dynamicdialog';
import { TumAetUiDatePickerComponent } from '@tumaet/ui-angular';
import { QuizExerciseUpdateComponent } from 'app/quiz/manage/update/quiz-exercise-update.component';
import { QuizExercise, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { Course } from 'app/course/shared/entities/course.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { CalendarService } from 'app/calendar/shared/service/calendar.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { DragAndDropQuestionUtil } from 'app/quiz/shared/service/drag-and-drop-question-util.service';
import { ShortAnswerQuestionUtil } from 'app/quiz/shared/service/short-answer-question-util.service';
import { ExerciseTitleChannelNamePrimengComponent } from 'app/exercise/exercise-title-channel-name-primeng/exercise-title-channel-name-primeng.component';
import { CategorySelectorPrimengComponent } from 'app/exercise/category-selector-primeng/category-selector-primeng.component';
import { CompetencySelectionPrimengComponent } from 'app/atlas/shared/competency-selection-primeng/competency-selection-primeng.component';
import { QuizQuestionListEditComponent } from 'app/quiz/manage/list-edit/quiz-question-list-edit.component';
import { QuizAiGenerationModalComponent } from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generation-modal.component';
import { MockRouter } from 'src/test/javascript/spec/helpers/mocks/mock-router';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';

/** Renders the real template of the schedule section: the heavy children are replaced, the date fields are not. */
describe('QuizExerciseUpdateComponent schedule section', () => {
    let fixture: ComponentFixture<QuizExerciseUpdateComponent>;
    let comp: QuizExerciseUpdateComponent;
    const course = { id: 123 } as Course;

    const createExercise = (quizMode: QuizMode) => {
        const exercise = new QuizExercise(course, undefined);
        exercise.id = 456;
        exercise.title = 'test';
        exercise.duration = 600;
        exercise.quizMode = quizMode;
        const question = new MultipleChoiceQuestion();
        question.title = 'test';
        question.points = 10;
        const option = new AnswerOption();
        option.isCorrect = true;
        question.answerOptions = [option];
        exercise.quizQuestions = [question];
        exercise.quizBatches = [];
        exercise.isAtLeastEditor = true;
        return exercise;
    };

    const pickers = () => fixture.debugElement.queryAll(By.directive(TumAetUiDatePickerComponent)).map((element) => element.componentInstance as TumAetUiDatePickerComponent);
    const picker = (inputId: string) => pickers().find((candidate) => candidate.inputId() === inputId)!;
    const type = async (inputId: string, text: string) => {
        const input: HTMLInputElement = fixture.nativeElement.querySelector(`#${inputId}`);
        input.value = text;
        input.dispatchEvent(new Event('input'));
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };
    const open = async (quizMode: QuizMode, scheduleQuizStart = false, group = false) => {
        const exercise = createExercise(quizMode);
        if (group) {
            exercise.exerciseVariantGroup = { id: 7 } as QuizExercise['exerciseVariantGroup'];
        }
        comp.course = course;
        comp.quizExercise.set(exercise);
        comp.init();
        comp.scheduleQuizStart = scheduleQuizStart;
        comp.cacheValidation();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                MockProvider(DialogService),
                { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ courseId: course.id, exerciseId: 456 }) }, queryParams: of({}) } },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                MockProvider(AlertService),
                MockProvider(CalendarService),
                MockProvider(DragAndDropQuestionUtil),
                MockProvider(ShortAnswerQuestionUtil),
                MockProvider(ProfileService, { isModuleFeatureActive: () => false }),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(QuizExerciseUpdateComponent, {
                remove: {
                    imports: [
                        ExerciseTitleChannelNamePrimengComponent,
                        CategorySelectorPrimengComponent,
                        CompetencySelectionPrimengComponent,
                        QuizQuestionListEditComponent,
                        QuizAiGenerationModalComponent,
                    ],
                },
                add: {
                    imports: [
                        MockComponent(ExerciseTitleChannelNamePrimengComponent),
                        MockComponent(CategorySelectorPrimengComponent),
                        MockComponent(CompetencySelectionPrimengComponent),
                        MockComponent(QuizQuestionListEditComponent),
                        MockComponent(QuizAiGenerationModalComponent),
                    ],
                },
            })
            .compileComponents();
        fixture = TestBed.createComponent(QuizExerciseUpdateComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should show the release and due date, but no start time, outside the synchronized mode', async () => {
        await open(QuizMode.INDIVIDUAL);

        expect(pickers().map((candidate) => candidate.inputId())).toEqual(['pick-releaseDate-input', 'pick-dueDate-input']);
    });

    it('should show the release date and, once scheduled, the start time in the synchronized mode', async () => {
        await open(QuizMode.SYNCHRONIZED);
        expect(pickers().map((candidate) => candidate.inputId())).toEqual(['pick-releaseDate-input']);

        await open(QuizMode.SYNCHRONIZED, true);
        expect(pickers().map((candidate) => candidate.inputId())).toEqual(['pick-releaseDate-input', 'quiz-batch-start-time-0']);
    });

    it('should write picked dates to the quiz and validate them', async () => {
        await open(QuizMode.INDIVIDUAL);
        const cacheValidationSpy = vi.spyOn(comp, 'cacheValidation');

        picker('pick-releaseDate-input').value.set(dayjs('2030-06-02T12:00:00'));
        expect(comp.quizExercise().releaseDate!.isSame(dayjs('2030-06-02T12:00:00'))).toBe(true);
        expect(cacheValidationSpy).toHaveBeenCalledOnce();

        picker('pick-dueDate-input').value.set(dayjs('2030-06-01T12:00:00'));
        fixture.detectChanges();

        expect(comp.quizExercise().dueDate!.isSame(dayjs('2030-06-01T12:00:00'))).toBe(true);
        expect(comp.quizExercise().dueDateError).toBe(true);
        expect(picker('pick-dueDate-input').invalid()).toBe(true);
        expect(fixture.nativeElement.textContent).toContain('artemisApp.quizExercise.dueDateError');
    });

    it('should mark a start time before the release date invalid and explain it', async () => {
        await open(QuizMode.SYNCHRONIZED, true);
        picker('pick-releaseDate-input').value.set(dayjs('2030-06-02T12:00:00'));

        picker('quiz-batch-start-time-0').value.set(dayjs('2030-06-01T12:00:00'));
        fixture.detectChanges();

        expect(comp.quizExercise().quizBatches![0].startTime!.isSame(dayjs('2030-06-01T12:00:00'))).toBe(true);
        expect(picker('quiz-batch-start-time-0').invalid()).toBe(true);
        expect(fixture.nativeElement.textContent).toContain('artemisApp.quizExercise.startTimeErrorSynchronized');
        expect(comp.isSaveDisabled()).toBe(true);
    });

    it('should keep the fields editable and show no notice while the quiz is not in a variant group', async () => {
        await open(QuizMode.INDIVIDUAL);

        expect(picker('pick-releaseDate-input').disabled()).toBe(false);
        expect(picker('pick-dueDate-input').disabled()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="exercise-group-date-notice"]')).toBeNull();
    });

    it('should lock the dates and offer to edit the group while the quiz is in a variant group', async () => {
        await open(QuizMode.INDIVIDUAL, false, true);

        expect(picker('pick-releaseDate-input').disabled()).toBe(true);
        expect(picker('pick-dueDate-input').disabled()).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="exercise-group-date-notice"]')).not.toBeNull();
    });

    describe('text that is not a date', () => {
        it('should block saving and explain why while a date field holds such text', async () => {
            await open(QuizMode.INDIVIDUAL);
            comp.pendingChangesCache.set(true);
            comp.cacheValidation();
            expect(comp.hasInvalidDateText()).toBe(false);
            const releaseDate = comp.quizExercise().releaseDate;

            await type('pick-releaseDate-input', 'not a date');

            expect(comp.hasInvalidDateText()).toBe(true);
            expect(comp.isSaveDisabled()).toBe(true);
            expect(comp.saveBlockedReasons().some((reason) => reason.includes('entity.dateMissingOrNotValidGeneric'))).toBe(true);
            // the picker keeps the last valid date, which is why saving has to be blocked
            expect(comp.quizExercise().releaseDate).toBe(releaseDate);

            await type('pick-releaseDate-input', '');

            expect(comp.hasInvalidDateText()).toBe(false);
            expect(comp.saveBlockedReasons().some((reason) => reason.includes('entity.dateMissingOrNotValidGeneric'))).toBe(false);
        });

        it('should not count an external date error as text that is not a date', async () => {
            await open(QuizMode.INDIVIDUAL);

            picker('pick-releaseDate-input').value.set(dayjs('2030-06-02T12:00:00'));
            picker('pick-dueDate-input').value.set(dayjs('2030-06-01T12:00:00'));
            fixture.detectChanges();

            expect(picker('pick-dueDate-input').invalid()).toBe(true);
            expect(comp.hasInvalidDateText()).toBe(false);
        });

        it('should forget invalid text once its field is gone', async () => {
            await open(QuizMode.INDIVIDUAL);
            await type('pick-dueDate-input', 'not a date');
            expect(comp.hasInvalidDateText()).toBe(true);

            comp.quizExercise().quizMode = QuizMode.SYNCHRONIZED;
            comp.cacheValidation();
            fixture.detectChanges();

            expect(comp.hasInvalidDateText()).toBe(false);
        });
    });
});
