import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { DialogService } from 'primeng/dynamicdialog';
import { QuizExerciseUpdateComponent } from 'app/quiz/manage/update/quiz-exercise-update.component';
import { QuizExercise, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { Course, CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { CalendarService } from 'app/calendar/shared/service/calendar.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { DragAndDropQuestionUtil } from 'app/quiz/shared/service/drag-and-drop-question-util.service';
import { ShortAnswerQuestionUtil } from 'app/quiz/shared/service/short-answer-question-util.service';
import { ExerciseTitleChannelNameComponent } from 'app/exercise/exercise-title-channel-name/exercise-title-channel-name.component';
import { CategorySelectorPrimengComponent } from 'app/exercise/category-selector-primeng/category-selector-primeng.component';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { QuizQuestionListEditComponent } from 'app/quiz/manage/list-edit/quiz-question-list-edit.component';
import { QuizAiGenerationModalComponent } from 'app/hyperion/quiz/quiz-ai-generation-modal/quiz-ai-generation-modal.component';
import { MockRouter } from 'src/test/javascript/spec/helpers/mocks/mock-router';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';

/** Keeps the real title/channel controls and quiz template; only unrelated heavy children are replaced. */
describe('QuizExerciseUpdateComponent title and channel validation', () => {
    let fixture: ComponentFixture<QuizExerciseUpdateComponent>;
    let comp: QuizExerciseUpdateComponent;
    const course: Course = { id: 123, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING };

    const createExercise = (quizMode: QuizMode) => {
        const exercise = new QuizExercise(course, undefined);
        exercise.id = 456;
        exercise.title = 'Original quiz';
        exercise.channelName = 'exercise-original-quiz';
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

    const controls = () => fixture.debugElement.query(By.directive(ExerciseTitleChannelNameComponent)).componentInstance as ExerciseTitleChannelNameComponent;
    const saveButton = () => fixture.nativeElement.querySelector('[data-testid="quiz-save"]') as HTMLButtonElement;
    const type = async (id: string, text: string) => {
        const input = fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement;
        input.value = text;
        input.dispatchEvent(new Event('input'));
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };
    const open = async (options: { communicationDisabled?: boolean; exam?: boolean } = {}) => {
        const exercise = createExercise(QuizMode.INDIVIDUAL);
        if (options.communicationDisabled) {
            exercise.course = { id: 123, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.DISABLED };
        }
        comp.isExamMode.set(!!options.exam);
        comp.course = exercise.course;
        comp.courseId.set(course.id);
        comp.quizExercise.set(exercise);
        comp.init();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };
    const respondWithExistingTitles = (titles: string[]) => {
        TestBed.inject(HttpTestingController).expectOne('api/course/courses/123/existing-exercise-details?exerciseType=quiz').flush({ exerciseTitles: titles, shortNames: [] });
        fixture.detectChanges();
    };

    beforeEach(async () => {
        vi.spyOn(QuizExerciseUpdateComponent.prototype, 'ngOnInit').mockImplementation(() => {});
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
                    imports: [CategorySelectorPrimengComponent, CompetencySelectionComponent, QuizQuestionListEditComponent, QuizAiGenerationModalComponent],
                },
                add: {
                    imports: [
                        MockComponent(CategorySelectorPrimengComponent),
                        MockComponent(CompetencySelectionComponent),
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

    it('should block a duplicate title immediately when existing titles arrive, and unblock a unique title', async () => {
        await open();
        await type('field_title', 'Duplicate quiz');
        expect(comp.quizIsValid()).toBe(true);
        expect(saveButton().getAttribute('aria-disabled')).toBe('false');

        respondWithExistingTitles(['Duplicate quiz']);

        expect(controls().titleChannelNameComponent().isTitleDisallowed()).toBe(true);
        expect(comp.isSaveDisabled()).toBe(true);
        expect(saveButton().getAttribute('aria-disabled')).toBe('true');
        expect(comp.saveBlockedReasons()).toContain('artemisApp.exercise.form.title.disallowedValue');

        await type('field_title', 'Unique quiz');

        expect(comp.isSaveDisabled()).toBe(false);
        expect(comp.saveBlockedReasons()).toEqual([]);
    });

    it('should block an empty displayed channel and unblock a valid channel', async () => {
        await open();
        await type('field_channel_name', '');

        expect(controls().titleChannelNameComponent().isChannelFieldDisplayed()).toBe(true);
        expect(comp.quizIsValid()).toBe(true);
        expect(comp.isSaveDisabled()).toBe(true);
        expect(comp.saveBlockedReasons()).toContain('artemisApp.exercise.form.channelName.empty');

        await type('field_channel_name', 'valid-channel');

        expect(comp.isSaveDisabled()).toBe(false);
        expect(comp.saveBlockedReasons()).toEqual([]);
    });

    it.each([{ communicationDisabled: true }, { exam: true }])('should not require a hidden channel with %o', async (options) => {
        await open(options);
        await type('field_title', 'Changed quiz');
        controls().titleChannelNameComponent().channelName.set('');
        fixture.detectChanges();

        expect(controls().titleChannelNameComponent().isChannelFieldDisplayed()).toBe(false);
        expect(comp.isSaveDisabled()).toBe(false);
        expect(comp.saveBlockedReasons()).toEqual([]);
    });

    it('should keep an unchanged quiz disabled when the current title is already used', async () => {
        await open();
        respondWithExistingTitles(['Original quiz']);

        expect(controls().titleChannelNameComponent().isTitleDisallowed()).toBe(false);
        expect(comp.pendingChangesCache()).toBe(false);
        expect(comp.isSaveDisabled()).toBe(true);
        expect(comp.saveBlockedReasons()).toEqual([]);
    });

    it.each(['duplicate title', 'empty channel'])('should reject direct save with %s without sending a request', async (invalidField) => {
        await open();
        if (invalidField === 'duplicate title') {
            await type('field_title', 'Duplicate quiz');
            respondWithExistingTitles(['Duplicate quiz']);
        } else {
            await type('field_channel_name', '');
        }
        expect(comp.quizIsValid()).toBe(true);
        expect(comp.pendingChangesCache()).toBe(true);
        const http = TestBed.inject(HttpTestingController);
        const questionEditor = comp.quizQuestionListEditComponent();
        questionEditor.fileMap = new Map();

        comp.save();

        expect(comp.isSaving()).toBe(false);
        http.expectNone((request) => request.method === 'POST' || request.method === 'PUT');
    });
});
