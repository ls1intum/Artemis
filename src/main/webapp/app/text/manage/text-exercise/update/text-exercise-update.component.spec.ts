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
import { By } from '@angular/platform-browser';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Data, Params, UrlSegment, provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { provideTranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective } from 'ng-mocks';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';
import { Component, forwardRef, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

import { TextExerciseUpdateComponent } from 'app/text/manage/text-exercise/update/text-exercise-update.component';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { ExerciseMode, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TeamAssignmentConfig } from 'app/exercise/shared/entities/team/team-assignment-config.model';
import * as Utils from 'app/exercise/course-exercises/course-utils';

import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { ExerciseUpdateWarningService } from 'app/exercise/exercise-update-warning/exercise-update-warning.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { CalendarService } from 'app/calendar/shared/service/calendar.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExerciseGroupTimelineLockStubComponent } from 'test/helpers/stubs/exercise/exercise-group-timeline-lock-stub.component';
import { IncludedInOverallScorePickerComponent } from 'app/exercise/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { PresentationScoreComponent } from 'app/exercise/presentation-score/presentation-score.component';
import { GradingInstructionsDetailsComponent } from 'app/exercise/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { FormStatusBarComponent } from 'app/shared-ui/form/form-status-bar/form-status-bar.component';
import { FormFooterComponent } from 'app/shared-ui/form/form-footer/form-footer.component';
import { CategorySelectorPrimengComponent } from 'app/exercise/category-selector-primeng/category-selector-primeng.component';
import { DifficultyPickerComponent } from 'app/exercise/difficulty-picker/difficulty-picker.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { FeatureOverlayComponent } from 'app/shared-ui/components/feature-overlay/feature-overlay.component';
import { ExerciseTimelineComponent } from 'app/exercise/exercise-timeline/exercise-timeline.component';
import { ExerciseTitleChannelNameComponent } from 'app/exercise/exercise-title-channel-name/exercise-title-channel-name.component';
import { ExerciseUpdatePlagiarismComponent } from 'app/plagiarism/manage/exercise-update-plagiarism/exercise-update-plagiarism.component';
import { ExerciseGroupDateNoticeComponent } from 'app/exercise/exercise-group-date-notice/exercise-group-date-notice.component';

// NOTE: Do NOT import MarkdownEditorMonacoComponent here - it transitively imports monaco-editor
// which causes static initializers to run before mocks are applied.

// Mock component to replace MarkdownEditorMonacoComponent without importing the real one
@Component({ selector: 'jhi-markdown-editor-monaco', template: '', standalone: true })
class MockMarkdownEditorMonacoComponent {
    markdown = input<string>('');
    domainActions = input<unknown[]>([]);
}

class MockTitleChannelNameComponent {
    isValid = signal(true);
    isChannelFieldDisplayed = signal(true);
    isTitleDisallowed = signal(false);
}

// Stub for ExerciseTitleChannelNameComponent - ng-mocks MockComponent doesn't handle viewChild properly.
// Provided under the real class, so that the host's viewChild(ExerciseTitleChannelNameComponent) finds it.
@Component({
    selector: 'jhi-exercise-title-channel-name',
    template: '',
    standalone: true,
    providers: [{ provide: ExerciseTitleChannelNameComponent, useExisting: forwardRef(() => StubExerciseTitleChannelNameComponent) }],
})
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
    exerciseChange = output<TextExercise>();
    // Use a method that returns a mock instance instead of viewChild
    private readonly _titleChannelNameComponent = new MockTitleChannelNameComponent();
    titleChannelNameComponent = () => this._titleChannelNameComponent;
}

// Stub for ExerciseUpdatePlagiarismComponent, provided under the real class for the host's viewChild
@Component({
    selector: 'jhi-exercise-update-plagiarism',
    template: '',
    standalone: true,
    providers: [{ provide: ExerciseUpdatePlagiarismComponent, useExisting: forwardRef(() => StubExerciseUpdatePlagiarismComponent) }],
})
class StubExerciseUpdatePlagiarismComponent {
    exercise = input<TextExercise | undefined>();
    isFormValid = signal(true);
    invalidControlNames = signal<string[]>([]);
}

// Stub for TeamConfigFormGroupComponent
@Component({ selector: 'jhi-team-config-form-group', template: '', standalone: true })
class StubTeamConfigFormGroupComponent {
    exercise = input.required<TextExercise>();
    isImport = input<boolean>(false);
    exerciseChange = output<TextExercise>();

    /** Changes the team config the way the real component does: it writes onto the exercise, then emits it. */
    changeTeamConfig(mode: ExerciseMode, minTeamSize?: number) {
        const exercise = this.exercise();
        exercise.mode = mode;
        exercise.teamAssignmentConfig = undefined;
        if (mode === ExerciseMode.TEAM) {
            exercise.teamAssignmentConfig = new TeamAssignmentConfig();
            exercise.teamAssignmentConfig.minTeamSize = minTeamSize;
            exercise.teamAssignmentConfig.maxTeamSize = 5;
        }
        this.exerciseChange.emit(exercise);
    }
}

describe('TextExercise Management Update Component', () => {
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
        routeData$ = new BehaviorSubject<Data>({ textExercise: createExercise(createCourse()) });
        routeUrl$ = new BehaviorSubject([{ path: 'new' }] as UrlSegment[]);
        routeParams$ = new BehaviorSubject<Params>({ courseId: 1 });

        await TestBed.configureTestingModule({
            imports: [TextExerciseUpdateComponent],
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
                provideTranslateService(),
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
                        StubExerciseTitleChannelNameComponent,
                        StubTeamConfigFormGroupComponent,
                        MockComponent(IncludedInOverallScorePickerComponent),
                        MockComponent(PresentationScoreComponent),
                        MockComponent(GradingInstructionsDetailsComponent),
                        MockComponent(DocumentationButtonComponent),
                        MockComponent(FormStatusBarComponent),
                        MockComponent(FormFooterComponent),
                        MockComponent(CategorySelectorPrimengComponent),
                        MockComponent(DifficultyPickerComponent),
                        MockComponent(HelpIconComponent),
                        MockComponent(CompetencySelectionComponent),
                        MockMarkdownEditorMonacoComponent,
                        StubExerciseUpdatePlagiarismComponent,
                        MockComponent(FeatureOverlayComponent),
                        ExerciseGroupTimelineLockStubComponent,
                        ExerciseTimelineComponent,
                        MockComponent(ExerciseGroupDateNoticeComponent),
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
                expect(component.isSaving()).toBe(false);
                expect(refreshSpy).toHaveBeenCalledOnce();
            });

            it('should flush grading instructions before setting isSaving', async () => {
                const exercise = createExistingExercise();
                routeData$.next({ textExercise: exercise });
                routeUrl$.next([{ path: 'exercise-groups' }] as UrlSegment[]);

                fixture = TestBed.createComponent(TextExerciseUpdateComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
                await fixture.whenStable();

                let savingDuringFlush = false;
                const prepareForSave = vi.fn(() => {
                    savingDuringFlush = component.isSaving();
                });
                Object.defineProperty(component, 'gradingInstructionsDetails', { value: () => ({ prepareForSave }) });
                vi.spyOn(textExerciseService, 'update').mockReturnValue(of(new HttpResponse({ body: exercise })));

                component.save();
                await fixture.whenStable();

                expect(prepareForSave).toHaveBeenCalledOnce();
                expect(savingDuringFlush).toBe(false);
            });

            it('should abort the save when the grading instructions could not be parsed', async () => {
                const exercise = createExistingExercise();
                routeData$.next({ textExercise: exercise });
                routeUrl$.next([{ path: 'exercise-groups' }] as UrlSegment[]);

                fixture = TestBed.createComponent(TextExerciseUpdateComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
                await fixture.whenStable();

                Object.defineProperty(component, 'gradingInstructionsDetails', { value: () => ({ prepareForSave: () => false }) });
                const update = vi.spyOn(textExerciseService, 'update');

                component.save();
                await fixture.whenStable();

                // The exercise still holds the previous grading criteria, so sending it would persist
                // criteria the user no longer sees and discard the text they typed.
                expect(update).not.toHaveBeenCalled();
                expect(component.isSaving()).toBe(false);
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
                expect(component.isSaving()).toBe(false);
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
                expect(component.isSaving()).toBe(false);
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

            expect(component.isExamMode()).toBe(true);
            expect(component.textExercise).toEqual(exercise);
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

            expect(component.isExamMode()).toBe(false);
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
            expect(component.isSaving()).toBe(false);
        });

        it('should render one timeline containing all exercise dates', async () => {
            const exercise = createExercise(createCourse());
            exercise.releaseDate = dayjs().add(1, 'hour');
            exercise.startDate = dayjs().add(2, 'hours');
            exercise.dueDate = dayjs().add(1, 'day');
            exercise.assessmentDueDate = dayjs().add(2, 'days');
            exercise.exampleSolutionPublicationDate = dayjs().add(3, 'days');
            routeData$.next({ textExercise: exercise });

            fixture = TestBed.createComponent(TextExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            const timelines = fixture.debugElement.queryAll(By.directive(ExerciseTimelineComponent));
            const timeline = timelines[0].componentInstance as ExerciseTimelineComponent;

            expect(timelines).toHaveLength(1);
            expect(timeline.releaseDate()).toBe(exercise.releaseDate);
            expect(timeline.startDate()).toBe(exercise.startDate);
            expect(timeline.dueDate()).toBe(exercise.dueDate);
            expect(timeline.assessmentDueDate()).toBe(exercise.assessmentDueDate);
            expect(timeline.exampleSolutionPublicationDate()).toBe(exercise.exampleSolutionPublicationDate);
            expect(timeline.exampleSolutionPublicationDateErrorStringKey()).toBe('artemisApp.exercise.exampleSolutionPublicationDateRequiresExampleSolution');

            exercise.exampleSolution = 'Example solution';
            fixture.detectChanges();
            expect(timeline.exampleSolutionPublicationDateErrorStringKey()).toBeUndefined();
        });

        it('should render the group date notice first in the grading controls', async () => {
            const exercise = createExercise(createCourse());
            routeData$.next({ textExercise: exercise });

            fixture = TestBed.createComponent(TextExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            const variantLock = fixture.debugElement.query(By.directive(ExerciseGroupTimelineLockStubComponent)).componentInstance as ExerciseGroupTimelineLockStubComponent;
            variantLock.locked = () => true;
            const openModalSpy = vi.spyOn(variantLock, 'openModal');
            fixture.detectChanges();

            const gradingOptions = fixture.debugElement.query(By.css('.grading-options'));
            const notice = gradingOptions.query(By.directive(ExerciseGroupDateNoticeComponent));

            expect(gradingOptions.nativeElement.firstElementChild).toBe(notice.nativeElement);
            expect((notice.nativeElement as HTMLElement).classList).toContain('mb-3');

            (notice.componentInstance as ExerciseGroupDateNoticeComponent).editGroupDates.emit();

            expect(openModalSpy).toHaveBeenCalledOnce();
        });
    });

    describe('ngOnInit in import mode: Course to Course', () => {
        it('should set isImport and remove all dates', async () => {
            const exercise = createExercise(createCourse());
            exercise.id = 1;
            exercise.releaseDate = dayjs();
            exercise.startDate = dayjs();
            exercise.dueDate = dayjs();
            exercise.assessmentDueDate = dayjs();
            exercise.exampleSolutionPublicationDate = dayjs();
            routeData$.next({ textExercise: exercise });
            routeUrl$.next([{ path: 'import' }] as UrlSegment[]);
            routeParams$.next({ courseId: 1 });

            fixture = TestBed.createComponent(TextExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isImport()).toBe(true);
            expect(component.isExamMode()).toBe(false);
            expect(component.textExercise.assessmentDueDate).toBeUndefined();
            expect(component.textExercise.releaseDate).toBeUndefined();
            expect(component.textExercise.startDate).toBeUndefined();
            expect(component.textExercise.dueDate).toBeUndefined();
            expect(component.textExercise.exampleSolutionPublicationDate).toBeUndefined();

            const timeline = fixture.debugElement.query(By.directive(ExerciseTimelineComponent)).componentInstance as ExerciseTimelineComponent;
            const exampleSolutionPublicationDateItem = timeline.timelineItems().find((item) => item.labelStringKey === 'artemisApp.exercise.exampleSolutionPublicationDate');
            const otherTimelineItems = timeline.timelineItems().filter((item) => item.labelStringKey !== 'artemisApp.exercise.exampleSolutionPublicationDate');

            expect(exampleSolutionPublicationDateItem?.disabled).toBe(true);
            expect(otherTimelineItems.every((item) => !item.disabled)).toBe(true);
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
            exercise.startDate = dayjs();
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

            expect(component.isImport()).toBe(true);
            expect(component.isExamMode()).toBe(false);
            expect(component.textExercise.assessmentDueDate).toBeUndefined();
            expect(component.textExercise.releaseDate).toBeUndefined();
            expect(component.textExercise.startDate).toBeUndefined();
            expect(component.textExercise.dueDate).toBeUndefined();
        });
    });

    describe('ngOnInit in import mode: Course to Exam', () => {
        it('should set isImport and isExamMode and remove all dates', async () => {
            const exercise = createExercise(createCourse());
            exercise.id = 1;
            exercise.releaseDate = dayjs();
            exercise.startDate = dayjs();
            exercise.dueDate = dayjs();
            exercise.assessmentDueDate = dayjs();
            routeData$.next({ textExercise: exercise });
            routeUrl$.next([{ path: 'exercise-groups' }, { path: 'import' }] as UrlSegment[]);
            routeParams$.next({ groupId: 1 });

            fixture = TestBed.createComponent(TextExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isImport()).toBe(true);
            expect(component.isExamMode()).toBe(true);
            expect(component.textExercise.course).toBeUndefined();
            expect(component.textExercise.assessmentDueDate).toBeUndefined();
            expect(component.textExercise.releaseDate).toBeUndefined();
            expect(component.textExercise.startDate).toBeUndefined();
            expect(component.textExercise.dueDate).toBeUndefined();
        });
    });

    describe('ngOnInit in import mode: Exam to Exam', () => {
        it('should set isImport and isExamMode and remove all dates', async () => {
            const exercise = new TextExercise(undefined, undefined);
            exercise.exerciseGroup = new ExerciseGroup();
            exercise.id = 1;
            exercise.releaseDate = dayjs();
            exercise.startDate = dayjs();
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

            expect(component.isImport()).toBe(true);
            expect(component.isExamMode()).toBe(true);
            expect(component.textExercise.assessmentDueDate).toBeUndefined();
            expect(component.textExercise.releaseDate).toBeUndefined();
            expect(component.textExercise.startDate).toBeUndefined();
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

        component.exerciseCategories.set([]);
        const newCategories = [new ExerciseCategory('Easy', undefined), new ExerciseCategory('Hard', undefined)];

        component.updateCategories(newCategories);

        expect(component.textExercise.categories).toEqual(newCategories);
        expect(component.exerciseCategories()).toEqual(newCategories);
    });

    describe('invalidReasons', () => {
        let course: Course;
        let titleChannelNameMock: MockTitleChannelNameComponent;

        const filledInExercise = () => {
            const exercise = new TextExercise(course, undefined);
            exercise.title = 'Valid title';
            exercise.channelName = 'valid-title';
            exercise.mode = ExerciseMode.INDIVIDUAL;
            exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
            exercise.maxPoints = 10;
            exercise.bonusPoints = 0;
            return exercise;
        };
        const reasonKeys = () => component.invalidReasons().map((reason) => reason.translateKey);
        const sectionOf = (title: string) => component.formSectionStatus().find((section) => section.title === `artemisApp.exercise.sections.${title}`);

        async function render(exercise: TextExercise) {
            routeData$.next({ textExercise: exercise });
            routeUrl$.next([{ path: 'new' }] as UrlSegment[]);
            fixture = TestBed.createComponent(TextExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();
            titleChannelNameMock = fixture.debugElement.query(By.directive(StubExerciseTitleChannelNameComponent)).componentInstance.titleChannelNameComponent();
            component.timelineStatus.set({ valid: true, empty: false, invalidItems: [] });
            // Read once, so that every later read has to be invalidated by the change under test rather than computed fresh.
            fixture.detectChanges();
            component.invalidReasons();
            component.formSectionStatus();
        }

        async function typeInto(selector: string, value: string) {
            const input: HTMLInputElement = fixture.debugElement.query(By.css(selector)).nativeElement;
            input.value = value;
            input.dispatchEvent(new Event('input'));
            fixture.detectChanges();
            await fixture.whenStable();
        }

        beforeEach(() => {
            course = createCourse();
        });

        it('should report the mandatory fields of an untouched creation form', async () => {
            await render(new TextExercise(course, undefined));

            expect(reasonKeys()).toContain('artemisApp.exercise.form.title.undefined');
            expect(reasonKeys()).toContain('artemisApp.exercise.form.points.undefined');
        });

        it('should report no reason for a completely filled in exercise', async () => {
            await render(filledInExercise());

            expect(component.invalidReasons()).toEqual([]);
        });

        it('should forward the timeline reasons', async () => {
            await render(filledInExercise());
            component.timelineStatus.set({
                valid: false,
                empty: true,
                invalidItems: [{ labelStringKey: 'artemisApp.exercise.dueDate', reasonKey: 'artemisApp.exercise.form.timeline.order', dateName: 'Due Date' }],
            });

            expect(component.invalidReasons()).toEqual([{ translateKey: 'artemisApp.exercise.form.timeline.order', translateValues: { dateName: 'Due Date' } }]);
        });

        it('should report a title shorter than the minimum length', async () => {
            const exercise = filledInExercise();
            exercise.title = 'ab';
            await render(exercise);

            expect(reasonKeys()).toContain('artemisApp.exercise.form.title.minlength');
        });

        it('should follow the title field when it reports a title as already used', async () => {
            await render(filledInExercise());
            expect(reasonKeys()).not.toContain('artemisApp.exercise.form.title.disallowedValue');

            titleChannelNameMock.isTitleDisallowed.set(true);

            expect(reasonKeys()).toContain('artemisApp.exercise.form.title.disallowedValue');
        });

        it('should require a channel name only while the channel field is displayed', async () => {
            const exercise = filledInExercise();
            exercise.channelName = undefined;
            await render(exercise);
            expect(reasonKeys()).toContain('artemisApp.exercise.form.channelName.empty');

            titleChannelNameMock.isChannelFieldDisplayed.set(false);

            expect(reasonKeys()).not.toContain('artemisApp.exercise.form.channelName.empty');
        });

        // The bug class a computed() can introduce: a reason that stays after the user fixed the field, or does not come back.
        it('should drop the points reason once points are typed and bring it back when they are cleared', async () => {
            const exercise = filledInExercise();
            exercise.maxPoints = undefined;
            await render(exercise);
            expect(reasonKeys()).toEqual(['artemisApp.exercise.form.points.undefined']);
            expect(sectionOf('grading')?.valid).toBe(false);

            await typeInto('#field_points', '10');
            expect(component.invalidReasons()).toEqual([]);
            expect(sectionOf('grading')?.valid).toBe(true);
            expect(component.textExercise.maxPoints).toBe(10);

            await typeInto('#field_points', '');
            expect(reasonKeys()).toEqual(['artemisApp.exercise.form.points.undefined']);
            expect(sectionOf('grading')?.valid).toBe(false);
        });

        it('should follow the bonus points as they are typed', async () => {
            await render(filledInExercise());

            await typeInto('#field_bonusPoints', '-1');
            expect(reasonKeys()).toEqual(['artemisApp.exercise.form.bonusPoints.customMin']);

            await typeInto('#field_bonusPoints', '3');
            expect(component.invalidReasons()).toEqual([]);
        });

        it('should follow the team config component when it changes the mode and the team size', async () => {
            await render(filledInExercise());
            const teamConfig: StubTeamConfigFormGroupComponent = fixture.debugElement.query(By.directive(StubTeamConfigFormGroupComponent)).componentInstance;

            teamConfig.changeTeamConfig(ExerciseMode.TEAM, undefined);
            expect(reasonKeys()).toEqual(['artemisApp.exercise.form.minTeamSize.required']);
            expect(sectionOf('mode')?.valid).toBe(false);

            teamConfig.changeTeamConfig(ExerciseMode.TEAM, 2);
            expect(component.invalidReasons()).toEqual([]);
            expect(sectionOf('mode')?.valid).toBe(true);
        });

        it('should follow the title component when it writes a new title', async () => {
            await render(filledInExercise());
            const titleComponent: StubExerciseTitleChannelNameComponent = fixture.debugElement.query(By.directive(StubExerciseTitleChannelNameComponent)).componentInstance;

            component.textExercise.title = 'ab';
            titleComponent.exerciseChange.emit(component.textExercise);

            expect(reasonKeys()).toEqual(['artemisApp.exercise.form.title.minlength']);
            expect(sectionOf('general')?.valid).toBe(false);
        });

        it('should report an example solution publication date before the due date, and stop once it is moved', async () => {
            await render(filledInExercise());
            const timeline: ExerciseTimelineComponent = fixture.debugElement.query(By.directive(ExerciseTimelineComponent)).componentInstance;
            const dueDate = dayjs().add(2, 'days');

            timeline.dueDate.set(dueDate);
            timeline.exampleSolutionPublicationDate.set(dueDate.subtract(1, 'day'));
            expect(reasonKeys()).toContain('artemisApp.exercise.exampleSolutionPublicationDateError');

            timeline.exampleSolutionPublicationDate.set(dueDate.add(1, 'day'));
            expect(reasonKeys()).not.toContain('artemisApp.exercise.exampleSolutionPublicationDateError');
        });

        it('should name the plagiarism control that is invalid, also when another one takes its place', async () => {
            await render(filledInExercise());
            const plagiarism: StubExerciseUpdatePlagiarismComponent = fixture.debugElement.query(By.directive(StubExerciseUpdatePlagiarismComponent)).componentInstance;

            plagiarism.isFormValid.set(false);
            plagiarism.invalidControlNames.set(['similarityThreshold']);
            expect(reasonKeys()).toEqual(['artemisApp.exercise.form.continuousPlagiarismControl.similarityThreshold.pattern']);

            plagiarism.invalidControlNames.set(['minimumScore']);
            expect(reasonKeys()).toEqual(['artemisApp.exercise.form.continuousPlagiarismControl.minimumScore.customMin']);
            expect(sectionOf('grading')?.valid).toBe(false);
        });

        it('should mark the problem and solution sections empty until they are written', async () => {
            await render(filledInExercise());
            expect(sectionOf('problem')?.empty).toBe(true);

            const editors = fixture.debugElement.queryAll(By.directive(MockMarkdownEditorMonacoComponent));
            editors[0].triggerEventHandler('markdownChange', 'A problem');
            editors[1].triggerEventHandler('markdownChange', 'A solution');

            expect(sectionOf('problem')?.empty).toBe(false);
            expect(sectionOf('solution')?.empty).toBe(false);
            expect(component.textExercise.problemStatement).toBe('A problem');
        });
        it('should trim the title through the state when saving, so that the reasons follow a failed save', async () => {
            const exercise = filledInExercise();
            exercise.title = 'ab ';
            await render(exercise);
            expect(component.invalidReasons()).toEqual([]);
            vi.spyOn(TestBed.inject(TextExerciseService), 'create').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));

            component.save();

            expect(exercise.title).toBe('ab');
            expect(reasonKeys()).toEqual(['artemisApp.exercise.form.title.minlength']);
        });

        it('should edit the exercise in place, so that the child components keep the object they were given', async () => {
            const exercise = filledInExercise();
            await render(exercise);
            const titleComponent: StubExerciseTitleChannelNameComponent = fixture.debugElement.query(By.directive(StubExerciseTitleChannelNameComponent)).componentInstance;
            const teamConfig: StubTeamConfigFormGroupComponent = fixture.debugElement.query(By.directive(StubTeamConfigFormGroupComponent)).componentInstance;

            await typeInto('#field_points', '7');
            await typeInto('#field_bonusPoints', '2');

            expect(component.textExercise).toBe(exercise);
            expect(titleComponent.exercise()).toBe(exercise);
            expect(teamConfig.exercise()).toBe(exercise);
            expect(exercise.maxPoints).toBe(7);
            expect(exercise.bonusPoints).toBe(2);
        });
    });

    describe('bonus points as the score mode changes', () => {
        const bonusInput = () => fixture.debugElement.query(By.css('#field_bonusPoints'));
        const gradingSectionIsValid = () => component.formSectionStatus().find((section) => section.title === 'artemisApp.exercise.sections.grading')?.valid;

        /** Switches the score mode the way the picker does: it emits the new mode. */
        async function switchScoreMode(mode: IncludedInOverallScore): Promise<void> {
            const picker = fixture.debugElement.query(By.directive(IncludedInOverallScorePickerComponent));
            picker.componentInstance.includedInOverallScoreChange.emit(mode);
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();
        }

        beforeEach(async () => {
            const exercise = createExercise(createCourse());
            exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
            exercise.maxPoints = 10;
            exercise.bonusPoints = 0;
            routeData$.next({ textExercise: exercise });
            fixture = TestBed.createComponent(TextExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();
            // Exam mode short-circuits the plagiarism and timeline half of the grading verdict, leaving the points
            // and the bonus points - which is the part these tests are about.
            component.isExamMode.set(true);
            fixture.detectChanges();
        });

        it('should not call the grading section invalid just because the field is absent', async () => {
            // Started from invalid on purpose. Asserting "valid" against a baseline that is already valid passes
            // whether or not anything recomputed, which is exactly how this test first proved nothing.
            const input: HTMLInputElement = bonusInput().nativeElement;
            input.value = '99999';
            input.dispatchEvent(new Event('input'));
            fixture.detectChanges();
            await fixture.whenStable();
            expect(gradingSectionIsValid()).toBe(false);

            await switchScoreMode(IncludedInOverallScore.NOT_INCLUDED);

            expect(bonusInput()).toBeNull();
            expect(gradingSectionIsValid()).toBe(true);
        });

        it('should keep following the field after it is rebuilt, not the one it first saw', async () => {
            await switchScoreMode(IncludedInOverallScore.NOT_INCLUDED);
            await switchScoreMode(IncludedInOverallScore.INCLUDED_COMPLETELY);
            expect(bonusInput()).not.toBeNull();
            expect(gradingSectionIsValid()).toBe(true);

            const input: HTMLInputElement = bonusInput().nativeElement;
            input.value = '99999';
            input.dispatchEvent(new Event('input'));
            fixture.detectChanges();
            await fixture.whenStable();

            expect(gradingSectionIsValid()).toBe(false);
        });
    });
});
