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
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { Exam } from 'app/entities/exam.model';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';

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
                MockProvider(TranslateService),
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
        describe('existing exercise', () => {
            const course = { id: 1 } as Course;
            const textExercise = new TextExercise(course, undefined);
            textExercise.id = 123;

            beforeEach(() => {
                const route = TestBed.inject(ActivatedRoute);
                route.data = of({ textExercise });
                route.url = of([{ path: 'exercise-groups' } as UrlSegment]);
            });

            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                comp.ngOnInit();

                const entity = { ...textExercise };
                jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));

                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity, {});
                expect(comp.isSaving).toBeFalse();
            }));
        });

        describe('new exercise', () => {
            const course = { id: 1 } as Course;
            const textExercise = new TextExercise(course, undefined);

            beforeEach(() => {
                const route = TestBed.inject(ActivatedRoute);
                route.data = of({ textExercise });
                route.url = of([{ path: 'exercise-groups' } as UrlSegment]);
            });

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                comp.ngOnInit();

                const entity = { ...textExercise };
                jest.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: entity })));

                // WHEN
                comp.save();
                tick(1000); // simulate async

                // THEN
                expect(service.create).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toBeFalse();
            }));
        });

        describe('imported exercise', () => {
            const course = { id: 1 } as Course;
            const textExercise = new TextExercise(course, undefined);

            beforeEach(() => {
                const route = TestBed.inject(ActivatedRoute);
                route.data = of({ textExercise });
                route.url = of([{ path: 'exercise-groups' } as UrlSegment]);
            });

            it('Should call import service on save for new entity', fakeAsync(() => {
                // GIVEN
                comp.ngOnInit();
                comp.isImport = true;

                const entity = { ...textExercise };
                jest.spyOn(service, 'import').mockReturnValue(of(new HttpResponse({ body: entity })));

                // WHEN
                comp.save();
                tick(1000); // simulate async

                // THEN
                expect(service.import).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toBeFalse();
            }));
        });
    });

    describe('ngOnInit cl for exam exercise', () => {
        const textExercise = new TextExercise(undefined, new ExerciseGroup());

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.url = of([{ path: 'exercise-groups' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('Should be in exam mode', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isExamMode).toBeTrue();
            expect(comp.textExercise).toEqual(textExercise);
        }));
    });

    describe('ngOnInit for course exercise', () => {
        const textExercise = new TextExercise(new Course(), undefined);

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('Should not be in exam mode', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isExamMode).toBeFalse();
            expect(comp.textExercise).toEqual(textExercise);
        }));
    });

    describe('ngOnInit in import mode: Course to Course', () => {
        const textExercise = new TextExercise(new Course(), undefined);
        textExercise.id = 1;
        textExercise.releaseDate = dayjs();
        textExercise.dueDate = dayjs();
        textExercise.assessmentDueDate = dayjs();
        const courseId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('Should set isImport and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toBeTrue();
            expect(comp.isExamMode).toBeFalse();
            expect(comp.textExercise.assessmentDueDate).toEqual(undefined);
            expect(comp.textExercise.releaseDate).toEqual(undefined);
            expect(comp.textExercise.dueDate).toEqual(undefined);
        }));
    });

    describe('ngOnInit in import mode: Exam to Course', () => {
        const textExercise = new TextExercise(undefined, undefined);
        textExercise.exerciseGroup = new ExerciseGroup();
        textExercise.exerciseGroup.exam = new Exam();
        textExercise.exerciseGroup.exam.course = new Course();
        textExercise.exerciseGroup.exam.course.id = 1;
        textExercise.id = 1;
        textExercise.releaseDate = dayjs();
        textExercise.dueDate = dayjs();
        textExercise.assessmentDueDate = dayjs();
        const courseId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('Should set isImport and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toBeTrue();
            expect(comp.isExamMode).toBeFalse();
            expect(comp.textExercise.assessmentDueDate).toEqual(undefined);
            expect(comp.textExercise.releaseDate).toEqual(undefined);
            expect(comp.textExercise.dueDate).toEqual(undefined);
        }));
    });

    describe('ngOnInit in import mode: Course to Exam', () => {
        const textExercise = new TextExercise(new Course(), undefined);
        textExercise.id = 1;
        textExercise.releaseDate = dayjs();
        textExercise.dueDate = dayjs();
        textExercise.assessmentDueDate = dayjs();
        const groupId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ groupId });
            route.url = of([{ path: 'exercise-groups' } as UrlSegment, { path: 'import' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('Should set isImport and isExamMode and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toBeTrue();
            expect(comp.isExamMode).toBeTrue();
            expect(comp.textExercise.course).toEqual(undefined);
            expect(comp.textExercise.assessmentDueDate).toEqual(undefined);
            expect(comp.textExercise.releaseDate).toEqual(undefined);
            expect(comp.textExercise.dueDate).toEqual(undefined);
        }));
    });

    describe('ngOnInit in import mode: Exam to Exam', () => {
        const textExercise = new TextExercise(undefined, undefined);
        textExercise.exerciseGroup = new ExerciseGroup();
        textExercise.id = 1;
        textExercise.releaseDate = dayjs();
        textExercise.dueDate = dayjs();
        textExercise.assessmentDueDate = dayjs();
        const groupId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ groupId });
            route.url = of([{ path: 'exercise-groups' } as UrlSegment, { path: 'import' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('Should set isImport and isExamMode and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toBeTrue();
            expect(comp.isExamMode).toBeTrue();
            expect(comp.textExercise.assessmentDueDate).toEqual(undefined);
            expect(comp.textExercise.releaseDate).toEqual(undefined);
            expect(comp.textExercise.dueDate).toEqual(undefined);
        }));
    });
});
