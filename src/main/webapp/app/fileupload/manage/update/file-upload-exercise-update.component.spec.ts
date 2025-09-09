import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, UrlSegment } from '@angular/router';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { Subject, of } from 'rxjs';

import { FileUploadExerciseUpdateComponent } from 'app/fileupload/manage/update/file-upload-exercise-update.component';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateService } from '@ngx-translate/core';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { fileUploadExercise } from 'test/helpers/mocks/service/mock-file-upload-exercise.service';
import { TeamConfigFormGroupComponent } from 'app/exercise/team-config-form-group/team-config-form-group.component';
import { NgModel } from '@angular/forms';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { MockComponent, MockProvider } from 'ng-mocks';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';

describe('FileUploadExerciseUpdateComponent', () => {
    let comp: FileUploadExerciseUpdateComponent;
    let fixture: ComponentFixture<FileUploadExerciseUpdateComponent>;
    let service: FileUploadExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [OwlDateTimeModule, OwlNativeDateTimeModule],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({}) },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                MockComponent(FormDateTimePickerComponent),
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(CalendarService),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(FileUploadExerciseService);
    });

    describe('save', () => {
        describe('new exercise', () => {
            const course = { id: 1 } as Course;
            const fileUploadExercise = new FileUploadExercise(course, undefined);
            fileUploadExercise.channelName = 'test';

            beforeEach(() => {
                const route = TestBed.inject(ActivatedRoute);
                route.data = of({ fileUploadExercise });
                route.url = of([{ path: 'exercise-groups' } as UrlSegment]);
            });

            it('should call create service and refresh calendar on save for new entity', fakeAsync(() => {
                // GIVEN
                comp.ngOnInit();

                const entity = { ...fileUploadExercise };
                jest.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: entity })));

                const calendarService = TestBed.inject(CalendarService);
                const refreshSpy = jest.spyOn(calendarService, 'reloadEvents');

                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.create).toHaveBeenCalledWith(entity);
                expect(refreshSpy).toHaveBeenCalledOnce();
                expect(comp.isSaving).toBeFalse();
            }));
        });

        describe('existing exercise', () => {
            const course = { id: 1 } as Course;
            const fileUploadExercise = new FileUploadExercise(course, undefined);
            fileUploadExercise.id = 123;
            fileUploadExercise.channelName = 'test';

            beforeEach(() => {
                const route = TestBed.inject(ActivatedRoute);
                route.data = of({ fileUploadExercise });
                route.url = of([{ path: 'exercise-groups' } as UrlSegment]);
            });

            it('should call update service and refresh calendar on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = { ...fileUploadExercise };
                jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));
                comp.ngOnInit();

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

    describe('ngOnInit with given exerciseGroup', () => {
        const fileUploadExercise = new FileUploadExercise(undefined, new ExerciseGroup());

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.data = of({ fileUploadExercise });
            route.url = of([{ path: 'exercise-groups' } as UrlSegment]);
        });

        it('should be in exam mode', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isExamMode).toBeTrue();
            expect(comp.fileUploadExercise).toEqual(fileUploadExercise);
        }));
    });

    describe('ngOnInit without given exerciseGroup', () => {
        const fileUploadExercise = new FileUploadExercise(new Course(), undefined);

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.data = of({ fileUploadExercise });
            route.url = of([{ path: 'new' } as UrlSegment]);

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
            expect(comp.fileUploadExercise).toEqual(fileUploadExercise);
        }));

        it('should calculate valid sections', () => {
            const calculateValidSpy = jest.spyOn(comp, 'calculateFormSectionStatus');
            comp.exerciseTitleChannelNameComponent().titleChannelNameComponent().isValid.set(false);
            comp.teamConfigFormGroupComponent = { formValidChanges: new Subject() } as TeamConfigFormGroupComponent;
            comp.bonusPoints = { valueChanges: new Subject(), valid: true } as unknown as NgModel;
            comp.points = { valueChanges: new Subject(), valid: true } as unknown as NgModel;

            comp.ngOnInit();
            comp.ngAfterViewInit();

            comp.exerciseTitleChannelNameComponent().titleChannelNameComponent().isValid.set(true);
            fixture.detectChanges();
            expect(calculateValidSpy).toHaveBeenCalledOnce();
            expect(comp.formStatusSections).toBeDefined();
            expect(comp.formStatusSections[0].valid).toBeTrue();

            comp.validateDate();
            expect(calculateValidSpy).toHaveBeenCalledTimes(2);

            comp.ngOnDestroy();
        });
    });
    describe('imported exercise', () => {
        const course = { id: 1 } as Course;
        const fileUploadExercise = new FileUploadExercise(course, undefined);

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.data = of({ fileUploadExercise });
            route.url = of([{ path: 'exercise-groups' } as UrlSegment]);
        });

        it('should call import service on save for new entity', fakeAsync(() => {
            // GIVEN
            comp.ngOnInit();
            comp.isImport = true;

            const entity = { ...fileUploadExercise };
            jest.spyOn(service, 'import').mockReturnValue(of(new HttpResponse({ body: entity })));

            // WHEN
            comp.save();
            tick(1000); // simulate async

            // THEN
            expect(service.import).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toBeFalse();
        }));
    });

    describe('ngOnInit in import mode: Course to Course', () => {
        const fileUploadExercise = new FileUploadExercise(new Course(), undefined);
        fileUploadExercise.id = 1;
        fileUploadExercise.releaseDate = dayjs();
        fileUploadExercise.dueDate = dayjs();
        fileUploadExercise.assessmentDueDate = dayjs();
        fileUploadExercise.channelName = 'test';
        const courseId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
            route.data = of({ fileUploadExercise });
        });

        it('should set isImport and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toBeTrue();
            expect(comp.isExamMode).toBeFalse();
            expect(comp.fileUploadExercise.assessmentDueDate).toBeUndefined();
            expect(comp.fileUploadExercise.releaseDate).toBeUndefined();
            expect(comp.fileUploadExercise.dueDate).toBeUndefined();
        }));
    });
    describe('ngOnInit in import mode: Exam to Course', () => {
        const fileUploadExercise = new FileUploadExercise(undefined, undefined);
        fileUploadExercise.exerciseGroup = new ExerciseGroup();
        fileUploadExercise.exerciseGroup.exam = new Exam();
        fileUploadExercise.exerciseGroup.exam.course = new Course();
        fileUploadExercise.exerciseGroup.exam.course.id = 1;
        fileUploadExercise.id = 1;
        fileUploadExercise.releaseDate = dayjs();
        fileUploadExercise.dueDate = dayjs();
        fileUploadExercise.assessmentDueDate = dayjs();

        fileUploadExercise.channelName = 'test';
        const courseId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
            route.data = of({ fileUploadExercise });
        });

        it('should set isImport and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toBeTrue();
            expect(comp.isExamMode).toBeFalse();
            expect(comp.fileUploadExercise.assessmentDueDate).toBeUndefined();
            expect(comp.fileUploadExercise.releaseDate).toBeUndefined();
            expect(comp.fileUploadExercise.dueDate).toBeUndefined();
        }));
    });

    describe('ngOnInit in import mode: Course to Exam', () => {
        const fileUploadExercise = new FileUploadExercise(new Course(), undefined);
        fileUploadExercise.id = 1;
        fileUploadExercise.releaseDate = dayjs();
        fileUploadExercise.dueDate = dayjs();
        fileUploadExercise.assessmentDueDate = dayjs();
        fileUploadExercise.channelName = 'test';
        const groupId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ groupId });
            route.url = of([{ path: 'exercise-groups' } as UrlSegment, { path: 'import' } as UrlSegment]);
            route.data = of({ fileUploadExercise });
        });

        it('should set isImport and isExamMode and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toBeTrue();
            expect(comp.isExamMode).toBeTrue();
            expect(comp.fileUploadExercise.course).toBeUndefined();
            expect(comp.fileUploadExercise.assessmentDueDate).toBeUndefined();
            expect(comp.fileUploadExercise.releaseDate).toBeUndefined();
            expect(comp.fileUploadExercise.dueDate).toBeUndefined();
        }));
    });

    describe('ngOnInit in import mode: Exam to Exam', () => {
        const fileUploadExercise = new TextExercise(undefined, undefined);
        fileUploadExercise.exerciseGroup = new ExerciseGroup();
        fileUploadExercise.id = 1;
        fileUploadExercise.releaseDate = dayjs();
        fileUploadExercise.dueDate = dayjs();
        fileUploadExercise.assessmentDueDate = dayjs();
        fileUploadExercise.channelName = 'test';
        const groupId = 1;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ groupId });
            route.url = of([{ path: 'exercise-groups' } as UrlSegment, { path: 'import' } as UrlSegment]);
            route.data = of({ fileUploadExercise });
        });

        it('should set isImport and isExamMode and remove all dates', fakeAsync(() => {
            // WHEN
            comp.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(comp.isImport).toBeTrue();
            expect(comp.isExamMode).toBeTrue();
            expect(comp.fileUploadExercise.assessmentDueDate).toBeUndefined();
            expect(comp.fileUploadExercise.releaseDate).toBeUndefined();
            expect(comp.fileUploadExercise.dueDate).toBeUndefined();
        }));
    });

    it('should updateCategories properly by making category available for selection again when removing it', () => {
        comp.fileUploadExercise = fileUploadExercise;
        comp.exerciseCategories = [];
        const newCategories = [new ExerciseCategory('Easy', undefined), new ExerciseCategory('Hard', undefined)];

        comp.updateCategories(newCategories);

        expect(comp.fileUploadExercise.categories).toEqual(newCategories);
        expect(comp.exerciseCategories).toEqual(newCategories);
    });
});
