import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { Subject, of } from 'rxjs';
import { ActivatedRoute, Router, UrlSegment } from '@angular/router';
import { signal } from '@angular/core';

import { ModelingExerciseUpdateComponent } from 'app/modeling/manage/update/modeling-exercise-update.component';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
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
import { ExerciseTitleChannelNameComponent } from 'app/exercise/exercise-title-channel-name/exercise-title-channel-name.component';
import { NgModel } from '@angular/forms';
import { TeamConfigFormGroupComponent } from 'app/exercise/team-config-form-group/team-config-form-group.component';
import { UMLDiagramType } from '@ls1intum/apollon';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockRouter } from 'test/helpers/mocks/mock-router';

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
            imports: [MockComponent(NgbPagination)],
            declarations: [ModelingExerciseUpdateComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({}) },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: Router, useClass: MockRouter },
                MockProvider(TranslateService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideTemplate(ModelingExerciseUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(ModelingExerciseService);
        courseService = TestBed.inject(CourseManagementService);
        exerciseService = TestBed.inject(ExerciseService);
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

            it('should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                comp.ngOnInit();

                const entity = { ...modelingExercise };
                jest.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: entity })));

                // WHEN
                comp.save();
                tick(1000); // simulate async

                // THEN
                expect(service.create).toHaveBeenCalledWith(entity);
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

            it('should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                comp.ngOnInit();

                const entity = { ...modelingExercise };
                jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));

                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity, {});
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
        const calculateValidSpy = jest.spyOn(comp, 'calculateFormSectionStatus');
        comp.modelingExercise = { startDate: dayjs(), dueDate: dayjs(), assessmentDueDate: dayjs(), releaseDate: dayjs() } as ModelingExercise;
        comp.exerciseTitleChannelNameComponent = { titleChannelNameComponent: { isValid: signal(true) } } as ExerciseTitleChannelNameComponent;
        comp.teamConfigFormGroupComponent = { formValidChanges: new Subject(), formValid: true } as TeamConfigFormGroupComponent;
        comp.bonusPoints = { valueChanges: new Subject(), valid: true } as any as NgModel;
        comp.points = { valueChanges: new Subject(), valid: true } as any as NgModel;

        comp.ngAfterViewInit();

        (comp.points.valueChanges as Subject<boolean>).next(false);
        (comp.bonusPoints.valueChanges as Subject<boolean>).next(false);
        comp.teamConfigFormGroupComponent.formValidChanges.next(false);
        comp.exerciseTitleChannelNameComponent.titleChannelNameComponent.isValid.set(false);
        expect(calculateValidSpy).toHaveBeenCalledTimes(4);

        comp.ngOnDestroy();

        expect(comp.titleChannelNameComponentSubscription?.closed).toBeTrue();
        expect(comp.bonusPointsSubscription?.closed).toBeTrue();
        expect(comp.pointsSubscription?.closed).toBeTrue();
    });
});
