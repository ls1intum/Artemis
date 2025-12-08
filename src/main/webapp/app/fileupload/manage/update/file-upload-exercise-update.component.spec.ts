import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, UrlSegment } from '@angular/router';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { BehaviorSubject, of } from 'rxjs';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseUpdateWarningService } from 'app/exercise/exercise-update-warning/exercise-update-warning.service';

import { FileUploadExerciseUpdateComponent } from 'app/fileupload/manage/update/file-upload-exercise-update.component';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateService } from '@ngx-translate/core';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';

import dayjs from 'dayjs/esm';

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
    let activatedRoute: MockActivatedRoute;

    beforeAll(() => {
        global.ResizeObserver = MockResizeObserver;
    });

    beforeEach(async () => {
        const mockRoute = new MockActivatedRoute({});
        mockRoute.url = new BehaviorSubject<UrlSegment[]>([]);
        mockRoute.data = new BehaviorSubject({});
        mockRoute.params = new BehaviorSubject({});
        TestBed.configureTestingModule({
            imports: [OwlDateTimeModule, OwlNativeDateTimeModule],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: ActivatedRoute, useValue: mockRoute },
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
        activatedRoute = TestBed.inject(ActivatedRoute) as unknown as MockActivatedRoute;
    });

    describe('save', () => {
        describe('new exercise', () => {
            const course = { id: 1 } as Course;
            const exercise = new FileUploadExercise(course, undefined);
            exercise.channelName = 'test';

            beforeEach(() => {
                (activatedRoute.data as BehaviorSubject<any>).next({ fileUploadExercise: exercise });
                (activatedRoute.url as BehaviorSubject<any>).next([{ path: 'exercise-groups' } as UrlSegment]);
            });

            it('should call create service and refresh calendar on save for new entity', fakeAsync(() => {
                // GIVEN
                fixture.detectChanges(); // Trigger effects
                tick();

                const modalService = TestBed.inject(NgbModal);
                jest.spyOn(modalService, 'hasOpenModals').mockReturnValue(false);

                const popupService = TestBed.inject(ExerciseUpdateWarningService);
                jest.spyOn(popupService as any, 'checkExerciseBeforeUpdate').mockReturnValue(Promise.resolve());

                const entity = { ...exercise };
                jest.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: entity })));

                // WHEN
                comp.save();
                tick(1000);
                fixture.detectChanges();

                // THEN
                expect(service.create).toHaveBeenCalledWith(entity);
            }));
        });

        describe('existing exercise', () => {
            const course = { id: 1 } as Course;
            const exercise = new FileUploadExercise(course, undefined);
            exercise.id = 123;
            exercise.channelName = 'test';

            beforeEach(() => {
                (activatedRoute.data as BehaviorSubject<any>).next({ fileUploadExercise: exercise });
                (activatedRoute.url as BehaviorSubject<any>).next([{ path: 'exercise-groups' } as UrlSegment]);
            });

            it('should call update service and refresh calendar on save for existing entity', async () => {
                // GIVEN
                const entity = { ...exercise };
                jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));

                fixture.detectChanges(); // Run effects
                await fixture.whenStable();

                // WHEN
                await comp.save();
                fixture.detectChanges();

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity, {});
                expect(comp.isSaving()).toBeFalse();
            }, 10000);
        });
    });

    describe('init', () => {
        it('should be in exam mode if exerciseGroup present', fakeAsync(() => {
            const exercise = new FileUploadExercise(undefined, new ExerciseGroup());
            activatedRoute.data = of({ fileUploadExercise: exercise });
            activatedRoute.url = of([{ path: 'exercise-groups' } as UrlSegment]);

            // WHEN
            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            comp = fixture.componentInstance;
            fixture.detectChanges();
            tick();

            // THEN
            expect(comp.isExamMode()).toBeTrue();
            expect(comp.fileUploadExercise()).toEqual(exercise);
        }));

        it('should not be in exam mode if course present', fakeAsync(() => {
            const exercise = new FileUploadExercise(new Course(), undefined);
            activatedRoute.data = of({ fileUploadExercise: exercise });
            activatedRoute.url = of([{ path: 'new' } as UrlSegment]);

            // WHEN
            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            comp = fixture.componentInstance;
            fixture.detectChanges();
            tick();

            // THEN
            expect(comp.isExamMode()).toBeFalse();
            expect(comp.fileUploadExercise().id).toBe(exercise.id);
            expect(comp.fileUploadExercise().course?.id).toBe(exercise.course?.id);
        }));
    });

    describe('imported exercise', () => {
        const course = { id: 1 } as Course;
        const exercise = new FileUploadExercise(course, undefined);

        beforeEach(() => {
            (activatedRoute.data as BehaviorSubject<any>).next({ fileUploadExercise: exercise });
            (activatedRoute.url as BehaviorSubject<any>).next([{ path: 'import' } as UrlSegment]);
            (activatedRoute.params as BehaviorSubject<any>).next({ courseId: 1 });
        });

        it('should call import service on save', async () => {
            // GIVEN
            fixture.detectChanges();
            await fixture.whenStable();

            expect(comp.isImport()).toBeTrue();

            const entity = { ...exercise };
            jest.spyOn(service, 'import').mockReturnValue(of(new HttpResponse({ body: entity })));

            // WHEN
            await comp.save();
            fixture.detectChanges();

            // THEN
            expect(service.import).toHaveBeenCalledWith(expect.objectContaining({ id: entity.id }));
            expect(comp.isSaving()).toBeFalse();
        }, 10000);

        it('should reset dates and set isImport for Course import', fakeAsync(() => {
            const ex = new FileUploadExercise(new Course(), undefined);
            ex.id = 1;
            ex.releaseDate = dayjs();
            ex.dueDate = dayjs();
            ex.assessmentDueDate = dayjs();

            activatedRoute.data = of({ fileUploadExercise: ex });
            activatedRoute.url = of([{ path: 'import' } as UrlSegment]);
            activatedRoute.params = of({ courseId: 1 });

            fixture = TestBed.createComponent(FileUploadExerciseUpdateComponent);
            comp = fixture.componentInstance;
            fixture.detectChanges();
            tick();

            expect(comp.isImport()).toBeTrue();
            expect(comp.isExamMode()).toBeFalse();
            expect(comp.fileUploadExercise().assessmentDueDate).toBeUndefined();
            expect(comp.fileUploadExercise().releaseDate).toBeUndefined();
            expect(comp.fileUploadExercise().dueDate).toBeUndefined();
        }));
    });

    it('should updateCategories properly', () => {
        fixture.detectChanges();
        const newCategories = [new ExerciseCategory('Easy', undefined), new ExerciseCategory('Hard', undefined)];
        comp.updateCategories(newCategories);

        expect(comp.fileUploadExercise().categories).toEqual(newCategories);
        expect(comp.exerciseCategories()).toEqual(newCategories);
    });
});
