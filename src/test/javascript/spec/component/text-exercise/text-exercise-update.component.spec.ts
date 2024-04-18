import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
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
import { Subject, of, throwError } from 'rxjs';
import { Exam } from 'app/entities/exam.model';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import * as Utils from 'app/exercises/shared/course-exercises/course-utils';
import { NgModel } from '@angular/forms';
import { ExerciseTitleChannelNameComponent } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.component';
import { ExerciseUpdatePlagiarismComponent } from 'app/exercises/shared/plagiarism/exercise-update-plagiarism/exercise-update-plagiarism.component';
import { TeamConfigFormGroupComponent } from 'app/exercises/shared/team-config-form-group/team-config-form-group.component';

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
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({}) },
                { provide: NgbModal, useClass: MockNgbModalService },
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
            textExercise.channelName = 'testChannel';
            beforeEach(() => {
                const route = TestBed.inject(ActivatedRoute);
                route.data = of({ textExercise });
                route.url = of([{ path: 'exercise-groups' } as UrlSegment]);
            });

            it('should call update service on save for existing entity', fakeAsync(() => {
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

            it('should error during save', fakeAsync(() => {
                const onErrorSpy = jest.spyOn(comp as any, 'onSaveError');

                // GIVEN
                comp.ngOnInit();

                jest.spyOn(service, 'update').mockReturnValue(throwError(() => new HttpErrorResponse({ error: { title: 'some-error' } })));

                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(onErrorSpy).toHaveBeenCalledOnce();
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

            it('should call create service on save for new entity', fakeAsync(() => {
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

            it('should call import service on save for new entity', fakeAsync(() => {
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

    describe('exam exercise', () => {
        const textExercise = new TextExercise(undefined, new ExerciseGroup());

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.url = of([{ path: 'exercise-groups' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('should be in exam mode', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isExamMode).toBeTrue();
            expect(comp.textExercise).toEqual(textExercise);
        }));

        it('should not set dateErrors', fakeAsync(() => {
            const calculatValidationSectionsSpy = jest.spyOn(comp, 'calculateFormSectionStatus').mockReturnValue();
            const dateErrorNames = ['dueDateError', 'startDateError', 'assessmentDueDateError', 'exampleSolutionPublicationDateError'];
            comp.ngOnInit();
            tick();
            comp.validateDate();
            expect(calculatValidationSectionsSpy).toHaveBeenCalledOnce();
            for (const errorName of dateErrorNames) {
                expect(comp.textExercise[errorName]).toBeFalsy();
            }
        }));
    });

    describe('ngOnInit for course exercise', () => {
        const textExercise = new TextExercise(new Course(), undefined);

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('should not be in exam mode', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isExamMode).toBeFalse();
            expect(comp.textExercise).toEqual(textExercise);
        }));

        it('should calculate valid sections', () => {
            const calculateValidSpy = jest.spyOn(comp, 'calculateFormSectionStatus');
            comp.exerciseTitleChannelNameComponent = { titleChannelNameComponent: { formValidChanges: new Subject() } } as ExerciseTitleChannelNameComponent;
            comp.exerciseUpdatePlagiarismComponent = {
                formValidChanges: new Subject(),
                formValid: true,
            } as ExerciseUpdatePlagiarismComponent;
            comp.teamConfigFormGroupComponent = { formValidChanges: new Subject() } as TeamConfigFormGroupComponent;
            comp.bonusPoints = { valueChanges: new Subject(), valid: true } as unknown as NgModel;
            comp.points = { valueChanges: new Subject(), valid: true } as unknown as NgModel;

            comp.ngOnInit();
            comp.ngAfterViewInit();
            expect(comp.titleChannelNameComponentSubscription).toBeDefined();

            comp.exerciseTitleChannelNameComponent.titleChannelNameComponent.formValid = true;
            comp.exerciseTitleChannelNameComponent.titleChannelNameComponent.formValidChanges.next(true);
            expect(calculateValidSpy).toHaveBeenCalledOnce();
            expect(comp.formSectionStatus).toBeDefined();
            expect(comp.formSectionStatus[0].valid).toBeTrue();

            comp.validateDate();
            expect(calculateValidSpy).toHaveBeenCalledTimes(2);

            comp.ngOnDestroy();
            expect(comp.titleChannelNameComponentSubscription?.closed).toBeTrue();
        });
    });

    describe('ngOnInit in import mode: Course to Course', () => {
        const textExercise = new TextExercise(new Course(), undefined);
        textExercise.id = 1;
        textExercise.releaseDate = dayjs();
        textExercise.dueDate = dayjs();
        textExercise.assessmentDueDate = dayjs();
        textExercise.channelName = 'testChannel';
        const courseId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
            route.data = of({ textExercise });
            route.queryParams = of({ shouldHaveBackButtonToWizard: true });
        });

        it('should set isImport and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toBeTrue();
            expect(comp.isExamMode).toBeFalse();
            expect(comp.textExercise.assessmentDueDate).toBeUndefined();
            expect(comp.textExercise.releaseDate).toBeUndefined();
            expect(comp.textExercise.dueDate).toBeUndefined();
        }));

        it('should load exercise categories', () => {
            const loadExerciseCategoriesSpy = jest.spyOn(Utils, 'loadCourseExerciseCategories');

            comp.ngOnInit();

            expect(loadExerciseCategoriesSpy).toHaveBeenCalledOnce();
        });
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
        textExercise.channelName = 'testChannel';
        const courseId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('should set isImport and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toBeTrue();
            expect(comp.isExamMode).toBeFalse();
            expect(comp.textExercise.assessmentDueDate).toBeUndefined();
            expect(comp.textExercise.releaseDate).toBeUndefined();
            expect(comp.textExercise.dueDate).toBeUndefined();
        }));
    });

    describe('ngOnInit in import mode: Course to Exam', () => {
        const textExercise = new TextExercise(new Course(), undefined);
        textExercise.id = 1;
        textExercise.releaseDate = dayjs();
        textExercise.dueDate = dayjs();
        textExercise.assessmentDueDate = dayjs();
        textExercise.channelName = 'testChannel';
        const groupId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ groupId });
            route.url = of([{ path: 'exercise-groups' } as UrlSegment, { path: 'import' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('should set isImport and isExamMode and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toBeTrue();
            expect(comp.isExamMode).toBeTrue();
            expect(comp.textExercise.course).toBeUndefined();
            expect(comp.textExercise.assessmentDueDate).toBeUndefined();
            expect(comp.textExercise.releaseDate).toBeUndefined();
            expect(comp.textExercise.dueDate).toBeUndefined();
        }));
    });

    describe('ngOnInit in import mode: Exam to Exam', () => {
        const textExercise = new TextExercise(undefined, undefined);
        textExercise.exerciseGroup = new ExerciseGroup();
        textExercise.id = 1;
        textExercise.releaseDate = dayjs();
        textExercise.dueDate = dayjs();
        textExercise.assessmentDueDate = dayjs();
        textExercise.channelName = 'testChannel';

        const groupId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ groupId });
            route.url = of([{ path: 'exercise-groups' } as UrlSegment, { path: 'import' } as UrlSegment]);
            route.data = of({ textExercise });
        });

        it('should set isImport and isExamMode and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toBeTrue();
            expect(comp.isExamMode).toBeTrue();
            expect(comp.textExercise.assessmentDueDate).toBeUndefined();
            expect(comp.textExercise.releaseDate).toBeUndefined();
            expect(comp.textExercise.dueDate).toBeUndefined();
        }));
    });

    it('should updateCategories properly by making category available for selection again when removing it', () => {
        comp.textExercise = new TextExercise(undefined, undefined);
        comp.exerciseCategories = [];
        const newCategories = [{ category: 'Easy' }, { category: 'Hard' }];

        comp.updateCategories(newCategories);

        expect(comp.textExercise.categories).toEqual(newCategories);
        expect(comp.exerciseCategories).toEqual(newCategories);
    });
});
