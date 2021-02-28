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
import * as moment from 'moment';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';

describe('ModelingExercise Management Update Component', () => {
    let comp: ModelingExerciseUpdateComponent;
    let fixture: ComponentFixture<ModelingExerciseUpdateComponent>;
    let service: ModelingExerciseService;

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
    });

    describe('save', () => {
        it('Should call update service on save for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new ModelingExercise(UMLDiagramType.ActivityDiagram, undefined, undefined);
            entity.id = 123;
            spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.modelingExercise = entity;
            comp.modelingExercise.course = { id: 1 } as Course;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.update).toHaveBeenCalledWith(entity, {});
            expect(comp.isSaving).toEqual(false);
        }));

        it('Should call create service on save for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
            spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.modelingExercise = entity;
            comp.modelingExercise.course = { id: 1 } as Course;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.create).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toEqual(false);
        }));

        it('Should trim the exercise title before saving', fakeAsync(() => {
            // GIVEN
            const entity = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
            entity.title = 'My Exercise   ';
            spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.modelingExercise = entity;
            comp.modelingExercise.course = { id: 1 } as Course;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.create).toHaveBeenCalledWith(entity);
            expect(entity.title).toEqual('My Exercise');
        }));
    });

    describe('ngOnInit in import mode: Course to Course', () => {
        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, new Course(), undefined);
        modelingExercise.id = 1;
        modelingExercise.releaseDate = moment();
        modelingExercise.dueDate = moment();
        modelingExercise.assessmentDueDate = moment();
        const courseId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
            route.data = of({ modelingExercise });
        });

        it('Should set isImport and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toEqual(true);
            expect(comp.isExamMode).toEqual(false);
            expect(comp.modelingExercise.assessmentDueDate).toEqual(undefined);
            expect(comp.modelingExercise.releaseDate).toEqual(undefined);
            expect(comp.modelingExercise.dueDate).toEqual(undefined);
        }));
    });

    describe('ngOnInit in import mode: Exam to Course', () => {
        const exerciseGroup = new ExerciseGroup();
        exerciseGroup.exam = new Exam();
        exerciseGroup.exam.course = new Course();
        exerciseGroup.exam.course.id = 1;
        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup);
        modelingExercise.id = 1;
        modelingExercise.releaseDate = moment();
        modelingExercise.dueDate = moment();
        modelingExercise.assessmentDueDate = moment();
        const courseId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
            route.data = of({ modelingExercise });
        });

        it('Should set isImport and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toEqual(true);
            expect(comp.isExamMode).toEqual(false);
            expect(comp.modelingExercise.assessmentDueDate).toEqual(undefined);
            expect(comp.modelingExercise.releaseDate).toEqual(undefined);
            expect(comp.modelingExercise.dueDate).toEqual(undefined);
        }));
    });

    describe('ngOnInit in import mode: Course to Exam', () => {
        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, new Course(), undefined);
        modelingExercise.id = 1;
        modelingExercise.releaseDate = moment();
        modelingExercise.dueDate = moment();
        modelingExercise.assessmentDueDate = moment();
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
            expect(comp.isImport).toEqual(true);
            expect(comp.isExamMode).toEqual(true);
            expect(comp.modelingExercise.course).toEqual(undefined);
            expect(comp.modelingExercise.assessmentDueDate).toEqual(undefined);
            expect(comp.modelingExercise.releaseDate).toEqual(undefined);
            expect(comp.modelingExercise.dueDate).toEqual(undefined);
        }));
    });

    describe('ngOnInit in import mode: Exam to Exam', () => {
        const exerciseGroup = new ExerciseGroup();
        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup);
        modelingExercise.id = 1;
        modelingExercise.releaseDate = moment();
        modelingExercise.dueDate = moment();
        modelingExercise.assessmentDueDate = moment();
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
            expect(comp.isImport).toEqual(true);
            expect(comp.isExamMode).toEqual(true);
            expect(comp.modelingExercise.assessmentDueDate).toEqual(undefined);
            expect(comp.modelingExercise.releaseDate).toEqual(undefined);
            expect(comp.modelingExercise.dueDate).toEqual(undefined);
        }));
    });
});
