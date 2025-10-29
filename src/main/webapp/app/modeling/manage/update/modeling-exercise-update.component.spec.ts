import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { Subject, of } from 'rxjs';
import { ActivatedRoute, Router, UrlSegment } from '@angular/router';

import { ModelingExerciseUpdateComponent } from 'app/modeling/manage/update/modeling-exercise-update.component';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import * as Utils from 'app/exercise/course-exercises/course-utils';
import { NgModel } from '@angular/forms';
import { TeamConfigFormGroupComponent } from 'app/exercise/team-config-form-group/team-config-form-group.component';
import { UMLDiagramType } from '@tumaet/apollon';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';

describe('ModelingExerciseUpdateComponent', () => {
    let comp: ModelingExerciseUpdateComponent;
    let fixture: ComponentFixture<ModelingExerciseUpdateComponent>;
    let service: ModelingExerciseService;
    let courseService: CourseManagementService;
    let exerciseService: ExerciseService;
    const categories = [new ExerciseCategory('testCat', undefined), new ExerciseCategory('testCat2', undefined)];

    const categoriesStringified = categories.map((cat) => JSON.stringify(cat));

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockComponent(NgbPagination), OwlDateTimeModule, OwlNativeDateTimeModule],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({}) },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(CalendarService),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(ModelingExerciseService);
        courseService = TestBed.inject(CourseManagementService);
        exerciseService = TestBed.inject(ExerciseService);

        const route = TestBed.inject(ActivatedRoute);
        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, new Course(), new ExerciseGroup());
        modelingExercise.id = 123;
        modelingExercise.course = new Course();
        route.url = of([{ path: 'new' } as UrlSegment]);
        route.data = of({ modelingExercise: modelingExercise });
        route.snapshot = {
            paramMap: {
                get: () => 'mockValue',
            },
        } as any;

        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
    });

    describe('save', () => {
        describe('new exercise', () => {
            const course = { id: 1 } as Course;
            const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
            modelingExercise.course = course;

            modelingExercise.channelName = 'test';

            beforeEach(() => {
                const route = TestBed.inject(ActivatedRoute);
                route.data = of({ modelingExercise });
                route.url = of([{ path: 'exercise-groups' } as UrlSegment]);
            });

            it('should call create service on save for new entity and refresh calendar events', fakeAsync(() => {
                // GIVEN
                comp.ngOnInit();

                const entity = { ...modelingExercise };
                jest.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: entity })));
                const calendarService = TestBed.inject(CalendarService);
                const refreshSpy = jest.spyOn(calendarService, 'reloadEvents');

                // WHEN
                comp.save();
                tick(1000); // simulate async

                // THEN
                expect(service.create).toHaveBeenCalledWith(entity);
                expect(refreshSpy).toHaveBeenCalledOnce();
                expect(comp.isSaving).toBeFalse();
            }));
        });

        describe('existing exercise', () => {
            const course = { id: 1 } as Course;
            const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
            modelingExercise.course = course;
            modelingExercise.id = 123;

            modelingExercise.channelName = 'test';

            beforeEach(() => {
                const route = TestBed.inject(ActivatedRoute);
                route.data = of({ modelingExercise });
                route.url = of([{ path: 'exercise-groups' } as UrlSegment]);
            });

            it('should call update service on save for existing entity and refresh calendar events', fakeAsync(() => {
                // GIVEN
                comp.ngOnInit();

                const entity = { ...modelingExercise };
                jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));
                const calendarService = TestBed.inject(CalendarService);
                const refreshSpy = jest.spyOn(calendarService, 'reloadEvents');

                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity, {});
                expect(refreshSpy).toHaveBeenCalledOnce();
                expect(comp.isSaving).toBeFalse();
            }));
        });
    });

    describe('ngOnInit in import mode: Course to Course', () => {
        const course = new Course();
        course.id = 123;
        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        modelingExercise.id = 1;
        modelingExercise.releaseDate = dayjs();
        modelingExercise.dueDate = dayjs();
        modelingExercise.assessmentDueDate = dayjs();
        modelingExercise.channelName = 'test';
        const courseIdImportingCourse = 1;
        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId: courseIdImportingCourse });
            route.url = of([{ path: 'import' } as UrlSegment]);
            route.data = of({ modelingExercise });
        });

        it('should set isImport and remove all dates', fakeAsync(() => {
            jest.spyOn(courseService, 'findAllCategoriesOfCourse').mockReturnValue(of(new HttpResponse({ body: categoriesStringified })));
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toBeTrue();
            expect(comp.isExamMode).toBeFalse();
            expect(comp.modelingExercise.assessmentDueDate).toBeUndefined();
            expect(comp.modelingExercise.releaseDate).toBeUndefined();
            expect(comp.modelingExercise.dueDate).toBeUndefined();
            expect(courseService.findAllCategoriesOfCourse).toHaveBeenLastCalledWith(courseIdImportingCourse);
            expect(comp.existingCategories).toEqual(categories);
        }));

        it('should load exercise categories', () => {
            const loadExerciseCategoriesSpy = jest.spyOn(Utils, 'loadCourseExerciseCategories');

            comp.ngOnInit();

            expect(loadExerciseCategoriesSpy).toHaveBeenCalledOnce();
        });
    });

    describe('ngOnInit in import mode: Exam to Course', () => {
        const exerciseGroup = new ExerciseGroup();
        exerciseGroup.exam = new Exam();
        exerciseGroup.exam.course = new Course();
        exerciseGroup.exam.course.id = 1;
        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup);
        modelingExercise.id = 1;
        modelingExercise.releaseDate = dayjs();
        modelingExercise.dueDate = dayjs();
        modelingExercise.assessmentDueDate = dayjs();
        modelingExercise.channelName = 'test';
        const courseId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
            route.data = of({ modelingExercise });
        });

        it('should set isImport and remove all dates', fakeAsync(() => {
            jest.spyOn(courseService, 'findAllCategoriesOfCourse').mockReturnValue(of(new HttpResponse({ body: categoriesStringified })));

            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toBeTrue();
            expect(comp.isExamMode).toBeFalse();
            expect(comp.modelingExercise.assessmentDueDate).toBeUndefined();
            expect(comp.modelingExercise.releaseDate).toBeUndefined();
            expect(comp.modelingExercise.dueDate).toBeUndefined();
            expect(courseService.findAllCategoriesOfCourse).toHaveBeenLastCalledWith(exerciseGroup!.exam!.course!.id);
            expect(comp.existingCategories).toEqual(categories);
        }));
    });

    describe('ngOnInit in import mode: Course to Exam', () => {
        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, new Course(), undefined);
        modelingExercise.id = 1;
        modelingExercise.releaseDate = dayjs();
        modelingExercise.dueDate = dayjs();
        modelingExercise.assessmentDueDate = dayjs();
        const groupId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ groupId });
            route.url = of([{ path: 'exercise-groups' } as UrlSegment, { path: 'import' } as UrlSegment]);
            route.data = of({ modelingExercise });
        });

        it('should set isImport and isExamMode and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toBeTrue();
            expect(comp.isExamMode).toBeTrue();
            expect(comp.modelingExercise.course).toBeUndefined();
            expect(comp.modelingExercise.assessmentDueDate).toBeUndefined();
            expect(comp.modelingExercise.releaseDate).toBeUndefined();
            expect(comp.modelingExercise.dueDate).toBeUndefined();
        }));
    });

    describe('ngOnInit in import mode: Exam to Exam', () => {
        const exerciseGroup = new ExerciseGroup();
        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup);
        modelingExercise.id = 1;
        modelingExercise.releaseDate = dayjs();
        modelingExercise.dueDate = dayjs();
        modelingExercise.assessmentDueDate = dayjs();
        const groupId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ groupId });
            route.url = of([{ path: 'exercise-groups' } as UrlSegment, { path: 'import' } as UrlSegment]);
            route.data = of({ modelingExercise });
        });

        it('should set isImport and isExamMode and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toBeTrue();
            expect(comp.isExamMode).toBeTrue();
            expect(comp.modelingExercise.assessmentDueDate).toBeUndefined();
            expect(comp.modelingExercise.releaseDate).toBeUndefined();
            expect(comp.modelingExercise.dueDate).toBeUndefined();
        }));
    });

    it('should update categories with given ones', () => {
        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        modelingExercise.categories = categories;
        comp.modelingExercise = modelingExercise;
        const newCategories = [new ExerciseCategory('newCat1', undefined), new ExerciseCategory('newCat2', undefined)];

        comp.updateCategories(newCategories);
        expect(comp.modelingExercise.categories).toEqual(newCategories);
    });

    it('should call exercise service to validate date', () => {
        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        comp.modelingExercise = modelingExercise;
        jest.spyOn(exerciseService, 'validateDate');
        comp.validateDate();
        expect(exerciseService.validateDate).toHaveBeenCalledWith(modelingExercise);
    });

    it('should set assessmentType to manual in exam mode', () => {
        comp.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        comp.isExamMode = true;
        comp.diagramTypeChanged();
        expect(comp.modelingExercise.assessmentType).toEqual(AssessmentType.SEMI_AUTOMATIC);
    });

    it('should updateCategories properly by making category available for selection again when removing it', () => {
        comp.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        comp.exerciseCategories = [];
        const newCategories = [new ExerciseCategory('Easy', undefined), new ExerciseCategory('Hard', undefined)];

        comp.updateCategories(newCategories);

        expect(comp.modelingExercise.categories).toEqual(newCategories);
        expect(comp.exerciseCategories).toEqual(newCategories);
    });

    it('should subscribe and unsubscribe to input element changes', () => {
        jest.spyOn(console, 'error').mockImplementation(); // Suppress console errors from getCurrentModel, did not find a way to mock apollonEditor!.model properly

        const calculateValidSpy = jest.spyOn(comp, 'calculateFormSectionStatus');
        comp.teamConfigFormGroupComponent = { formValidChanges: new Subject(), formValid: true } as TeamConfigFormGroupComponent;
        comp.bonusPoints = { valueChanges: new Subject(), valid: true } as any as NgModel;
        comp.points = { valueChanges: new Subject(), valid: true } as any as NgModel;

        comp.ngOnInit();
        comp.ngAfterViewInit();
        fixture.detectChanges();

        (comp.points.valueChanges as Subject<boolean>).next(false);
        (comp.bonusPoints.valueChanges as Subject<boolean>).next(false);
        comp.teamConfigFormGroupComponent.formValidChanges.next(false);
        comp.exerciseTitleChannelNameComponent().titleChannelNameComponent().isValid.set(false);
        fixture.detectChanges();
        expect(calculateValidSpy).toHaveBeenCalledTimes(4);

        comp.ngOnDestroy();

        expect(comp.bonusPointsSubscription?.closed).toBeTrue();
        expect(comp.pointsSubscription?.closed).toBeTrue();
    });

    describe('handleEnterKeyNavigation', () => {
        beforeEach(() => {
            jest.spyOn(console, 'error').mockImplementation();
            comp.ngOnInit();
            fixture.detectChanges();
        });

        it('should prevent default and stop propagation', () => {
            const mockEvent = {
                preventDefault: jest.fn(),
                stopPropagation: jest.fn(),
            } as any as Event;

            comp.handleEnterKeyNavigation(mockEvent);

            expect(mockEvent.preventDefault).toHaveBeenCalledOnce();
            expect(mockEvent.stopPropagation).toHaveBeenCalledOnce();
        });

        it('should not navigate when focused on TEXTAREA', () => {
            const textarea = document.createElement('textarea');
            document.body.appendChild(textarea);
            textarea.focus();

            const mockEvent = {
                preventDefault: jest.fn(),
                stopPropagation: jest.fn(),
            } as any as Event;

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
                preventDefault: jest.fn(),
                stopPropagation: jest.fn(),
            } as any as Event;

            comp.handleEnterKeyNavigation(mockEvent);

            expect(mockEvent.preventDefault).toHaveBeenCalledOnce();
            document.body.removeChild(editableDiv);
        });

        it('should return early when editFormEl is undefined', () => {
            comp.editFormEl = undefined;

            const mockEvent = {
                preventDefault: jest.fn(),
                stopPropagation: jest.fn(),
            } as any as Event;

            comp.handleEnterKeyNavigation(mockEvent);

            expect(mockEvent.preventDefault).toHaveBeenCalledOnce();
        });

        it('should not navigate when focused inside Apollon Editor', () => {
            const mockApollon = document.createElement('div');
            mockApollon.className = 'apollon-container';
            const mockInput = document.createElement('input');
            mockApollon.appendChild(mockInput);

            const mockFormRoot = {
                querySelector: jest.fn().mockReturnValue(mockApollon),
                querySelectorAll: jest.fn().mockReturnValue([]),
            };

            comp.editFormEl = { nativeElement: mockFormRoot } as any;

            Object.defineProperty(document, 'activeElement', {
                writable: true,
                configurable: true,
                value: mockInput,
            });

            const mockEvent = {
                preventDefault: jest.fn(),
                stopPropagation: jest.fn(),
            } as any as Event;

            comp.handleEnterKeyNavigation(mockEvent);

            expect(mockFormRoot.querySelector).toHaveBeenCalledWith('.apollon-container');
        });

        it('should move focus to next input when Enter is pressed', () => {
            const input1 = document.createElement('input');
            const input2 = document.createElement('input');

            const mockFormRoot = {
                querySelector: jest.fn().mockReturnValue(null),
                querySelectorAll: jest.fn().mockReturnValue([input1, input2]),
            };

            comp.editFormEl = { nativeElement: mockFormRoot } as any;

            Object.defineProperty(document, 'activeElement', {
                writable: true,
                configurable: true,
                value: input1,
            });

            const focusSpy = jest.spyOn(input2, 'focus');

            const mockEvent = {
                preventDefault: jest.fn(),
                stopPropagation: jest.fn(),
            } as any as Event;

            comp.handleEnterKeyNavigation(mockEvent);

            expect(focusSpy).toHaveBeenCalledOnce();
        });

        it('should not focus next element if current is last', () => {
            const input1 = document.createElement('input');
            const input2 = document.createElement('input');

            const mockFormRoot = {
                querySelector: jest.fn().mockReturnValue(null),
                querySelectorAll: jest.fn().mockReturnValue([input1, input2]),
            };

            comp.editFormEl = { nativeElement: mockFormRoot } as any;

            Object.defineProperty(document, 'activeElement', {
                writable: true,
                configurable: true,
                value: input2,
            });

            const mockEvent = {
                preventDefault: jest.fn(),
                stopPropagation: jest.fn(),
            } as any as Event;

            comp.handleEnterKeyNavigation(mockEvent);

            expect(mockEvent.preventDefault).toHaveBeenCalledOnce();
        });
    });
});
