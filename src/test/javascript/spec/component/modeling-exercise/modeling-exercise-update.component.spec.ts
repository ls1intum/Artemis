import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ActivatedRoute, UrlSegment } from '@angular/router';

import { ArtemisTestModule } from '../../test.module';
import { ModelingExerciseUpdateComponent } from 'app/exercises/modeling/manage/modeling-exercise-update.component';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exam } from 'app/entities/exam.model';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';

describe('ModelingExercise Management Update Component', () => {
    let comp: ModelingExerciseUpdateComponent;
    let fixture: ComponentFixture<ModelingExerciseUpdateComponent>;
    let service: ModelingExerciseService;
    let courseService: CourseManagementService;
    let exerciseService: ExerciseService;
    const categories = [{ category: 'testCat' }, { category: 'testCat2' }];
    const categoriesStringified = categories.map((cat) => JSON.stringify(cat));

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ModelingExerciseUpdateComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                MockProvider(TranslateService),
            ],
        })
            .overrideTemplate(ModelingExerciseUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(ModelingExerciseService);
        courseService = fixture.debugElement.injector.get(CourseManagementService);
        exerciseService = fixture.debugElement.injector.get(ExerciseService);
    });

    describe('save', () => {
        describe('new exercise', () => {
            const course = { id: 1 } as Course;
            const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
            modelingExercise.course = course;

            beforeEach(() => {
                const route = TestBed.inject(ActivatedRoute);
                route.data = of({ modelingExercise });
                route.url = of([{ path: 'exercise-groups' } as UrlSegment]);
            });

            it('Should call create service on save for new entity', fakeAsync(() => {
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

            beforeEach(() => {
                const route = TestBed.inject(ActivatedRoute);
                route.data = of({ modelingExercise });
                route.url = of([{ path: 'exercise-groups' } as UrlSegment]);
            });

            it('Should call update service on save for existing entity', fakeAsync(() => {
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
        const courseId = 1;
        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
            route.data = of({ modelingExercise });
        });

        it('Should set isImport and remove all dates', fakeAsync(() => {
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
            expect(courseService.findAllCategoriesOfCourse).toHaveBeenLastCalledWith(course.id);
            expect(comp.existingCategories).toEqual(categories);
        }));
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
        const courseId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
            route.data = of({ modelingExercise });
        });

        it('Should set isImport and remove all dates', fakeAsync(() => {
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

        it('Should set isImport and isExamMode and remove all dates', fakeAsync(() => {
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

        it('Should set isImport and isExamMode and remove all dates', fakeAsync(() => {
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
        const newCategories = [{ category: 'newCat1' }, { category: 'newCat2' }];
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
});
