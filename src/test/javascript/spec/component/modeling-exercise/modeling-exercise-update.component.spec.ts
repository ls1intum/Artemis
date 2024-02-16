import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Subject, of } from 'rxjs';
import { ActivatedRoute, UrlSegment } from '@angular/router';

import { ArtemisTestModule } from '../../test.module';
import { ModelingExerciseUpdateComponent } from 'app/exercises/modeling/manage/modeling-exercise-update.component';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exam } from 'app/entities/exam.model';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import * as Utils from 'app/exercises/shared/course-exercises/course-utils';
import { ExerciseTitleChannelNameComponent } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.component';
import { ExerciseUpdatePlagiarismComponent } from 'app/exercises/shared/plagiarism/exercise-update-plagiarism/exercise-update-plagiarism.component';
import { NgModel } from '@angular/forms';
import { TeamConfigFormGroupComponent } from 'app/exercises/shared/team-config-form-group/team-config-form-group.component';
import { UMLDiagramType } from '@ls1intum/apollon';

describe('ModelingExerciseUpdateComponent', () => {
    let comp: ModelingExerciseUpdateComponent;
    let fixture: ComponentFixture<ModelingExerciseUpdateComponent>;
    let service: ModelingExerciseService;
    let courseService: CourseManagementService;
    let exerciseService: ExerciseService;
    const categories = [{ category: 'testCat' }, { category: 'testCat2' }];
    const categoriesStringified = categories.map((cat) => JSON.stringify(cat));

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockComponent(NgbPagination)],
            declarations: [ModelingExerciseUpdateComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({}) },
                { provide: NgbModal, useClass: MockNgbModalService },
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

    it('should updateCategories properly by making category available for selection again when removing it', () => {
        comp.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        comp.exerciseCategories = [];
        const newCategories = [{ category: 'Easy' }, { category: 'Hard' }];

        comp.updateCategories(newCategories);

        expect(comp.modelingExercise.categories).toEqual(newCategories);
        expect(comp.exerciseCategories).toEqual(newCategories);
    });

    it('should subscribe and unsubscribe to input element changes', () => {
        const calculateValidSpy = jest.spyOn(comp, 'calculateFormSectionStatus');
        comp.modelingExercise = { startDate: dayjs(), dueDate: dayjs(), assessmentDueDate: dayjs(), releaseDate: dayjs() } as ModelingExercise;
        comp.exerciseTitleChannelNameComponent = { titleChannelNameComponent: { formValidChanges: new Subject(), formValid: true } } as ExerciseTitleChannelNameComponent;
        comp.exerciseUpdatePlagiarismComponent = { formValidChanges: new Subject(), formValid: true } as ExerciseUpdatePlagiarismComponent;
        comp.teamConfigFormGroupComponent = { formValidChanges: new Subject(), formValid: true } as TeamConfigFormGroupComponent;
        comp.bonusPoints = { valueChanges: new Subject(), valid: true } as any as NgModel;
        comp.points = { valueChanges: new Subject(), valid: true } as any as NgModel;

        comp.ngAfterViewInit();

        (comp.points.valueChanges as Subject<boolean>).next(false);
        (comp.bonusPoints.valueChanges as Subject<boolean>).next(false);
        comp.teamConfigFormGroupComponent.formValidChanges.next(false);
        comp.exerciseUpdatePlagiarismComponent.formValidChanges.next(false);
        comp.exerciseTitleChannelNameComponent.titleChannelNameComponent.formValidChanges.next(false);
        expect(calculateValidSpy).toHaveBeenCalledTimes(5);

        comp.ngOnDestroy();

        expect(comp.titleChannelNameComponentSubscription?.closed).toBeTrue();
        expect(comp.plagiarismSubscription?.closed).toBeTrue();
        expect(comp.bonusPointsSubscription?.closed).toBeTrue();
        expect(comp.pointsSubscription?.closed).toBeTrue();
    });
});
