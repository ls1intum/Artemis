import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute, UrlSegment } from '@angular/router';

import { ArtemisTestModule } from '../../test.module';
import { TextExerciseUpdateComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-update.component';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { Course } from 'app/entities/course.model';
import moment = require('moment');
import { of } from 'rxjs';
import { Exam } from 'app/entities/exam.model';

describe('TextExercise Management Update Component', () => {
    let comp: TextExerciseUpdateComponent;
    let fixture: ComponentFixture<TextExerciseUpdateComponent>;
    let service: TextExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
            declarations: [TextExerciseUpdateComponent],
        })
            .overrideTemplate(TextExerciseUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(TextExerciseUpdateComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(TextExerciseService);
    });

    describe('save', () => {
        it('Should call update service on save for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new TextExercise();
            entity.id = 123;
            spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.textExercise = entity;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.update).toHaveBeenCalledWith(entity, {});
            expect(comp.isSaving).toEqual(false);
        }));

        it('Should call create service on save for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new TextExercise();
            spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.textExercise = entity;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.create).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toEqual(false);
        }));
        it('Should call import service on save for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new TextExercise();
            spyOn(service, 'import').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.textExercise = entity;
            comp.isImport = true;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.import).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toEqual(false);
        }));
    });

    describe('ngOnInit cl for exam exercise', () => {
        const textExercise = new TextExercise(null, new ExerciseGroup());

        beforeEach(() => {
            const route = TestBed.get(ActivatedRoute);
            route.url = of([{ path: 'exercise-groups' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('Should be in exam mode', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isExamMode).toEqual(true);
            expect(comp.textExercise).toEqual(textExercise);
        }));
    });

    describe('ngOnInit for course exercise', () => {
        const textExercise = new TextExercise(new Course(), null);

        beforeEach(() => {
            const route = TestBed.get(ActivatedRoute);
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('Should not be in exam mode', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isExamMode).toEqual(false);
            expect(comp.textExercise).toEqual(textExercise);
        }));
    });

    describe('ngOnInit in import mode: Course to Course', () => {
        const textExercise = new TextExercise(new Course());
        textExercise.id = 1;
        textExercise.releaseDate = moment(moment.now());
        textExercise.dueDate = moment(moment.now());
        textExercise.assessmentDueDate = moment(moment.now());
        const courseId = 1;

        beforeEach(() => {
            const route = TestBed.get(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('Should set isImport and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toEqual(true);
            expect(comp.isExamMode).toEqual(false);
            expect(comp.textExercise.assessmentDueDate).toEqual(null);
            expect(comp.textExercise.releaseDate).toEqual(null);
            expect(comp.textExercise.dueDate).toEqual(null);
        }));
    });

    describe('ngOnInit in import mode: Exam to Course', () => {
        const textExercise = new TextExercise();
        textExercise.exerciseGroup = new ExerciseGroup();
        textExercise.exerciseGroup.exam = new Exam();
        textExercise.exerciseGroup.exam.course = new Course();
        textExercise.exerciseGroup.exam.course.id = 1;
        textExercise.id = 1;
        textExercise.releaseDate = moment(moment.now());
        textExercise.dueDate = moment(moment.now());
        textExercise.assessmentDueDate = moment(moment.now());
        const courseId = 1;

        beforeEach(() => {
            const route = TestBed.get(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('Should set isImport and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toEqual(true);
            expect(comp.isExamMode).toEqual(false);
            expect(comp.textExercise.assessmentDueDate).toEqual(null);
            expect(comp.textExercise.releaseDate).toEqual(null);
            expect(comp.textExercise.dueDate).toEqual(null);
        }));
    });

    describe('ngOnInit in import mode: Course to Exam', () => {
        const textExercise = new TextExercise(new Course());
        textExercise.id = 1;
        textExercise.releaseDate = moment(moment.now());
        textExercise.dueDate = moment(moment.now());
        textExercise.assessmentDueDate = moment(moment.now());
        const groupId = 1;

        beforeEach(() => {
            const route = TestBed.get(ActivatedRoute);
            route.params = of({ groupId });
            route.url = of([{ path: 'exercise-groups' } as UrlSegment, { path: 'import' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('Should set isImport and isExamMode and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toEqual(true);
            expect(comp.isExamMode).toEqual(true);
            expect(comp.textExercise.course).toEqual(null);
            expect(comp.textExercise.assessmentDueDate).toEqual(null);
            expect(comp.textExercise.releaseDate).toEqual(null);
            expect(comp.textExercise.dueDate).toEqual(null);
        }));
    });

    describe('ngOnInit in import mode: Exam to Exam', () => {
        const textExercise = new TextExercise();
        textExercise.exerciseGroup = new ExerciseGroup();
        textExercise.id = 1;
        textExercise.releaseDate = moment(moment.now());
        textExercise.dueDate = moment(moment.now());
        textExercise.assessmentDueDate = moment(moment.now());
        const groupId = 1;

        beforeEach(() => {
            const route = TestBed.get(ActivatedRoute);
            route.params = of({ groupId });
            route.url = of([{ path: 'exercise-groups' } as UrlSegment, { path: 'import' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('Should set isImport and isExamMode and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toEqual(true);
            expect(comp.isExamMode).toEqual(true);
            expect(comp.textExercise.assessmentDueDate).toEqual(null);
            expect(comp.textExercise.releaseDate).toEqual(null);
            expect(comp.textExercise.dueDate).toEqual(null);
        }));
    });
});
