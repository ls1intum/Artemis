import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, UrlSegment } from '@angular/router';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';

import { TextExerciseUpdateComponent } from 'app/text/manage/text-exercise/update/text-exercise-update.component';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { FormSectionStatus } from 'app/shared/form/form-status-bar/form-status-bar.component';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { Course } from 'app/core/course/shared/entities/course.model';
import * as Utils from 'app/exercise/course-exercises/course-utils';
import dayjs from 'dayjs/esm';
import { Subject, of, throwError } from 'rxjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { NgModel } from '@angular/forms';
import { TeamConfigFormGroupComponent } from 'app/exercise/team-config-form-group/team-config-form-group.component';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockProvider } from 'ng-mocks';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { ActivatedRouteSnapshot } from '@angular/router';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';

describe('TextExercise Management Update Component', () => {
    let comp: TextExerciseUpdateComponent;
    let fixture: ComponentFixture<TextExerciseUpdateComponent>;
    let service: TextExerciseService;

    const createDateFieldStub = (): Partial<FormDateTimePickerComponent> => ({
        dateInput: { valid: true } as Partial<NgModel>,
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [OwlDateTimeModule, OwlNativeDateTimeModule],
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

        fixture = TestBed.createComponent(TextExerciseUpdateComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(TextExerciseService);
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

            it('should call update service and refresh calendar events on save for existing entity', fakeAsync(() => {
                // GIVEN
                comp.ngOnInit();

                const entity = { ...textExercise };
                jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));
                const calendarService = TestBed.inject(CalendarService);
                const refreshSpy = jest.spyOn(calendarService, 'reloadEvents');

                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity, {});
                expect(comp.isSaving).toBeFalse();
                expect(refreshSpy).toHaveBeenCalledOnce();
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

            it('should call create service and refresh calendar events on save for new entity', fakeAsync(() => {
                // GIVEN
                comp.ngOnInit();

                const entity = { ...textExercise };
                jest.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: entity })));
                const calendarService = TestBed.inject(CalendarService);
                const refreshSpy = jest.spyOn(calendarService, 'reloadEvents');

                // WHEN
                comp.save();
                tick(1000); // simulate async

                // THEN
                expect(service.create).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toBeFalse();
                expect(refreshSpy).toHaveBeenCalledOnce();
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
                expect(comp.textExercise[errorName as keyof TextExercise]).toBeFalsy();
            }
        }));
    });

    describe('ngOnInit for course exercise', () => {
        const textExercise = new TextExercise(new Course(), undefined);

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ textExercise });
            route.snapshot = {
                paramMap: {
                    get: (key: string) => 'mockValue',
                },
            } as ActivatedRouteSnapshot;

            global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
                return new MockResizeObserver(callback);
            });
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
            const calculateValidSpy = jest.spyOn(comp, 'calculateFormSectionStatus').mockImplementation(() => {
                comp.formSectionStatus = [
                    { valid: true, empty: false, title: 'dummy' },
                    { valid: true, empty: false, title: 'dummy2' },
                ] as FormSectionStatus[];
            });
            comp.exerciseTitleChannelNameComponent().titleChannelNameComponent().isValid.set(false);
            comp.exerciseUpdatePlagiarismComponent()?.isFormValid.set(true);
            comp.teamConfigFormGroupComponent = { formValidChanges: new Subject() } as TeamConfigFormGroupComponent;
            comp.bonusPoints = { valueChanges: new Subject(), valid: true } as unknown as NgModel;
            comp.points = { valueChanges: new Subject(), valid: true } as unknown as NgModel;
            comp.solutionPublicationDateField = createDateFieldStub();
            comp.releaseDateField = createDateFieldStub();
            comp.startDateField = createDateFieldStub();
            comp.dueDateField = createDateFieldStub();
            comp.assessmentDateField = createDateFieldStub();

            comp.ngOnInit();
            comp.ngAfterViewInit();
            // Angular will reset view children during initialization; ensure stubs stay defined.
            comp.solutionPublicationDateField = createDateFieldStub();
            comp.releaseDateField = createDateFieldStub();
            comp.startDateField = createDateFieldStub();
            comp.dueDateField = createDateFieldStub();
            comp.assessmentDateField = createDateFieldStub();

            comp.exerciseTitleChannelNameComponent().titleChannelNameComponent().isValid.set(true);

            fixture.changeDetectorRef.detectChanges();

            expect(calculateValidSpy).toHaveBeenCalledTimes(2);
            expect(comp.formSectionStatus).toBeDefined();
            expect(comp.formSectionStatus[0].valid).toBeTrue();

            comp.validateDate();
            expect(calculateValidSpy).toHaveBeenCalledTimes(3);

            comp.ngOnDestroy();
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
        const newCategories = [new ExerciseCategory('Easy', undefined), new ExerciseCategory('Hard', undefined)];

        comp.updateCategories(newCategories);

        expect(comp.textExercise.categories).toEqual(newCategories);
        expect(comp.exerciseCategories).toEqual(newCategories);
    });
});
