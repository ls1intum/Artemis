/**
 * Vitest tests for ModelingExerciseUpdateComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { BehaviorSubject, of } from 'rxjs';
import { ActivatedRoute, Data, Params, Router, UrlSegment } from '@angular/router';

import { ModelingExerciseUpdateComponent } from 'app/modeling/manage/update/modeling-exercise-update.component';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent, MockDirective } from 'ng-mocks';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { UMLDiagramType } from '@tumaet/apollon';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import * as Utils from 'app/exercise/course-exercises/course-utils';
import { Component, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
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
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercise/feedback-suggestion/exercise-feedback-suggestion-options.component';
import { DialogService } from 'primeng/dynamicdialog';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { ExerciseUpdateWarningService } from 'app/exercise/exercise-update-warning/exercise-update-warning.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';

// Mock ResizeObserver globally
class MockResizeObserverClass {
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
    constructor(_callback?: ResizeObserverCallback) {}
}
global.ResizeObserver = MockResizeObserverClass as unknown as typeof ResizeObserver;

// Stub for TitleChannelNameComponent to satisfy viewChild.required
@Component({ selector: 'jhi-title-channel-name', template: '' })
class StubTitleChannelNameComponent {
    isValid = signal(true);
}

// Stub for ExerciseTitleChannelNameComponent - must match the actual component's interface
@Component({
    selector: 'jhi-exercise-title-channel-name',
    template: '<jhi-title-channel-name />',
    imports: [StubTitleChannelNameComponent],
})
class StubExerciseTitleChannelNameComponent {
    exercise = input<ModelingExercise>();
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
    readonly titleChannelNameComponent = viewChild.required(StubTitleChannelNameComponent);
}

// Stub for ModelingEditorComponent
@Component({ selector: 'jhi-modeling-editor', template: '' })
class StubModelingEditorComponent {
    umlModel = input<unknown>();
    diagramType = input<unknown>();
    readOnly = input<boolean>(false);
    resizeOptions = input<unknown>();
    withExplanation = input<boolean>(false);
    onModelChanged = output<unknown>();
    apollonEditor = { nextRender: Promise.resolve() };

    getCurrentModel() {
        return { elements: {}, relationships: {}, version: '3.0.0' };
    }
}

// Stub for MarkdownEditorMonacoComponent
@Component({ selector: 'jhi-markdown-editor-monaco', template: '' })
class StubMarkdownEditorMonacoComponent {
    markdown = input<string>('');
    domainActions = input<unknown[]>([]);
    markdownChange = output<string>();
}

describe('ModelingExerciseUpdateComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: ModelingExerciseUpdateComponent;
    let fixture: ComponentFixture<ModelingExerciseUpdateComponent>;
    let service: ModelingExerciseService;
    let courseService: CourseManagementService;
    let exerciseService: ExerciseService;
    let calendarService: CalendarService;

    const categories = [new ExerciseCategory('testCat', undefined), new ExerciseCategory('testCat2', undefined)];
    const categoriesStringified = categories.map((cat) => JSON.stringify(cat));

    let routeData$: BehaviorSubject<Data>;
    let routeUrl$: BehaviorSubject<UrlSegment[]>;
    let routeParams$: BehaviorSubject<Params>;

    const createCourse = (id = 1): Course => {
        const course = new Course();
        course.id = id;
        return course;
    };

    const createModelingExercise = (course?: Course, exerciseGroup?: ExerciseGroup): ModelingExercise => {
        const exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, exerciseGroup);
        exercise.channelName = 'test';
        return exercise;
    };

    beforeEach(async () => {
        const course = createCourse();
        const modelingExercise = createModelingExercise(course);
        modelingExercise.id = 123;

        routeData$ = new BehaviorSubject({ modelingExercise });
        routeUrl$ = new BehaviorSubject([{ path: 'new' }] as UrlSegment[]);
        routeParams$ = new BehaviorSubject({ courseId: 1 });

        await TestBed.configureTestingModule({
            imports: [ModelingExerciseUpdateComponent, TranslateModule.forRoot()],
            providers: [
                LocalStorageService,
                SessionStorageService,
                {
                    provide: ActivatedRoute,
                    useValue: {
                        data: routeData$.asObservable(),
                        url: routeUrl$.asObservable(),
                        params: routeParams$.asObservable(),
                        snapshot: {
                            paramMap: {
                                get: () => 'mockValue',
                            },
                        },
                    },
                },
                { provide: Router, useClass: MockRouter },
                { provide: ProfileService, useClass: MockProfileService },
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
                    provide: CalendarService,
                    useValue: {
                        reloadEvents: vi.fn(),
                    },
                },
                {
                    provide: DialogService,
                    useValue: {
                        open: vi.fn(),
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
                        hasOpenDialogs: vi.fn().mockReturnValue(false),
                    },
                },
                {
                    provide: ExerciseGroupService,
                    useValue: {
                        find: vi.fn().mockReturnValue(of(new HttpResponse({ body: new ExerciseGroup() }))),
                    },
                },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(ModelingExerciseUpdateComponent, {
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
                        MockComponent(ExerciseFeedbackSuggestionOptionsComponent),
                        StubMarkdownEditorMonacoComponent,
                        StubModelingEditorComponent,
                    ],
                },
            })
            .compileComponents();

        service = TestBed.inject(ModelingExerciseService);
        courseService = TestBed.inject(CourseManagementService);
        exerciseService = TestBed.inject(ExerciseService);
        calendarService = TestBed.inject(CalendarService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        if (fixture) {
            fixture.destroy();
        }
    });

    describe('save', () => {
        describe('new exercise', () => {
            beforeEach(async () => {
                const course = createCourse();
                const modelingExercise = createModelingExercise(course);

                routeData$.next({ modelingExercise });
                routeUrl$.next([{ path: 'exercise-groups' } as UrlSegment]);

                fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
                comp = fixture.componentInstance;
                fixture.detectChanges();
                await fixture.whenStable();
            });

            it('should call create service on save for new entity and refresh calendar events', async () => {
                // GIVEN
                const createdExercise = { ...comp.modelingExercise, id: 789 };
                vi.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: createdExercise })));
                const refreshSpy = vi.spyOn(calendarService, 'reloadEvents');

                // WHEN
                comp.save();
                await fixture.whenStable();

                // THEN
                expect(service.create).toHaveBeenCalledWith(expect.objectContaining({ channelName: 'test' }));
                expect(refreshSpy).toHaveBeenCalledOnce();
                expect(comp.isSaving).toBe(false);
            });
        });

        describe('existing exercise', () => {
            beforeEach(async () => {
                const course = createCourse();
                const modelingExercise = createModelingExercise(course);
                modelingExercise.id = 123;

                routeData$.next({ modelingExercise });
                routeUrl$.next([{ path: 'exercise-groups' } as UrlSegment]);

                fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
                comp = fixture.componentInstance;
                fixture.detectChanges();
                await fixture.whenStable();
            });

            it('should call update service on save for existing entity and refresh calendar events', async () => {
                // GIVEN
                const updatedExercise = { ...comp.modelingExercise };
                vi.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: updatedExercise })));
                const refreshSpy = vi.spyOn(calendarService, 'reloadEvents');

                // WHEN
                comp.save();
                await fixture.whenStable();

                // THEN
                expect(service.update).toHaveBeenCalledWith(expect.objectContaining({ id: 123 }), {});
                expect(refreshSpy).toHaveBeenCalledOnce();
                expect(comp.isSaving).toBe(false);
            });
        });
    });

    describe('ngOnInit in import mode: Course to Course', () => {
        const courseIdImportingCourse = 1;

        beforeEach(async () => {
            const course = createCourse(123);
            const modelingExercise = createModelingExercise(course);
            modelingExercise.id = 1;
            modelingExercise.releaseDate = dayjs();
            modelingExercise.dueDate = dayjs();
            modelingExercise.assessmentDueDate = dayjs();

            routeData$.next({ modelingExercise });
            routeUrl$.next([{ path: 'import' } as UrlSegment]);
            routeParams$.next({ courseId: courseIdImportingCourse });
        });

        it('should set isImport and remove all dates', async () => {
            vi.spyOn(courseService, 'findAllCategoriesOfCourse').mockReturnValue(of(new HttpResponse({ body: categoriesStringified })));
            vi.spyOn(exerciseService, 'convertExerciseCategoriesAsStringFromServer').mockReturnValue(categories);

            fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
            comp = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(comp.isImport).toBe(true);
            expect(comp.isExamMode).toBe(false);
            expect(comp.modelingExercise.assessmentDueDate).toBeUndefined();
            expect(comp.modelingExercise.releaseDate).toBeUndefined();
            expect(comp.modelingExercise.dueDate).toBeUndefined();
            expect(courseService.findAllCategoriesOfCourse).toHaveBeenLastCalledWith(courseIdImportingCourse);
            expect(comp.existingCategories).toEqual(categories);
        });

        it('should load exercise categories', async () => {
            const loadExerciseCategoriesSpy = vi.spyOn(Utils, 'loadCourseExerciseCategories');

            fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
            comp = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(loadExerciseCategoriesSpy).toHaveBeenCalledOnce();
        });
    });

    describe('ngOnInit in import mode: Exam to Course', () => {
        const courseId = 1;

        beforeEach(async () => {
            const exerciseGroup = new ExerciseGroup();
            exerciseGroup.exam = new Exam();
            exerciseGroup.exam.course = createCourse(courseId);
            const modelingExercise = createModelingExercise(undefined, exerciseGroup);
            modelingExercise.id = 1;
            modelingExercise.releaseDate = dayjs();
            modelingExercise.dueDate = dayjs();
            modelingExercise.assessmentDueDate = dayjs();

            routeData$.next({ modelingExercise });
            routeUrl$.next([{ path: 'import' } as UrlSegment]);
            routeParams$.next({ courseId });
        });

        it('should set isImport and remove all dates', async () => {
            vi.spyOn(courseService, 'findAllCategoriesOfCourse').mockReturnValue(of(new HttpResponse({ body: categoriesStringified })));
            vi.spyOn(exerciseService, 'convertExerciseCategoriesAsStringFromServer').mockReturnValue(categories);

            fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
            comp = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(comp.isImport).toBe(true);
            expect(comp.isExamMode).toBe(false);
            expect(comp.modelingExercise.assessmentDueDate).toBeUndefined();
            expect(comp.modelingExercise.releaseDate).toBeUndefined();
            expect(comp.modelingExercise.dueDate).toBeUndefined();
            expect(courseService.findAllCategoriesOfCourse).toHaveBeenLastCalledWith(courseId);
            expect(comp.existingCategories).toEqual(categories);
        });
    });

    describe('ngOnInit in import mode: Course to Exam', () => {
        const groupId = 1;

        beforeEach(async () => {
            const modelingExercise = createModelingExercise(createCourse());
            modelingExercise.id = 1;
            modelingExercise.releaseDate = dayjs();
            modelingExercise.dueDate = dayjs();
            modelingExercise.assessmentDueDate = dayjs();

            routeData$.next({ modelingExercise });
            routeUrl$.next([{ path: 'exercise-groups' } as UrlSegment, { path: 'import' } as UrlSegment]);
            routeParams$.next({ groupId });
        });

        it('should set isImport and isExamMode and remove all dates', async () => {
            fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
            comp = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(comp.isImport).toBe(true);
            expect(comp.isExamMode).toBe(true);
            expect(comp.modelingExercise.course).toBeUndefined();
            expect(comp.modelingExercise.assessmentDueDate).toBeUndefined();
            expect(comp.modelingExercise.releaseDate).toBeUndefined();
            expect(comp.modelingExercise.dueDate).toBeUndefined();
        });
    });

    describe('ngOnInit in import mode: Exam to Exam', () => {
        const groupId = 1;

        beforeEach(async () => {
            const exerciseGroup = new ExerciseGroup();
            const modelingExercise = createModelingExercise(undefined, exerciseGroup);
            modelingExercise.id = 1;
            modelingExercise.releaseDate = dayjs();
            modelingExercise.dueDate = dayjs();
            modelingExercise.assessmentDueDate = dayjs();

            routeData$.next({ modelingExercise });
            routeUrl$.next([{ path: 'exercise-groups' } as UrlSegment, { path: 'import' } as UrlSegment]);
            routeParams$.next({ groupId });
        });

        it('should set isImport and isExamMode and remove all dates', async () => {
            fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
            comp = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(comp.isImport).toBe(true);
            expect(comp.isExamMode).toBe(true);
            expect(comp.modelingExercise.assessmentDueDate).toBeUndefined();
            expect(comp.modelingExercise.releaseDate).toBeUndefined();
            expect(comp.modelingExercise.dueDate).toBeUndefined();
        });
    });

    it('should update categories with given ones', async () => {
        fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
        comp = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        const newCategories = [new ExerciseCategory('newCat1', undefined), new ExerciseCategory('newCat2', undefined)];
        comp.updateCategories(newCategories);

        expect(comp.modelingExercise.categories).toEqual(newCategories);
    });

    it('should call exercise service to validate date', async () => {
        fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
        comp = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        vi.spyOn(exerciseService, 'validateDate');
        comp.validateDate();

        expect(exerciseService.validateDate).toHaveBeenCalledWith(comp.modelingExercise);
    });

    it('should set assessmentType to manual in exam mode', async () => {
        fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
        comp = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        comp.isExamMode = true;
        comp.semiAutomaticAssessmentAvailable = false;
        comp.diagramTypeChanged();

        expect(comp.modelingExercise.assessmentType).toEqual(AssessmentType.MANUAL);
    });

    it('should updateCategories properly by making category available for selection again when removing it', async () => {
        fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
        comp = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        comp.exerciseCategories = [];
        const newCategories = [new ExerciseCategory('Easy', undefined), new ExerciseCategory('Hard', undefined)];
        comp.updateCategories(newCategories);

        expect(comp.modelingExercise.categories).toEqual(newCategories);
        expect(comp.exerciseCategories).toEqual(newCategories);
    });

    it('should properly clean up subscriptions on destroy', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {}); // Suppress console errors

        fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
        comp = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        // Call ngOnDestroy and verify subscriptions are cleaned up
        comp.ngOnDestroy();

        expect(comp.bonusPointsSubscription?.closed ?? true).toBe(true);
        expect(comp.pointsSubscription?.closed ?? true).toBe(true);
    });

    describe('handleEnterKeyNavigation', () => {
        beforeEach(async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});

            fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
            comp = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();
        });

        it('should prevent default and stop propagation', () => {
            const mockEvent = {
                preventDefault: vi.fn(),
                stopPropagation: vi.fn(),
            } as unknown as Event;

            comp.handleEnterKeyNavigation(mockEvent);

            expect(mockEvent.preventDefault).toHaveBeenCalledOnce();
            expect(mockEvent.stopPropagation).toHaveBeenCalledOnce();
        });

        it('should not navigate when focused on TEXTAREA', () => {
            const textarea = document.createElement('textarea');
            document.body.appendChild(textarea);
            textarea.focus();

            const mockEvent = {
                preventDefault: vi.fn(),
                stopPropagation: vi.fn(),
            } as unknown as Event;

            comp.handleEnterKeyNavigation(mockEvent);

            expect(mockEvent.preventDefault).toHaveBeenCalledOnce();
            document.body.removeChild(textarea);
        });

        it('should not navigate when focused on contentEditable element', () => {
            const editableDiv = document.createElement('div');
            editableDiv.contentEditable = 'true';
            document.body.appendChild(editableDiv);
            editableDiv.focus();

            const mockEvent = {
                preventDefault: vi.fn(),
                stopPropagation: vi.fn(),
            } as unknown as Event;

            comp.handleEnterKeyNavigation(mockEvent);

            expect(mockEvent.preventDefault).toHaveBeenCalledOnce();
            document.body.removeChild(editableDiv);
        });
    });
});
