import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
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
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { PdfUploadTarget } from 'app/lecture/manage/pdf-upload-target-dialog/pdf-upload-target-dialog.component';
import { AlertService } from 'app/shared/service/alert.service';

describe('Lecture', () => {
    setupTestBed({ zoneless: true });

    let lectureComponentFixture: ComponentFixture<LectureComponent>;
    let lectureComponent: LectureComponent;
    let lectureService: LectureService;
    let profileService: ProfileService;
    let dialogService: DialogService;

    let pastLecture: Lecture;
    let pastLecture2: Lecture;
    let currentLecture: Lecture;
    let currentLecture2: Lecture;
    let currentLecture3: Lecture;
    let futureLecture: Lecture;
    let futureLecture2: Lecture;
    let unspecifiedLecture: Lecture;
    let lectureToIngest: Lecture;

    beforeEach(async () => {
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

        await TestBed.configureTestingModule({
            imports: [
                FaIconComponent,
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
                { provide: DialogService, useValue: { open: vi.fn() } },
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
                                body: { id: lectureId, course: { id: _courseId } } as Lecture,
                                status: 200,
                            }),
                        );
                    },
                    delete: () => {
                        return of(new HttpResponse<void>({ status: 200 }));
                    },
                    create: () => {
                        return of(
                            new HttpResponse({
                                body: { id: 999, course: { id: 1 } } as Lecture,
                                status: 201,
                            }),
                        );
                    },
                }),
                MockProvider(IrisSettingsService),
                MockProvider(AttachmentVideoUnitService),
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        lectureComponentFixture = TestBed.createComponent(LectureComponent);
        lectureComponent = lectureComponentFixture.componentInstance;
        lectureService = TestBed.inject(LectureService);
        dialogService = TestBed.inject(DialogService);
        profileService = TestBed.inject(ProfileService);
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should fetch lectures when initialized', async () => {
        const findAllSpy = vi.spyOn(lectureService, 'findAllByCourseId');

        lectureComponentFixture.detectChanges();
        await lectureComponentFixture.whenStable();

        expect(findAllSpy).toHaveBeenCalledOnce();
        expect(findAllSpy).toHaveBeenCalledWith(1);
        expect(lectureComponent.lectures).toHaveLength(8);
    });

    it('should delete lecture', async () => {
        const deleteSpy = vi.spyOn(lectureService, 'delete');

        lectureComponentFixture.detectChanges();
        await lectureComponentFixture.whenStable();
        lectureComponent.deleteLecture(pastLecture.id!);
        await lectureComponentFixture.whenStable();

        expect(deleteSpy).toHaveBeenCalledOnce();
        expect(deleteSpy).toHaveBeenCalledWith(pastLecture.id!);
        expect(lectureComponent.lectures).toHaveLength(7);
        expect(lectureComponent.lectures).not.toContain(pastLecture);
        expect(lectureComponent.filteredLectures).toEqual(lectureComponent.lectures);
    });

    it('should import lecture', async () => {
        const onCloseSubject = new Subject<Lecture | undefined>();
        const mockDialogRef = {
            onClose: onCloseSubject.asObservable(),
            close: vi.fn(),
        } as unknown as DynamicDialogRef;

        const openSpy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
        const importSpy = vi.spyOn(lectureService, 'import');

        lectureComponentFixture.detectChanges();
        await lectureComponentFixture.whenStable();
        lectureComponent.openImportModal();

        expect(openSpy).toHaveBeenCalledOnce();
        expect(openSpy).toHaveBeenCalledWith(LectureImportComponent, expect.objectContaining({ modal: true }));

        // Emit the result from the dialog
        onCloseSubject.next({ id: 123 } as Lecture);
        await lectureComponentFixture.whenStable();

        expect(importSpy).toHaveBeenCalledOnce();
        expect(importSpy).toHaveBeenCalledWith(1, 123);
        expect(lectureComponent.lectures).toHaveLength(9);
    });

    it('should show all lectures sorted', async () => {
        lectureComponentFixture.detectChanges();
        await lectureComponentFixture.whenStable();

        // No filters selected
        lectureComponent.toggleFilters([]);

        const filteredLectures = lectureComponent.filteredLectures;
        expect(lectureComponent.filteredLectures).toEqual(expect.arrayContaining(lectureComponent.lectures));
        expect(lectureComponent.filteredLectures.map((lecture) => lecture.id)).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);

        // Apply all filters
        lectureComponent.toggleFilters([LectureDateFilter.PAST, LectureDateFilter.CURRENT, LectureDateFilter.FUTURE, LectureDateFilter.UNSPECIFIED]);
        expect(lectureComponent.activeFilters.size).toBe(4);
        expect(lectureComponent.filteredLectures).toEqual(expect.arrayContaining(lectureComponent.lectures));

        // Check that no filters is equal to all filters
        expect(lectureComponent.filteredLectures).toEqual(filteredLectures);
    });

    it('should filter for past lectures', async () => {
        lectureComponentFixture.detectChanges();
        await lectureComponentFixture.whenStable();
        lectureComponent.toggleFilters([LectureDateFilter.PAST]);
        expect(lectureComponent.filteredLectures).toHaveLength(2);
        expect(lectureComponent.filteredLectures).toEqual(expect.arrayContaining([pastLecture, pastLecture2]));
    });

    it('should filter for current lectures', async () => {
        lectureComponentFixture.detectChanges();
        await lectureComponentFixture.whenStable();
        lectureComponent.toggleFilters([LectureDateFilter.CURRENT]);
        expect(lectureComponent.filteredLectures).toHaveLength(3);
        expect(lectureComponent.filteredLectures).toEqual(expect.arrayContaining([currentLecture, currentLecture2, currentLecture3]));
    });

    it('should filter for future lectures', async () => {
        lectureComponentFixture.detectChanges();
        await lectureComponentFixture.whenStable();
        lectureComponent.toggleFilters([LectureDateFilter.FUTURE]);
        expect(lectureComponent.filteredLectures).toHaveLength(2);
        expect(lectureComponent.filteredLectures).toEqual(expect.arrayContaining([futureLecture, futureLecture2]));
    });

    it('should filter for lectures without dates', async () => {
        lectureComponentFixture.detectChanges();
        await lectureComponentFixture.whenStable();
        lectureComponent.toggleFilters([LectureDateFilter.UNSPECIFIED]);
        expect(lectureComponent.filteredLectures).toHaveLength(1);
        expect(lectureComponent.filteredLectures).toContainEqual(unspecifiedLecture);
    });

    it('should return lecture id from trackId', () => {
        const lecture = new Lecture();
        lecture.id = 42;

        const result = lectureComponent.trackId(0, lecture);

        expect(result).toBe(42);
    });

    it('should navigate to lecture creation page with existing lectures state', async () => {
        const router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigate');
        lectureComponentFixture.detectChanges();
        await lectureComponentFixture.whenStable();

        lectureComponent.navigateToLectureCreationPage();

        expect(navigateSpy).toHaveBeenCalledWith(['course-management', 1, 'lectures', 'new'], {
            state: { existingLectures: lectureComponent.lectures },
        });
    });

    it('should handle error when deleting lecture fails', async () => {
        const errorMessage = 'Delete failed';
        vi.spyOn(lectureService, 'delete').mockReturnValue(throwError(() => new HttpErrorResponse({ error: errorMessage, status: 400 })));

        lectureComponentFixture.detectChanges();
        await lectureComponentFixture.whenStable();
        const initialLectureCount = lectureComponent.lectures.length;

        // Subscribe to dialogError$ to capture the error
        let capturedError = '';
        lectureComponent.dialogError$.subscribe((error) => (capturedError = error));

        lectureComponent.deleteLecture(pastLecture.id!);
        await lectureComponentFixture.whenStable();

        expect(lectureComponent.lectures).toHaveLength(initialLectureCount);
        expect(capturedError).toBeTruthy();
    });

    it('should handle import modal dismissal gracefully', async () => {
        const onCloseSubject = new Subject<Lecture | undefined>();
        const mockDialogRef = {
            onClose: onCloseSubject.asObservable(),
            close: vi.fn(),
        } as unknown as DynamicDialogRef;

        vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
        const importSpy = vi.spyOn(lectureService, 'import');

        lectureComponentFixture.detectChanges();
        await lectureComponentFixture.whenStable();
        lectureComponent.openImportModal();

        // Emit undefined (dismissed)
        onCloseSubject.next(undefined);
        await lectureComponentFixture.whenStable();

        expect(importSpy).not.toHaveBeenCalled();
    });

    it('should handle import error', async () => {
        const alertService = TestBed.inject(AlertService);
        const errorSpy = vi.spyOn(alertService, 'error');

        const onCloseSubject = new Subject<Lecture | undefined>();
        const mockDialogRef = {
            onClose: onCloseSubject.asObservable(),
            close: vi.fn(),
        } as unknown as DynamicDialogRef;

        vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
        // Use status 400 because onError explicitly skips 500 errors
        vi.spyOn(lectureService, 'import').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));

        lectureComponentFixture.detectChanges();
        await lectureComponentFixture.whenStable();
        lectureComponent.openImportModal();

        onCloseSubject.next({ id: 123 } as Lecture);
        await lectureComponentFixture.whenStable();

        expect(errorSpy).toHaveBeenCalledWith('error.http.400');
    });

    it('should handle error when loading lectures fails', async () => {
        // Set up error mock before triggering ngOnInit
        // Use status 400 because onError explicitly skips 500 errors
        vi.spyOn(lectureService, 'findAllByCourseId').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const alertService = TestBed.inject(AlertService);
        const errorSpy = vi.spyOn(alertService, 'error');

        // Manually trigger ngOnInit to use the error mock
        lectureComponent.ngOnInit();
        await lectureComponentFixture.whenStable();

        expect(errorSpy).toHaveBeenCalledWith('error.http.400');
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

        it('should open dialog when PDF files are dropped', async () => {
            const onCloseSubject = new Subject<PdfUploadTarget | undefined>();
            const mockDialogRef = {
                onClose: onCloseSubject.asObservable(),
                close: vi.fn(),
            } as unknown as DynamicDialogRef;

            const openSpy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);

            lectureComponentFixture.detectChanges();
            await lectureComponentFixture.whenStable();
            const files = [new File(['content'], 'test.pdf', { type: 'application/pdf' })];

            lectureComponent.onPdfFilesDropped(files);

            expect(openSpy).toHaveBeenCalledOnce();
        });

        it('should create new lecture with units when dialog returns new target', async () => {
            const createdLecture = new Lecture();
            createdLecture.id = 999;
            createdLecture.title = 'New Lecture';
            createdLecture.course = { id: 1 } as Course;

            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 1;

            vi.spyOn(lectureService, 'create').mockReturnValue(of(new HttpResponse({ body: createdLecture, status: 201 })));
            vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));
            const navigateSpy = vi.spyOn(router, 'navigate');
            const successSpy = vi.spyOn(alertService, 'success');

            const dialogResult: PdfUploadTarget = {
                targetType: 'new',
                newLectureTitle: 'New Lecture',
            };

            const onCloseSubject = new Subject<PdfUploadTarget | undefined>();
            const mockDialogRef = {
                onClose: onCloseSubject.asObservable(),
                close: vi.fn(),
            } as unknown as DynamicDialogRef;

            vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);

            lectureComponentFixture.detectChanges();
            await lectureComponentFixture.whenStable();
            const files = [new File(['content'], 'Chapter_01.pdf', { type: 'application/pdf' })];

            lectureComponent.onPdfFilesDropped(files);
            onCloseSubject.next(dialogResult);
            await lectureComponentFixture.whenStable();

            expect(lectureService.create).toHaveBeenCalled();
            expect(attachmentVideoUnitService.createAttachmentVideoUnitFromFile).toHaveBeenCalled();
            expect(successSpy).toHaveBeenCalledWith('artemisApp.lecture.pdfUpload.success');
            expect(navigateSpy).toHaveBeenCalledWith(['course-management', 1, 'lectures', 999, 'edit']);
            expect(lectureComponent.isUploadingPdfs()).toBe(false);
        });

        it('should create units for existing lecture when dialog returns existing target', async () => {
            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 1;

            vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));
            const navigateSpy = vi.spyOn(router, 'navigate');
            const successSpy = vi.spyOn(alertService, 'success');

            const dialogResult: PdfUploadTarget = {
                targetType: 'existing',
                lectureId: 42,
            };

            const onCloseSubject = new Subject<PdfUploadTarget | undefined>();
            const mockDialogRef = {
                onClose: onCloseSubject.asObservable(),
                close: vi.fn(),
            } as unknown as DynamicDialogRef;

            vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);

            lectureComponentFixture.detectChanges();
            await lectureComponentFixture.whenStable();
            const files = [new File(['content'], 'Chapter_01.pdf', { type: 'application/pdf' })];

            lectureComponent.onPdfFilesDropped(files);
            onCloseSubject.next(dialogResult);
            await lectureComponentFixture.whenStable();

            expect(attachmentVideoUnitService.createAttachmentVideoUnitFromFile).toHaveBeenCalled();
            expect(successSpy).toHaveBeenCalledWith('artemisApp.lecture.pdfUpload.success');
            expect(navigateSpy).toHaveBeenCalledWith(['course-management', 1, 'lectures', 42, 'edit']);
            expect(lectureComponent.isUploadingPdfs()).toBe(false);
        });

        it('should handle multiple PDF files', async () => {
            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 1;

            const createSpy = vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));

            const dialogResult: PdfUploadTarget = {
                targetType: 'existing',
                lectureId: 42,
            };

            const onCloseSubject = new Subject<PdfUploadTarget | undefined>();
            const mockDialogRef = {
                onClose: onCloseSubject.asObservable(),
                close: vi.fn(),
            } as unknown as DynamicDialogRef;

            vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);

            lectureComponentFixture.detectChanges();
            await lectureComponentFixture.whenStable();
            const files = [
                new File(['content1'], 'file1.pdf', { type: 'application/pdf' }),
                new File(['content2'], 'file2.pdf', { type: 'application/pdf' }),
                new File(['content3'], 'file3.pdf', { type: 'application/pdf' }),
            ];

            lectureComponent.onPdfFilesDropped(files);
            onCloseSubject.next(dialogResult);
            await lectureComponentFixture.whenStable();

            expect(createSpy).toHaveBeenCalledTimes(3);
        });

        it('should handle error when creating lecture fails', async () => {
            vi.spyOn(lectureService, 'create').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            const dialogResult: PdfUploadTarget = {
                targetType: 'new',
                newLectureTitle: 'New Lecture',
            };

            const onCloseSubject = new Subject<PdfUploadTarget | undefined>();
            const mockDialogRef = {
                onClose: onCloseSubject.asObservable(),
                close: vi.fn(),
            } as unknown as DynamicDialogRef;

            vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);

            lectureComponentFixture.detectChanges();
            await lectureComponentFixture.whenStable();
            const files = [new File(['content'], 'test.pdf', { type: 'application/pdf' })];

            lectureComponent.onPdfFilesDropped(files);
            onCloseSubject.next(dialogResult);
            await lectureComponentFixture.whenStable();

            // Error handling sets isUploadingPdfs to false
            expect(lectureComponent.isUploadingPdfs()).toBe(false);
        });

        it('should handle error when creating attachment unit fails', async () => {
            vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            const dialogResult: PdfUploadTarget = {
                targetType: 'existing',
                lectureId: 42,
            };

            const onCloseSubject = new Subject<PdfUploadTarget | undefined>();
            const mockDialogRef = {
                onClose: onCloseSubject.asObservable(),
                close: vi.fn(),
            } as unknown as DynamicDialogRef;

            vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);

            lectureComponentFixture.detectChanges();
            await lectureComponentFixture.whenStable();
            const files = [new File(['content'], 'test.pdf', { type: 'application/pdf' })];

            lectureComponent.onPdfFilesDropped(files);
            onCloseSubject.next(dialogResult);
            await lectureComponentFixture.whenStable();

            // Error handling sets isUploadingPdfs to false
            expect(lectureComponent.isUploadingPdfs()).toBe(false);
        });

        it('should do nothing when dialog is dismissed', async () => {
            const createSpy = vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile');

            const onCloseSubject = new Subject<PdfUploadTarget | undefined>();
            const mockDialogRef = {
                onClose: onCloseSubject.asObservable(),
                close: vi.fn(),
            } as unknown as DynamicDialogRef;

            vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);

            lectureComponentFixture.detectChanges();
            await lectureComponentFixture.whenStable();
            const files = [new File(['content'], 'test.pdf', { type: 'application/pdf' })];

            lectureComponent.onPdfFilesDropped(files);
            onCloseSubject.next(undefined);
            await lectureComponentFixture.whenStable();

            expect(createSpy).not.toHaveBeenCalled();
        });

        it('should call service with correct lecture id and file', async () => {
            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 1;

            const createSpy = vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));

            const dialogResult: PdfUploadTarget = {
                targetType: 'existing',
                lectureId: 42,
            };

            const onCloseSubject = new Subject<PdfUploadTarget | undefined>();
            const mockDialogRef = {
                onClose: onCloseSubject.asObservable(),
                close: vi.fn(),
            } as unknown as DynamicDialogRef;

            vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);

            lectureComponentFixture.detectChanges();
            await lectureComponentFixture.whenStable();
            const pdfFile = new File(['content'], 'Chapter_01_Introduction.pdf', { type: 'application/pdf' });

            lectureComponent.onPdfFilesDropped([pdfFile]);
            onCloseSubject.next(dialogResult);
            await lectureComponentFixture.whenStable();

            expect(createSpy).toHaveBeenCalledWith(42, pdfFile);
        });
    });
});
