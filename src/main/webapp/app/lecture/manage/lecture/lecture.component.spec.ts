import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { LectureComponent, LectureDateFilter } from 'app/lecture/manage/lecture/lecture.component';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { LectureImportComponent } from 'app/lecture/manage/lecture-import/lecture-import.component';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AttachmentVideoUnit, IngestionState } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { PdfUploadTarget, PdfUploadTargetDialogComponent } from 'app/lecture/manage/pdf-upload-target-dialog/pdf-upload-target-dialog.component';
import { AlertService } from 'app/shared/service/alert.service';

describe('Lecture', () => {
    let lectureComponentFixture: ComponentFixture<LectureComponent>;
    let lectureComponent: LectureComponent;
    let lectureService: LectureService;
    let profileService: ProfileService;
    let modalService: NgbModal;

    let pastLecture: Lecture;
    let pastLecture2: Lecture;
    let currentLecture: Lecture;
    let currentLecture2: Lecture;
    let currentLecture3: Lecture;
    let futureLecture: Lecture;
    let futureLecture2: Lecture;
    let unspecifiedLecture: Lecture;
    let lectureToIngest: Lecture;

    beforeEach(() => {
        const lastWeek = dayjs().subtract(1, 'week');
        const yesterday = dayjs().subtract(1, 'day');
        const tomorrow = dayjs().add(1, 'day');
        const nextWeek = dayjs().add(1, 'week');

        pastLecture = new Lecture();
        pastLecture.id = 6;
        pastLecture.endDate = yesterday;
        pastLecture.title = 'introduction to Computer Science';

        pastLecture2 = new Lecture();
        pastLecture2.id = 7;
        pastLecture2.startDate = lastWeek;
        pastLecture2.endDate = yesterday;
        pastLecture2.title = 'Data Structures';

        currentLecture = new Lecture();
        currentLecture.id = 4;
        currentLecture.startDate = yesterday;
        currentLecture.endDate = tomorrow;
        currentLecture.title = 'Algorithms';

        currentLecture2 = new Lecture();
        currentLecture2.id = 5;
        currentLecture2.startDate = yesterday;
        currentLecture2.title = 'operating Systems';

        currentLecture3 = new Lecture();
        currentLecture3.id = 3;
        currentLecture3.endDate = tomorrow;
        currentLecture3.title = 'computer Networks';

        futureLecture = new Lecture();
        futureLecture.id = 2;
        futureLecture.startDate = tomorrow;
        futureLecture.title = 'Database Systems';

        futureLecture2 = new Lecture();
        futureLecture2.id = 8;
        futureLecture2.startDate = tomorrow;
        futureLecture2.endDate = nextWeek;
        futureLecture2.title = 'Artificial Intelligence';

        unspecifiedLecture = new Lecture();
        unspecifiedLecture.id = 1;
        unspecifiedLecture.title = 'machine Learning';

        lectureToIngest = new Lecture();
        lectureToIngest.id = 1;
        lectureToIngest.title = 'machine Learning';
        lectureToIngest.course = new Course();
        lectureToIngest.course.id = 99;

        const profileInfo = {
            activeProfiles: [],
        } as unknown as ProfileInfo;

        TestBed.configureTestingModule({
            imports: [FaIconComponent],
            declarations: [
                LectureComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(DocumentationButtonComponent),
                MockDirective(TranslateDirective),
                MockRouterLinkDirective,
                MockDirective(SortDirective),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: Router, useClass: MockRouter },
                { provide: ProfileService, useValue: new MockProfileService() },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({
                                courseId: '1',
                            }),
                        },
                    },
                },
                MockProvider(LectureService, {
                    findAllByCourseId: () => {
                        return of(
                            new HttpResponse({
                                body: [pastLecture, pastLecture2, currentLecture, currentLecture2, currentLecture3, futureLecture, futureLecture2, unspecifiedLecture],
                                status: 200,
                            }),
                        );
                    },
                    import: (_courseId, lectureId) => {
                        return of(
                            new HttpResponse({
                                body: { id: lectureId } as Lecture,
                                status: 200,
                            }),
                        );
                    },
                    delete: () => {
                        return of(new HttpResponse({ status: 200 }));
                    },
                }),
                MockProvider(IrisSettingsService),
                MockProvider(AttachmentVideoUnitService),
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                lectureComponentFixture = TestBed.createComponent(LectureComponent);
                lectureComponent = lectureComponentFixture.componentInstance;
                lectureService = TestBed.inject(LectureService);
                modalService = TestBed.inject(NgbModal);
                profileService = TestBed.inject(ProfileService);
                jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should fetch lectures when initialized', () => {
        const findAllSpy = jest.spyOn(lectureService, 'findAllByCourseId');

        lectureComponentFixture.detectChanges();

        expect(findAllSpy).toHaveBeenCalledOnce();
        expect(findAllSpy).toHaveBeenCalledWith(1);
        expect(lectureComponent.lectures).toBeArrayOfSize(8);
    });

    it('should delete lecture', () => {
        const deleteSpy = jest.spyOn(lectureService, 'delete');

        lectureComponentFixture.detectChanges();
        lectureComponent.deleteLecture(pastLecture.id!);

        expect(deleteSpy).toHaveBeenCalledOnce();
        expect(deleteSpy).toHaveBeenCalledWith(pastLecture.id!);
        expect(lectureComponent.lectures).toBeArrayOfSize(7);
        expect(lectureComponent.lectures).not.toContain(pastLecture);
        expect(lectureComponent.filteredLectures).toEqual(lectureComponent.lectures);
    });

    it('should import lecture', async () => {
        const promise = new Promise((resolve) => {
            resolve({ id: 123 } as Lecture);
        });
        const openSpy = jest.spyOn(modalService, 'open').mockReturnValue({ result: promise } as NgbModalRef);
        const importSpy = jest.spyOn(lectureService, 'import');

        lectureComponentFixture.detectChanges();
        lectureComponent.openImportModal();

        expect(openSpy).toHaveBeenCalledOnce();
        expect(openSpy).toHaveBeenCalledWith(LectureImportComponent, { size: 'lg', backdrop: 'static' });

        await expect(promise)
            .toResolve()
            .then(() => {
                expect(importSpy).toHaveBeenCalledOnce();
                expect(importSpy).toHaveBeenCalledWith(1, 123);
                expect(lectureComponent.lectures).toBeArrayOfSize(9);
            });
    });

    it('should show all lectures sorted', () => {
        lectureComponentFixture.detectChanges();

        // No filters selected
        lectureComponent.toggleFilters([]);

        const filteredLectures = lectureComponent.filteredLectures;
        expect(lectureComponent.filteredLectures).toIncludeAllMembers(lectureComponent.lectures);
        expect(lectureComponent.filteredLectures.map((lecture) => lecture.id)).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);

        // Apply all filters
        lectureComponent.toggleFilters([LectureDateFilter.PAST, LectureDateFilter.CURRENT, LectureDateFilter.FUTURE, LectureDateFilter.UNSPECIFIED]);
        expect(lectureComponent.activeFilters.size).toBe(4);
        expect(lectureComponent.filteredLectures).toIncludeAllMembers(lectureComponent.lectures);

        // Check that no filters is equal to all filters
        expect(lectureComponent.filteredLectures).toEqual(filteredLectures);
    });

    it('should filter for past lectures', () => {
        lectureComponentFixture.detectChanges();
        lectureComponent.toggleFilters([LectureDateFilter.PAST]);
        expect(lectureComponent.filteredLectures).toBeArrayOfSize(2);
        expect(lectureComponent.filteredLectures).toIncludeAllMembers([pastLecture, pastLecture2]);
    });

    it('should filter for current lectures', () => {
        lectureComponentFixture.detectChanges();
        lectureComponent.toggleFilters([LectureDateFilter.CURRENT]);
        expect(lectureComponent.filteredLectures).toBeArrayOfSize(3);
        expect(lectureComponent.filteredLectures).toIncludeAllMembers([currentLecture, currentLecture2, currentLecture3]);
    });

    it('should filter for future lectures', () => {
        lectureComponentFixture.detectChanges();
        lectureComponent.toggleFilters([LectureDateFilter.FUTURE]);
        expect(lectureComponent.filteredLectures).toBeArrayOfSize(2);
        expect(lectureComponent.filteredLectures).toIncludeAllMembers([futureLecture, futureLecture2]);
    });

    it('should filter for lectures without dates', () => {
        lectureComponentFixture.detectChanges();
        lectureComponent.toggleFilters([LectureDateFilter.UNSPECIFIED]);
        expect(lectureComponent.filteredLectures).toBeArrayOfSize(1);
        expect(lectureComponent.filteredLectures).toContainEqual(unspecifiedLecture);
    });

    it('should call the service to ingest lectures when ingestLecturesInPyris is called', () => {
        lectureComponent.lectures = [lectureToIngest];
        const ingestSpy = jest.spyOn(lectureService, 'ingestLecturesInPyris').mockImplementation(() => of(new HttpResponse<void>({ status: 200 })));
        lectureComponent.ingestLecturesInPyris();
        expect(ingestSpy).toHaveBeenCalledWith(lectureToIngest.course?.id);
        expect(ingestSpy).toHaveBeenCalledOnce();
    });

    it('should update ingestion states correctly when getIngestionState returns data', () => {
        lectureComponent.courseId = 99;
        lectureComponent.lectures = [lectureToIngest, pastLecture];
        const mockIngestionStates = {
            1: IngestionState.DONE,
            6: IngestionState.PARTIALLY_INGESTED,
        };

        jest.spyOn(lectureService, 'getIngestionState').mockReturnValue(
            of(
                new HttpResponse({
                    body: mockIngestionStates,
                    status: 200,
                }),
            ),
        );

        lectureComponent.updateIngestionStates();

        expect(lectureService.getIngestionState).toHaveBeenCalledWith(lectureToIngest.course!.id!);
        expect(lectureToIngest.ingested).toBe(IngestionState.DONE);
        expect(pastLecture.ingested).toBe(IngestionState.PARTIALLY_INGESTED);
    });

    it('should not call the service if the first lecture does not exist', () => {
        lectureComponent.lectures = [];
        const ingestSpy = jest.spyOn(lectureService, 'ingestLecturesInPyris').mockImplementation(() => of(new HttpResponse<void>({ status: 200 })));
        lectureComponent.ingestLecturesInPyris();
        expect(ingestSpy).not.toHaveBeenCalled();
    });

    it('should do nothing when error occurs', () => {
        lectureComponent.lectures = [lectureToIngest];
        jest.spyOn(lectureService, 'ingestLecturesInPyris').mockReturnValue(throwError(() => new Error('Error while ingesting')));
        lectureComponent.ingestLecturesInPyris();
    });

    function setupInitializationTests() {
        const updateIngestionStatesSpy = jest.spyOn(lectureComponent, 'updateIngestionStates');
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);

        // Mock the services with Subjects to control when they emit
        const lectureSubject = new Subject<HttpResponse<Lecture[]>>();
        const settingsSubject = new Subject<any>();

        jest.spyOn(lectureService, 'findAllByCourseId').mockReturnValue(lectureSubject.asObservable());
        jest.spyOn(TestBed.inject(IrisSettingsService), 'getCourseSettingsWithRateLimit').mockReturnValue(settingsSubject.asObservable());

        // Set up test data
        const lectures = [lectureToIngest];
        const enabledSettings = {
            courseId: 1,
            settings: {
                enabled: true,
                customInstructions: '',
                variant: 'default',
                rateLimit: { requests: 100, timeframeHours: 24 },
            },
            effectiveRateLimit: { requests: 100, timeframeHours: 24 },
            applicationRateLimitDefaults: { requests: 50, timeframeHours: 12 },
        };

        return {
            updateIngestionStatesSpy,
            lectureSubject,
            settingsSubject,
            lectures,
            enabledSettings,
        };
    }

    it('should properly load ingestion state when lectures load first, then settings', () => {
        const { updateIngestionStatesSpy, lectureSubject, settingsSubject, lectures, enabledSettings } = setupInitializationTests();

        // Initialize component
        lectureComponent.ngOnInit();

        // Emit lectures first
        lectureSubject.next(new HttpResponse({ body: lectures, status: 200 }));
        expect(updateIngestionStatesSpy).not.toHaveBeenCalled();

        // Then emit settings
        settingsSubject.next(enabledSettings);
        expect(updateIngestionStatesSpy).toHaveBeenCalledOnce();
    });

    it('should properly load ingestion state when settings load first, then lectures', () => {
        const { updateIngestionStatesSpy, lectureSubject, settingsSubject, lectures, enabledSettings } = setupInitializationTests();

        // Initialize component
        lectureComponent.ngOnInit();

        // Emit settings first
        settingsSubject.next(enabledSettings);
        expect(updateIngestionStatesSpy).not.toHaveBeenCalled();

        // Then emit lectures
        lectureSubject.next(new HttpResponse({ body: lectures, status: 200 }));
        expect(updateIngestionStatesSpy).toHaveBeenCalledOnce();
    });

    it('should return lecture id from trackId', () => {
        const lecture = new Lecture();
        lecture.id = 42;

        const result = lectureComponent.trackId(0, lecture);

        expect(result).toBe(42);
    });

    it('should navigate to lecture creation page with existing lectures state', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        lectureComponentFixture.detectChanges();

        lectureComponent.navigateToLectureCreationPage();

        expect(navigateSpy).toHaveBeenCalledWith(['course-management', 1, 'lectures', 'new'], {
            state: { existingLectures: lectureComponent.lectures },
        });
    });

    it('should handle error when deleting lecture fails', () => {
        const errorMessage = 'Delete failed';
        jest.spyOn(lectureService, 'delete').mockReturnValue(throwError(() => new HttpErrorResponse({ error: errorMessage, status: 400 })));

        lectureComponentFixture.detectChanges();
        const initialLectureCount = lectureComponent.lectures.length;

        // Subscribe to dialogError$ to capture the error
        let capturedError = '';
        lectureComponent.dialogError$.subscribe((error) => (capturedError = error));

        lectureComponent.deleteLecture(pastLecture.id!);

        expect(lectureComponent.lectures).toHaveLength(initialLectureCount);
        expect(capturedError).toBeTruthy();
    });

    it('should handle import modal dismissal gracefully', async () => {
        const dismissPromise = Promise.reject('dismissed');
        jest.spyOn(modalService, 'open').mockReturnValue({ result: dismissPromise } as unknown as NgbModalRef);
        const importSpy = jest.spyOn(lectureService, 'import');

        lectureComponentFixture.detectChanges();
        lectureComponent.openImportModal();

        await expect(dismissPromise).toReject();
        expect(importSpy).not.toHaveBeenCalled();
    });

    it('should handle import error', fakeAsync(() => {
        const alertService = TestBed.inject(AlertService);
        const errorSpy = jest.spyOn(alertService, 'error');
        jest.spyOn(modalService, 'open').mockReturnValue({ result: Promise.resolve({ id: 123 } as Lecture) } as unknown as NgbModalRef);
        // Use status 400 because onError explicitly skips 500 errors
        jest.spyOn(lectureService, 'import').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));

        lectureComponentFixture.detectChanges();
        lectureComponent.openImportModal();
        tick();

        expect(errorSpy).toHaveBeenCalledWith('error.http.400');
    }));

    it('should handle error when loading lectures fails', fakeAsync(() => {
        // Set up error mock before triggering ngOnInit
        // Use status 400 because onError explicitly skips 500 errors
        jest.spyOn(lectureService, 'findAllByCourseId').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const alertService = TestBed.inject(AlertService);
        const errorSpy = jest.spyOn(alertService, 'error');

        // Manually trigger ngOnInit to use the error mock
        lectureComponent.ngOnInit();
        tick();

        expect(errorSpy).toHaveBeenCalledWith('error.http.400');
    }));

    it('should handle error when updating ingestion states fails', () => {
        const alertService = TestBed.inject(AlertService);
        const errorSpy = jest.spyOn(alertService, 'error');
        lectureComponent.courseId = 1;
        lectureComponent.lectures = [lectureToIngest];
        jest.spyOn(lectureService, 'getIngestionState').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        lectureComponent.updateIngestionStates();

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.iris.ingestionAlert.pyrisError');
    });

    describe('PDF drop zone functionality', () => {
        let attachmentVideoUnitService: AttachmentVideoUnitService;
        let alertService: AlertService;
        let router: Router;

        beforeEach(() => {
            attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
            alertService = TestBed.inject(AlertService);
            router = TestBed.inject(Router);
        });

        it('should open dialog when PDF files are dropped', () => {
            const openSpy = jest.spyOn(modalService, 'open').mockReturnValue({
                componentInstance: { lectures: [], initializeWithFiles: jest.fn() },
                result: Promise.reject('dismissed'),
            } as unknown as NgbModalRef);

            lectureComponentFixture.detectChanges();
            const files = [new File(['content'], 'test.pdf', { type: 'application/pdf' })];

            lectureComponent.onPdfFilesDropped(files);

            expect(openSpy).toHaveBeenCalledWith(PdfUploadTargetDialogComponent, { size: 'lg', backdrop: 'static' });
        });

        it('should pass lectures and files to dialog', () => {
            const initializeWithFilesSpy = jest.fn();
            jest.spyOn(modalService, 'open').mockReturnValue({
                componentInstance: { lectures: [], initializeWithFiles: initializeWithFilesSpy },
                result: Promise.reject('dismissed'),
            } as unknown as NgbModalRef);

            lectureComponentFixture.detectChanges();
            const files = [new File(['content'], 'test.pdf', { type: 'application/pdf' })];

            lectureComponent.onPdfFilesDropped(files);

            expect(initializeWithFilesSpy).toHaveBeenCalledWith(files);
        });

        it('should create new lecture with units when dialog returns new target', fakeAsync(() => {
            const createdLecture = new Lecture();
            createdLecture.id = 999;
            createdLecture.title = 'New Lecture';
            createdLecture.course = { id: 1 } as Course;

            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 1;

            jest.spyOn(lectureService, 'create').mockReturnValue(of(new HttpResponse({ body: createdLecture, status: 201 })));
            jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));
            const navigateSpy = jest.spyOn(router, 'navigate');
            const successSpy = jest.spyOn(alertService, 'success');

            const dialogResult: PdfUploadTarget = {
                targetType: 'new',
                newLectureTitle: 'New Lecture',
            };

            jest.spyOn(modalService, 'open').mockReturnValue({
                componentInstance: { lectures: [], initializeWithFiles: jest.fn() },
                result: Promise.resolve(dialogResult),
            } as unknown as NgbModalRef);

            lectureComponentFixture.detectChanges();
            const files = [new File(['content'], 'Chapter_01.pdf', { type: 'application/pdf' })];

            lectureComponent.onPdfFilesDropped(files);
            tick();

            expect(lectureService.create).toHaveBeenCalled();
            expect(attachmentVideoUnitService.createAttachmentVideoUnitFromFile).toHaveBeenCalled();
            expect(successSpy).toHaveBeenCalledWith('artemisApp.lecture.pdfUpload.success');
            expect(navigateSpy).toHaveBeenCalledWith(['course-management', 1, 'lectures', 999, 'edit']);
            expect(lectureComponent.isUploadingPdfs()).toBeFalse();
        }));

        it('should create units for existing lecture when dialog returns existing target', fakeAsync(() => {
            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 1;

            jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));
            const navigateSpy = jest.spyOn(router, 'navigate');
            const successSpy = jest.spyOn(alertService, 'success');

            const dialogResult: PdfUploadTarget = {
                targetType: 'existing',
                lectureId: 42,
            };

            jest.spyOn(modalService, 'open').mockReturnValue({
                componentInstance: { lectures: [], initializeWithFiles: jest.fn() },
                result: Promise.resolve(dialogResult),
            } as unknown as NgbModalRef);

            lectureComponentFixture.detectChanges();
            const files = [new File(['content'], 'Chapter_01.pdf', { type: 'application/pdf' })];

            lectureComponent.onPdfFilesDropped(files);
            tick();

            expect(attachmentVideoUnitService.createAttachmentVideoUnitFromFile).toHaveBeenCalled();
            expect(successSpy).toHaveBeenCalledWith('artemisApp.lecture.pdfUpload.success');
            expect(navigateSpy).toHaveBeenCalledWith(['course-management', 1, 'lectures', 42, 'edit']);
            expect(lectureComponent.isUploadingPdfs()).toBeFalse();
        }));

        it('should handle multiple PDF files', fakeAsync(() => {
            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 1;

            const createSpy = jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));

            const dialogResult: PdfUploadTarget = {
                targetType: 'existing',
                lectureId: 42,
            };

            jest.spyOn(modalService, 'open').mockReturnValue({
                componentInstance: { lectures: [], initializeWithFiles: jest.fn() },
                result: Promise.resolve(dialogResult),
            } as unknown as NgbModalRef);

            lectureComponentFixture.detectChanges();
            const files = [
                new File(['content1'], 'file1.pdf', { type: 'application/pdf' }),
                new File(['content2'], 'file2.pdf', { type: 'application/pdf' }),
                new File(['content3'], 'file3.pdf', { type: 'application/pdf' }),
            ];

            lectureComponent.onPdfFilesDropped(files);
            tick();

            expect(createSpy).toHaveBeenCalledTimes(3);
        }));

        it('should handle error when creating lecture fails', fakeAsync(() => {
            jest.spyOn(lectureService, 'create').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            const dialogResult: PdfUploadTarget = {
                targetType: 'new',
                newLectureTitle: 'New Lecture',
            };

            jest.spyOn(modalService, 'open').mockReturnValue({
                componentInstance: { lectures: [], initializeWithFiles: jest.fn() },
                result: Promise.resolve(dialogResult),
            } as unknown as NgbModalRef);

            lectureComponentFixture.detectChanges();
            const files = [new File(['content'], 'test.pdf', { type: 'application/pdf' })];

            lectureComponent.onPdfFilesDropped(files);
            tick();

            // Error handling sets isUploadingPdfs to false
            expect(lectureComponent.isUploadingPdfs()).toBeFalse();
        }));

        it('should handle error when creating attachment unit fails', fakeAsync(() => {
            jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            const dialogResult: PdfUploadTarget = {
                targetType: 'existing',
                lectureId: 42,
            };

            jest.spyOn(modalService, 'open').mockReturnValue({
                componentInstance: { lectures: [], initializeWithFiles: jest.fn() },
                result: Promise.resolve(dialogResult),
            } as unknown as NgbModalRef);

            lectureComponentFixture.detectChanges();
            const files = [new File(['content'], 'test.pdf', { type: 'application/pdf' })];

            lectureComponent.onPdfFilesDropped(files);
            tick();

            // Error handling sets isUploadingPdfs to false
            expect(lectureComponent.isUploadingPdfs()).toBeFalse();
        }));

        it('should do nothing when dialog is dismissed', fakeAsync(() => {
            const createSpy = jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile');

            jest.spyOn(modalService, 'open').mockReturnValue({
                componentInstance: { lectures: [], initializeWithFiles: jest.fn() },
                result: Promise.reject('dismissed'),
            } as unknown as NgbModalRef);

            lectureComponentFixture.detectChanges();
            const files = [new File(['content'], 'test.pdf', { type: 'application/pdf' })];

            lectureComponent.onPdfFilesDropped(files);
            tick();

            expect(createSpy).not.toHaveBeenCalled();
        }));

        it('should call service with correct lecture id and file', fakeAsync(() => {
            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 1;

            const createSpy = jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));

            const dialogResult: PdfUploadTarget = {
                targetType: 'existing',
                lectureId: 42,
            };

            jest.spyOn(modalService, 'open').mockReturnValue({
                componentInstance: { lectures: [], initializeWithFiles: jest.fn() },
                result: Promise.resolve(dialogResult),
            } as unknown as NgbModalRef);

            lectureComponentFixture.detectChanges();
            const pdfFile = new File(['content'], 'Chapter_01_Introduction.pdf', { type: 'application/pdf' });

            lectureComponent.onPdfFilesDropped([pdfFile]);
            tick();

            expect(createSpy).toHaveBeenCalledWith(42, pdfFile);
        }));
    });
});
