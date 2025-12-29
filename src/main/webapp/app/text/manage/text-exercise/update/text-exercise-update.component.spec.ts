/**
 * Vitest tests for TextExerciseUpdateComponent.
 *
 * vi.mock('monaco-editor') is required because MonacoTextEditorAdapter has static initializers
 * that run before the path alias mock can take effect. vi.mock hoists to file top.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('monaco-editor', () => ({
    editor: { create: () => ({}), createModel: () => ({}), defineTheme: () => {}, setTheme: () => {} },
    languages: { register: () => {}, setMonarchTokensProvider: () => {}, setLanguageConfiguration: () => {}, registerCompletionItemProvider: () => ({ dispose: () => {} }) },
    Range: class {
        constructor(
            public startLineNumber: number,
            public startColumn: number,
            public endLineNumber: number,
            public endColumn: number,
        ) {}
    },
    Position: class {
        constructor(
            public lineNumber: number,
            public column: number,
        ) {}
    },
    KeyCode: { Backspace: 1, Tab: 2, Enter: 3, Escape: 9, Delete: 10 },
    KeyMod: { CtrlCmd: 2048, Shift: 1024, Alt: 512, WinCtrl: 256 },
    MarkerSeverity: { Error: 8, Warning: 4, Info: 2, Hint: 1 },
    Uri: { parse: (s: string) => ({ toString: () => s }) },
}));

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Data, Params, UrlSegment, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent, MockDirective } from 'ng-mocks';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

import { TextExerciseUpdateComponent } from 'app/text/manage/text-exercise/update/text-exercise-update.component';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import * as Utils from 'app/exercise/course-exercises/course-utils';

import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { ExerciseUpdateWarningService } from 'app/exercise/exercise-update-warning/exercise-update-warning.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { IncludedInOverallScorePickerComponent } from 'app/exercise/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { PresentationScoreComponent } from 'app/exercise/presentation-score/presentation-score.component';
import { GradingInstructionsDetailsComponent } from 'app/exercise/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { FormStatusBarComponent } from 'app/shared/form/form-status-bar/form-status-bar.component';
import { FormFooterComponent } from 'app/shared/form/form-footer/form-footer.component';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { DifficultyPickerComponent } from 'app/exercise/difficulty-picker/difficulty-picker.component';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';

// NOTE: Do NOT import MarkdownEditorMonacoComponent here - it transitively imports monaco-editor
// which causes static initializers to run before mocks are applied.

// Mock component to replace MarkdownEditorMonacoComponent without importing the real one
@Component({ selector: 'jhi-markdown-editor-monaco', template: '', standalone: true })
class MockMarkdownEditorMonacoComponent {
    markdown = input<string>('');
    domainActions = input<unknown[]>([]);
}

// Mock component for ExerciseFeedbackSuggestionOptionsComponent
@Component({ selector: 'jhi-exercise-feedback-suggestion-options', template: '', standalone: true })
class MockExerciseFeedbackSuggestionOptionsComponent {
    exercise = input<TextExercise>();
    dueDate = input<dayjs.Dayjs>();
}

// Mock for TitleChannelNameComponent interface
class MockTitleChannelNameComponent {
    isValid = signal(true);
}

// Stub for ExerciseTitleChannelNameComponent - ng-mocks MockComponent doesn't handle viewChild properly
@Component({ selector: 'jhi-exercise-title-channel-name', template: '', standalone: true })
class StubExerciseTitleChannelNameComponent {
    exercise = input<TextExercise | undefined>();
    titlePattern = input<string>('');
    minTitleLength = input<number>(0);
    isExamMode = input<boolean>(false);
    isImport = input<boolean>(false);
    hideTitleLabel = input<boolean>(false);
    course = input<Course>();
    isEditFieldDisplayedRecord = input<Record<string, boolean>>();
    courseId = input<number>();
    onTitleChange = output<string>();
    onChannelNameChange = output<string>();
    // Use a method that returns a mock instance instead of viewChild
    private readonly _titleChannelNameComponent = new MockTitleChannelNameComponent();
    titleChannelNameComponent = () => this._titleChannelNameComponent;
}

// Stub for ExerciseUpdatePlagiarismComponent
@Component({ selector: 'jhi-exercise-update-plagiarism', template: '', standalone: true })
class StubExerciseUpdatePlagiarismComponent {
    exercise = input<TextExercise | undefined>();
    isFormValid = signal(true);
}

// Stub for TeamConfigFormGroupComponent
@Component({ selector: 'jhi-team-config-form-group', template: '', standalone: true })
class StubTeamConfigFormGroupComponent {
    exercise = input<TextExercise | undefined>();
    formValid = true;
    formValidChanges = new Subject<boolean>();
}

describe('TextExercise Management Update Component', () => {
    setupTestBed({ zoneless: true });

    let component: TextExerciseUpdateComponent;
    let fixture: ComponentFixture<TextExerciseUpdateComponent>;
    let textExerciseService: TextExerciseService;
    let calendarService: CalendarService;

    let routeData$: BehaviorSubject<Data>;
    let routeUrl$: BehaviorSubject<UrlSegment[]>;
    let routeParams$: BehaviorSubject<Params>;

    const createCourse = (id = 1): Course => {
        const course = new Course();
        course.id = id;
        return course;
    };

    const createExercise = (course?: Course, exerciseGroup?: ExerciseGroup): TextExercise => {
        const exercise = new TextExercise(course, exerciseGroup);
        exercise.id = undefined;
        exercise.title = 'Test Text Exercise';
        exercise.channelName = 'test-channel';
        return exercise;
    };

    const createExistingExercise = (): TextExercise => {
        const exercise = createExercise(createCourse());
        exercise.id = 123;
        return exercise;
    };

    beforeEach(async () => {
        routeData$ = new BehaviorSubject({ textExercise: createExercise(createCourse()) });
        routeUrl$ = new BehaviorSubject([{ path: 'new' }] as UrlSegment[]);
        routeParams$ = new BehaviorSubject({ courseId: 1 });

        await TestBed.configureTestingModule({
            imports: [TextExerciseUpdateComponent, TranslateModule.forRoot()],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        data: routeData$.asObservable(),
                        url: routeUrl$.asObservable(),
                        params: routeParams$.asObservable(),
                        snapshot: { paramMap: { get: () => null } },
                    },
                },
                {
                    provide: NgbModal,
                    useValue: {
                        open: vi.fn(),
                        hasOpenModals: vi.fn().mockReturnValue(false),
                    },
                },
                {
                    provide: CourseManagementService,
                    useValue: {
                        find: vi.fn().mockReturnValue(of(new HttpResponse({ body: createCourse() }))),
                        findAllCategoriesOfCourse: vi.fn().mockReturnValue(of(new HttpResponse({ body: [] }))),
                    },
                },
                {
                    provide: ExerciseService,
                    useValue: {
                        validateDate: vi.fn(),
                        convertExerciseCategoriesAsStringFromServer: vi.fn().mockReturnValue([]),
                    },
                },
                {
                    provide: ArtemisNavigationUtilService,
                    useValue: {
                        navigateBackFromExerciseUpdate: vi.fn(),
                        navigateForwardFromExerciseUpdateOrCreation: vi.fn(),
                    },
                },
                {
                    provide: ExerciseUpdateWarningService,
                    useValue: {
                        checkForExerciseUpdateWarning: vi.fn().mockReturnValue(of(undefined)),
                        checkExerciseBeforeUpdate: vi.fn().mockResolvedValue(undefined),
                    },
                },
                {
                    provide: ExerciseGroupService,
                    useValue: {
                        find: vi.fn().mockReturnValue(of(new HttpResponse({ body: new ExerciseGroup() }))),
                    },
                },
                {
                    provide: CalendarService,
                    useValue: {
                        reloadEvents: vi.fn(),
                    },
                },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .overrideComponent(TextExerciseUpdateComponent, {
                set: {
                    imports: [
                        FormsModule,
                        MockDirective(TranslateDirective),
                        FaIconComponent,
                        NgbTooltip,
                        ArtemisTranslatePipe,
                        MockComponent(FormDateTimePickerComponent),
                        StubExerciseTitleChannelNameComponent,
                        StubTeamConfigFormGroupComponent,
                        MockComponent(IncludedInOverallScorePickerComponent),
                        MockComponent(PresentationScoreComponent),
                        MockComponent(GradingInstructionsDetailsComponent),
                        MockComponent(DocumentationButtonComponent),
                        MockComponent(FormStatusBarComponent),
                        MockComponent(FormFooterComponent),
                        MockComponent(CategorySelectorComponent),
                        MockComponent(DifficultyPickerComponent),
                        MockComponent(HelpIconComponent),
                        MockComponent(CompetencySelectionComponent),
                        MockMarkdownEditorMonacoComponent,
                        MockExerciseFeedbackSuggestionOptionsComponent,
                        StubExerciseUpdatePlagiarismComponent,
                        MockComponent(FeatureOverlayComponent),
                    ],
                },
            })
            .compileComponents();

        textExerciseService = TestBed.inject(TextExerciseService);
        calendarService = TestBed.inject(CalendarService);
    });

    afterEach(() => {
        vi.clearAllMocks();
        if (fixture) {
            fixture.destroy();
        }
    });

    describe('save', () => {
        describe('existing exercise', () => {
            it('should call update service and refresh calendar events on save for existing entity', async () => {
                const exercise = createExistingExercise();
                routeData$.next({ textExercise: exercise });
                routeUrl$.next([{ path: 'exercise-groups' }] as UrlSegment[]);

                fixture = TestBed.createComponent(TextExerciseUpdateComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
                await fixture.whenStable();

                vi.spyOn(textExerciseService, 'update').mockReturnValue(of(new HttpResponse({ body: exercise })));
                const refreshSpy = vi.spyOn(calendarService, 'reloadEvents');

                component.save();
                await fixture.whenStable();

                expect(textExerciseService.update).toHaveBeenCalledWith(exercise, {});
                expect(component.isSaving).toBe(false);
                expect(refreshSpy).toHaveBeenCalledOnce();
            });

            it('should error during save', async () => {
                const exercise = createExistingExercise();
                routeData$.next({ textExercise: exercise });
                routeUrl$.next([{ path: 'exercise-groups' }] as UrlSegment[]);

                fixture = TestBed.createComponent(TextExerciseUpdateComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
                await fixture.whenStable();

                const onErrorSpy = vi.spyOn(component as any, 'onSaveError');
                vi.spyOn(textExerciseService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse({ error: { title: 'some-error' } })));

                component.save();
                await fixture.whenStable();

                expect(onErrorSpy).toHaveBeenCalledOnce();
            });
        });

        describe('new exercise', () => {
            it('should call create service and refresh calendar events on save for new entity', async () => {
                const exercise = createExercise(createCourse());
                routeData$.next({ textExercise: exercise });
                routeUrl$.next([{ path: 'new' }] as UrlSegment[]);

                fixture = TestBed.createComponent(TextExerciseUpdateComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
                await fixture.whenStable();

                vi.spyOn(textExerciseService, 'create').mockReturnValue(of(new HttpResponse({ body: exercise })));
                const refreshSpy = vi.spyOn(calendarService, 'reloadEvents');

                component.save();
                await fixture.whenStable();

                expect(textExerciseService.create).toHaveBeenCalledWith(exercise);
                expect(component.isSaving).toBe(false);
                expect(refreshSpy).toHaveBeenCalledOnce();
            });
        });

        describe('imported exercise', () => {
            it('should call import service on save for new entity', async () => {
                const exercise = createExercise(createCourse());
                routeData$.next({ textExercise: exercise });
                routeUrl$.next([{ path: 'import' }] as UrlSegment[]);

                fixture = TestBed.createComponent(TextExerciseUpdateComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
                await fixture.whenStable();

                vi.spyOn(textExerciseService, 'import').mockReturnValue(of(new HttpResponse({ body: exercise })));

                component.save();
                await fixture.whenStable();

                expect(textExerciseService.import).toHaveBeenCalledWith(exercise);
                expect(component.isSaving).toBe(false);
            });
        });
    });

    describe('exam exercise', () => {
        it('should be in exam mode', async () => {
            const exerciseGroup = new ExerciseGroup();
            const exercise = createExercise(undefined, exerciseGroup);
            routeData$.next({ textExercise: exercise });
            routeUrl$.next([{ path: 'exercise-groups' }] as UrlSegment[]);

            fixture = TestBed.createComponent(TextExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isExamMode).toBe(true);
            expect(component.textExercise).toEqual(exercise);
        });

        it('should not set dateErrors', async () => {
            const exerciseGroup = new ExerciseGroup();
            const exercise = createExercise(undefined, exerciseGroup);
            routeData$.next({ textExercise: exercise });
            routeUrl$.next([{ path: 'exercise-groups' }] as UrlSegment[]);

            fixture = TestBed.createComponent(TextExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            const dateErrorNames = ['dueDateError', 'startDateError', 'assessmentDueDateError', 'exampleSolutionPublicationDateError'];
            component.validateDate();

            for (const errorName of dateErrorNames) {
                expect(component.textExercise[errorName as keyof TextExercise]).toBeFalsy();
            }
        });
    });

    describe('ngOnInit for course exercise', () => {
        it('should not be in exam mode', async () => {
            const exercise = createExercise(createCourse());
            routeData$.next({ textExercise: exercise });
            routeUrl$.next([{ path: 'new' }] as UrlSegment[]);

            fixture = TestBed.createComponent(TextExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isExamMode).toBe(false);
            expect(component.textExercise).toEqual(exercise);
        });

        it('should initialize component for course exercise', async () => {
            const exercise = createExercise(createCourse());
            routeData$.next({ textExercise: exercise });
            routeUrl$.next([{ path: 'new' }] as UrlSegment[]);

            fixture = TestBed.createComponent(TextExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            // Verify component is properly initialized
            expect(component.textExercise).toBeDefined();
            expect(component.backupExercise).toBeDefined();
            expect(component.isSaving).toBe(false);
        });
    });

    describe('ngOnInit in import mode: Course to Course', () => {
        it('should set isImport and remove all dates', async () => {
            const exercise = createExercise(createCourse());
            exercise.id = 1;
            exercise.releaseDate = dayjs();
            exercise.dueDate = dayjs();
            exercise.assessmentDueDate = dayjs();
            routeData$.next({ textExercise: exercise });
            routeUrl$.next([{ path: 'import' }] as UrlSegment[]);
            routeParams$.next({ courseId: 1 });

            fixture = TestBed.createComponent(TextExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isImport).toBe(true);
            expect(component.isExamMode).toBe(false);
            expect(component.textExercise.assessmentDueDate).toBeUndefined();
            expect(component.textExercise.releaseDate).toBeUndefined();
            expect(component.textExercise.dueDate).toBeUndefined();
        });

        it('should load exercise categories', async () => {
            const loadExerciseCategoriesSpy = vi.spyOn(Utils, 'loadCourseExerciseCategories');

            const exercise = createExercise(createCourse());
            exercise.id = 1;
            routeData$.next({ textExercise: exercise });
            routeUrl$.next([{ path: 'import' }] as UrlSegment[]);
            routeParams$.next({ courseId: 1 });

            fixture = TestBed.createComponent(TextExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            // loadCourseExerciseCategories may be called multiple times:
            // once when examCourseId is set and once in the import path
            expect(loadExerciseCategoriesSpy).toHaveBeenCalled();
        });
    });

    describe('ngOnInit in import mode: Exam to Course', () => {
        it('should set isImport and remove all dates', async () => {
            const exercise = new TextExercise(undefined, undefined);
            exercise.exerciseGroup = new ExerciseGroup();
            exercise.exerciseGroup.exam = new Exam();
            exercise.exerciseGroup.exam.course = createCourse();
            exercise.id = 1;
            exercise.releaseDate = dayjs();
            exercise.dueDate = dayjs();
            exercise.assessmentDueDate = dayjs();
            exercise.channelName = 'testChannel';
            routeData$.next({ textExercise: exercise });
            routeUrl$.next([{ path: 'import' }] as UrlSegment[]);
            routeParams$.next({ courseId: 1 });

            fixture = TestBed.createComponent(TextExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isImport).toBe(true);
            expect(component.isExamMode).toBe(false);
            expect(component.textExercise.assessmentDueDate).toBeUndefined();
            expect(component.textExercise.releaseDate).toBeUndefined();
            expect(component.textExercise.dueDate).toBeUndefined();
        });
    });

    describe('ngOnInit in import mode: Course to Exam', () => {
        it('should set isImport and isExamMode and remove all dates', async () => {
            const exercise = createExercise(createCourse());
            exercise.id = 1;
            exercise.releaseDate = dayjs();
            exercise.dueDate = dayjs();
            exercise.assessmentDueDate = dayjs();
            routeData$.next({ textExercise: exercise });
            routeUrl$.next([{ path: 'exercise-groups' }, { path: 'import' }] as UrlSegment[]);
            routeParams$.next({ groupId: 1 });

            fixture = TestBed.createComponent(TextExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isImport).toBe(true);
            expect(component.isExamMode).toBe(true);
            expect(component.textExercise.course).toBeUndefined();
            expect(component.textExercise.assessmentDueDate).toBeUndefined();
            expect(component.textExercise.releaseDate).toBeUndefined();
            expect(component.textExercise.dueDate).toBeUndefined();
        });
    });

    describe('ngOnInit in import mode: Exam to Exam', () => {
        it('should set isImport and isExamMode and remove all dates', async () => {
            const exercise = new TextExercise(undefined, undefined);
            exercise.exerciseGroup = new ExerciseGroup();
            exercise.id = 1;
            exercise.releaseDate = dayjs();
            exercise.dueDate = dayjs();
            exercise.assessmentDueDate = dayjs();
            exercise.channelName = 'testChannel';
            routeData$.next({ textExercise: exercise });
            routeUrl$.next([{ path: 'exercise-groups' }, { path: 'import' }] as UrlSegment[]);
            routeParams$.next({ groupId: 1 });

            fixture = TestBed.createComponent(TextExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isImport).toBe(true);
            expect(component.isExamMode).toBe(true);
            expect(component.textExercise.assessmentDueDate).toBeUndefined();
            expect(component.textExercise.releaseDate).toBeUndefined();
            expect(component.textExercise.dueDate).toBeUndefined();
        });
    });

    it('should updateCategories properly by making category available for selection again when removing it', async () => {
        const exercise = createExercise(createCourse());
        routeData$.next({ textExercise: exercise });
        routeUrl$.next([{ path: 'new' }] as UrlSegment[]);

        fixture = TestBed.createComponent(TextExerciseUpdateComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        component.exerciseCategories = [];
        const newCategories = [new ExerciseCategory('Easy', undefined), new ExerciseCategory('Hard', undefined)];

        component.updateCategories(newCategories);

        expect(component.textExercise.categories).toEqual(newCategories);
        expect(component.exerciseCategories).toEqual(newCategories);
    });
});
