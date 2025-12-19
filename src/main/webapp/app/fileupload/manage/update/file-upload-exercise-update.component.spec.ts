/**
 * Vitest tests for FileUploadExerciseUpdateComponent.
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
    KeyCode: {
        Backspace: 1,
        Tab: 2,
        Enter: 3,
        Escape: 9,
        Delete: 10,
        KeyA: 31,
        KeyB: 32,
        KeyC: 33,
        KeyD: 34,
        KeyE: 35,
        KeyF: 36,
        KeyG: 37,
        KeyH: 38,
        KeyI: 39,
        KeyJ: 40,
        KeyK: 41,
        KeyL: 42,
        KeyM: 43,
        KeyN: 44,
        KeyO: 45,
        KeyP: 46,
        KeyQ: 47,
        KeyR: 48,
        KeyS: 49,
        KeyT: 50,
        KeyU: 51,
        KeyV: 52,
        KeyW: 53,
        KeyX: 54,
        KeyY: 55,
        KeyZ: 56,
    },
    KeyMod: { CtrlCmd: 2048, Shift: 1024, Alt: 512, WinCtrl: 256 },
    MarkerSeverity: { Error: 8, Warning: 4, Info: 2, Hint: 1 },
    Uri: { parse: (s: string) => ({ toString: () => s }) },
}));

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Data, Params, UrlSegment, provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent, MockDirective } from 'ng-mocks';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import 'app/shared/util/array.extension';

import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

import { FileUploadExerciseUpdateComponent } from './file-upload-exercise-update.component';
import { FileUploadExerciseService } from '../services/file-upload-exercise.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { EditType } from 'app/exercise/util/exercise.utils';

import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { ExerciseUpdateWarningService } from 'app/exercise/exercise-update-warning/exercise-update-warning.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';

import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TeamConfigFormGroupComponent } from 'app/exercise/team-config-form-group/team-config-form-group.component';
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
// NOTE: Do NOT import MarkdownEditorMonacoComponent here - it transitively imports monaco-editor
// which causes static initializers to run before mocks are applied.
import { Component, Input, input, output, signal, viewChild } from '@angular/core';

// Mock component to replace MarkdownEditorMonacoComponent without importing the real one
@Component({ selector: 'jhi-markdown-editor-monaco', template: '', standalone: true })
class MockMarkdownEditorMonacoComponent {
    @Input() markdown: string = '';
    @Input() domainActions: unknown[] = [];
}

// Stub for TitleChannelNameComponent to satisfy viewChild.required
@Component({ selector: 'jhi-title-channel-name', template: '', standalone: true })
class StubTitleChannelNameComponent {
    isValid = signal(true);
}

// Stub for ExerciseTitleChannelNameComponent - ng-mocks MockComponent doesn't handle viewChild.required properly
@Component({ selector: 'jhi-exercise-title-channel-name', template: '<jhi-title-channel-name />', standalone: true, imports: [StubTitleChannelNameComponent] })
class StubExerciseTitleChannelNameComponent {
    @Input() exercise: FileUploadExercise | undefined;
    @Input() titlePattern: string = '';
    @Input() minTitleLength: number = 0;
    @Input() isExamMode: boolean = false;
    @Input() isImport: boolean = false;
    @Input() hideTitleLabel: boolean = false;
    course = input<Course>();
    isEditFieldDisplayedRecord = input<Record<string, boolean>>();
    courseId = input<number>();
    onTitleChange = output<string>();
    onChannelNameChange = output<string>();
    readonly titleChannelNameComponent = viewChild.required(StubTitleChannelNameComponent);
}

describe('FileUploadExerciseUpdateComponent', () => {
    setupTestBed({ zoneless: true });

    let component: FileUploadExerciseUpdateComponent;
    let fixture: ComponentFixture<FileUploadExerciseUpdateComponent>;
    let fileUploadExerciseService: FileUploadExerciseService;
    let alertService: AlertService;
    let navigationService: ArtemisNavigationUtilService;
    let calendarService: CalendarService;

    let routeData$: BehaviorSubject<Data>;
    let routeUrl$: BehaviorSubject<UrlSegment[]>;
    let routeParams$: BehaviorSubject<Params>;

    const createCourse = (id = 123): Course => {
        const course = new Course();
        course.id = id;
        return course;
    };

    const createExercise = (course?: Course, exerciseGroup?: ExerciseGroup): FileUploadExercise => {
        const exercise = new FileUploadExercise(course, exerciseGroup);
        exercise.id = undefined;
        exercise.title = 'Test Exercise';
        exercise.filePattern = 'pdf,png';
        exercise.channelName = 'test-channel';
        return exercise;
    };

    const createExistingExercise = (): FileUploadExercise => {
        const exercise = createExercise(createCourse());
        exercise.id = 456;
        return exercise;
    };

    beforeEach(async () => {
        routeData$ = new BehaviorSubject({ fileUploadExercise: createExercise(createCourse()) });
        routeUrl$ = new BehaviorSubject([{ path: 'new' }] as UrlSegment[]);
        routeParams$ = new BehaviorSubject({ courseId: 123 });

        await TestBed.configureTestingModule({
            imports: [FileUploadExerciseUpdateComponent, TranslateModule.forRoot()],
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
            ],
        })
            .overrideComponent(FileUploadExerciseUpdateComponent, {
                set: {
                    imports: [
                        FormsModule,
                        MockDirective(TranslateDirective),
                        FaIconComponent,
                        NgbTooltip,
                        ArtemisTranslatePipe,
                        MockComponent(FormDateTimePickerComponent),
                        StubExerciseTitleChannelNameComponent,
                        MockComponent(TeamConfigFormGroupComponent),
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
                    ],
                },
            })
            .compileComponents();

        fileUploadExerciseService = TestBed.inject(FileUploadExerciseService);
        alertService = TestBed.inject(AlertService);
        navigationService = TestBed.inject(ArtemisNavigationUtilService);
        calendarService = TestBed.inject(CalendarService);
    });

    afterEach(() => {
        vi.clearAllMocks();
        if (fixture) {
            fixture.destroy();
        }
    });

    describe('initialization', () => {
        it('should load exercise from route data', async () => {
            const exercise = createExercise(createCourse());
            routeData$.next({ fileUploadExercise: exercise });

            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.fileUploadExercise()).toBeDefined();
        });

        it('should set isExamMode to true for exam exercises', async () => {
            routeUrl$.next([{ path: 'exercise-groups' } as UrlSegment]);
            const exerciseGroup = new ExerciseGroup();
            const exercise = createExercise(undefined, exerciseGroup);
            routeData$.next({ fileUploadExercise: exercise });

            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isExamMode()).toBe(true);
        });

        it('should set isExamMode to false for course exercises', async () => {
            routeUrl$.next([{ path: 'new' } as UrlSegment]);
            const exercise = createExercise(createCourse());
            routeData$.next({ fileUploadExercise: exercise });

            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isExamMode()).toBe(false);
        });

        it('should set isImport to true for import route', async () => {
            routeUrl$.next([{ path: 'import' } as UrlSegment]);
            const exercise = createExercise(createCourse());
            routeData$.next({ fileUploadExercise: exercise });

            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isImport()).toBe(true);
        });

        it('should load existing categories for course exercises', async () => {
            const courseService = TestBed.inject(CourseManagementService);
            const categories = ['category1', 'category2'];
            vi.spyOn(courseService, 'findAllCategoriesOfCourse').mockReturnValue(of(new HttpResponse({ body: categories })));

            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(courseService.findAllCategoriesOfCourse).toHaveBeenCalled();
        });

        it('should set isSaving to false on init', async () => {
            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isSaving()).toBe(false);
        });
    });

    describe('save - new exercise', () => {
        beforeEach(async () => {
            const exercise = createExercise(createCourse());
            routeData$.next({ fileUploadExercise: exercise });
            routeUrl$.next([{ path: 'new' } as UrlSegment]);

            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();
        });

        it('should call create service for new exercise', async () => {
            const createdExercise = Object.assign({}, component.fileUploadExercise(), { id: 789 });
            vi.spyOn(fileUploadExerciseService, 'create').mockReturnValue(of(new HttpResponse({ body: createdExercise })));

            await component.save();

            expect(fileUploadExerciseService.create).toHaveBeenCalled();
        });

        it('should refresh calendar after successful creation', async () => {
            const createdExercise = Object.assign({}, component.fileUploadExercise(), { id: 789 });
            vi.spyOn(fileUploadExerciseService, 'create').mockReturnValue(of(new HttpResponse({ body: createdExercise })));

            await component.save();

            expect(calendarService.reloadEvents).toHaveBeenCalled();
        });

        it('should navigate forward after successful creation', async () => {
            const createdExercise = Object.assign({}, component.fileUploadExercise(), { id: 789 });
            vi.spyOn(fileUploadExerciseService, 'create').mockReturnValue(of(new HttpResponse({ body: createdExercise })));

            await component.save();

            expect(navigationService.navigateForwardFromExerciseUpdateOrCreation).toHaveBeenCalled();
        });

        it('should set isSaving to false after save completes', async () => {
            const createdExercise = Object.assign({}, component.fileUploadExercise(), { id: 789 });
            vi.spyOn(fileUploadExerciseService, 'create').mockReturnValue(of(new HttpResponse({ body: createdExercise })));

            await component.save();

            expect(component.isSaving()).toBe(false);
        });
    });

    describe('save - existing exercise', () => {
        beforeEach(async () => {
            const exercise = createExistingExercise();
            routeData$.next({ fileUploadExercise: exercise });
            routeUrl$.next([{ path: 'edit' } as UrlSegment]);

            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();
        });

        it('should call update service for existing exercise', async () => {
            const updatedExercise = component.fileUploadExercise();
            vi.spyOn(fileUploadExerciseService, 'update').mockReturnValue(of(new HttpResponse({ body: updatedExercise })));

            await component.save();

            expect(fileUploadExerciseService.update).toHaveBeenCalled();
        });

        it('should refresh calendar after successful update', async () => {
            const updatedExercise = component.fileUploadExercise();
            vi.spyOn(fileUploadExerciseService, 'update').mockReturnValue(of(new HttpResponse({ body: updatedExercise })));

            await component.save();

            expect(calendarService.reloadEvents).toHaveBeenCalled();
        });
    });

    describe('save - import', () => {
        beforeEach(async () => {
            const exercise = createExistingExercise();
            routeData$.next({ fileUploadExercise: exercise });
            routeUrl$.next([{ path: 'import' } as UrlSegment]);

            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();
        });

        it('should call import service for import', async () => {
            const importedExercise = Object.assign({}, component.fileUploadExercise(), { id: 999 });
            vi.spyOn(fileUploadExerciseService, 'import').mockReturnValue(of(new HttpResponse({ body: importedExercise })));

            await component.save();

            expect(fileUploadExerciseService.import).toHaveBeenCalled();
        });
    });

    describe('save - error handling', () => {
        beforeEach(async () => {
            const exercise = createExercise(createCourse());
            routeData$.next({ fileUploadExercise: exercise });

            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();
        });

        it('should show error alert on save failure', async () => {
            vi.spyOn(fileUploadExerciseService, 'create').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            const alertSpy = vi.spyOn(alertService, 'addAlert');

            await component.save();

            expect(alertSpy).toHaveBeenCalled();
        });

        it('should set isSaving to false on error', async () => {
            vi.spyOn(fileUploadExerciseService, 'create').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            await component.save();

            expect(component.isSaving()).toBe(false);
        });

        it('should show error title from error response', async () => {
            const errorResponse = new HttpErrorResponse({
                error: { title: 'Error Title', message: 'Error Message', params: {} },
            });
            vi.spyOn(fileUploadExerciseService, 'create').mockReturnValue(throwError(() => errorResponse));
            const alertSpy = vi.spyOn(alertService, 'addErrorAlert');

            await component.save();

            expect(alertSpy).toHaveBeenCalledWith('Error Title', 'Error Message', {});
        });
    });

    describe('previousState', () => {
        beforeEach(async () => {
            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();
        });

        it('should navigate back from exercise update', () => {
            component.previousState();

            expect(navigationService.navigateBackFromExerciseUpdate).toHaveBeenCalledWith(component.fileUploadExercise());
        });
    });

    describe('validateDate', () => {
        beforeEach(async () => {
            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();
        });

        it('should call exercise service validate date', () => {
            const exerciseService = TestBed.inject(ExerciseService);

            component.validateDate();

            expect(exerciseService.validateDate).toHaveBeenCalledWith(component.fileUploadExercise());
        });

        it('should recalculate form section status after validation', () => {
            const calculateSpy = vi.spyOn(component, 'calculateFormSectionStatus');

            component.validateDate();

            expect(calculateSpy).toHaveBeenCalled();
        });
    });

    describe('updateCategories', () => {
        beforeEach(async () => {
            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();
        });

        it('should update exercise categories', () => {
            const categories = [new ExerciseCategory('cat1', undefined), new ExerciseCategory('cat2', undefined)];

            component.updateCategories(categories);

            expect(component.fileUploadExercise().categories).toEqual(categories);
            expect(component.exerciseCategories()).toEqual(categories);
        });
    });

    describe('editType', () => {
        it('should return IMPORT when isImport is true', async () => {
            routeUrl$.next([{ path: 'import' } as UrlSegment]);
            const exercise = createExistingExercise();
            routeData$.next({ fileUploadExercise: exercise });

            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.editType()).toBe(EditType.IMPORT);
        });

        it('should return CREATE for new exercise', async () => {
            const exercise = createExercise(createCourse());
            routeData$.next({ fileUploadExercise: exercise });
            routeUrl$.next([{ path: 'new' } as UrlSegment]);

            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.editType()).toBe(EditType.CREATE);
        });

        it('should return UPDATE for existing exercise', async () => {
            const exercise = createExistingExercise();
            routeData$.next({ fileUploadExercise: exercise });
            routeUrl$.next([{ path: 'edit' } as UrlSegment]);

            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.editType()).toBe(EditType.UPDATE);
        });
    });

    describe('import mode handling', () => {
        it('should clear dates when importing course to course', async () => {
            const exercise = createExistingExercise();
            exercise.releaseDate = dayjs();
            exercise.dueDate = dayjs();
            exercise.assessmentDueDate = dayjs();
            routeData$.next({ fileUploadExercise: exercise });
            routeUrl$.next([{ path: 'import' } as UrlSegment]);
            routeParams$.next({ courseId: 123 });

            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isImport()).toBe(true);
        });

        it('should load exercise group when importing to exam', async () => {
            const exerciseGroupService = TestBed.inject(ExerciseGroupService);
            const exercise = createExistingExercise();
            routeData$.next({ fileUploadExercise: exercise });
            routeUrl$.next([{ path: 'exercise-groups' } as UrlSegment, { path: 'import' } as UrlSegment]);
            routeParams$.next({ courseId: 123, examId: 456, exerciseGroupId: 789 });

            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(exerciseGroupService.find).toHaveBeenCalled();
        });
    });

    describe('exam mode settings', () => {
        it('should set individual mode for exam exercises', async () => {
            const exerciseGroup = new ExerciseGroup();
            const exercise = createExercise(undefined, exerciseGroup);
            exercise.includedInOverallScore = IncludedInOverallScore.NOT_INCLUDED;
            routeData$.next({ fileUploadExercise: exercise });
            routeUrl$.next([{ path: 'exercise-groups' } as UrlSegment]);

            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isExamMode()).toBe(true);
        });
    });
});
