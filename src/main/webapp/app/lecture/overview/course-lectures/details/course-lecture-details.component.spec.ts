import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { DebugElement, ElementRef, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockInstance, MockPipe, MockProvider } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/foundation/service/alert.service';
import { EMPTY, of, throwError } from 'rxjs';
import { CourseLectureDetailsComponent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';
import { AttachmentVideoUnitComponent } from 'app/lecture/overview/course-lectures/attachment-video-unit/attachment-video-unit.component';
import { ExerciseUnitComponent } from 'app/lecture/overview/course-lectures/exercise-unit/exercise-unit.component';
import { TextUnitComponent } from 'app/lecture/overview/course-lectures/text-unit/text-unit.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { SidePanelComponent } from 'app/shared-ui/side-panel/side-panel.component';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Course, CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HttpErrorResponse, HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { HtmlForMarkdownPipe } from 'app/foundation/pipes/html-for-markdown.pipe';
import { SubmissionResultStatusComponent } from 'app/course/overview/submission-result-status/submission-result-status.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/course/overview/exercise-details/student-actions/exercise-details-student-actions.component';
import { NotReleasedTagComponent } from 'app/shared-ui/components/not-released-tag/not-released-tag.component';
import { DifficultyBadgeComponent } from 'app/exercise/exercise-headers/difficulty-badge/difficulty-badge.component';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { CourseExerciseRowComponent } from 'app/course/overview/course-exercises/course-exercise-row/course-exercise-row.component';
import { MockFileService } from 'test/helpers/mocks/service/mock-file.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { ScienceService } from 'app/foundation/science/science.service';
import * as DownloadUtils from 'app/foundation/util/download.util';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { OnlineUnitComponent } from 'app/lecture/overview/course-lectures/online-unit/online-unit.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NgbCollapse, NgbPopover, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DiscussionSectionComponent } from 'app/communication/shared/discussion-section/discussion-section.component';
import { FileService } from 'app/foundation/service/file.service';
import { InformationBoxComponent } from 'app/shared-ui/information-box/information-box.component';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { MODULE_FEATURE_IRIS } from 'app/app.constants';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';

describe('CourseLectureDetailsComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseLectureDetailsComponent>;
    let courseLecturesDetailsComponent: CourseLectureDetailsComponent;
    let lecture: Lecture;
    let course: Course;
    let lectureUnit1: AttachmentVideoUnit;
    let lectureUnit2: AttachmentVideoUnit;
    let lectureUnit3: TextUnit;
    let debugElement: DebugElement;
    let lectureService: LectureService;

    MockInstance(DiscussionSectionComponent, 'content', signal(new ElementRef(document.createElement('div'))));
    MockInstance(DiscussionSectionComponent, 'messages', signal([new ElementRef(document.createElement('div'))]));
    // @ts-ignore
    MockInstance(DiscussionSectionComponent, 'postCreateEditModal', signal(new ElementRef(document.createElement('div'))));

    beforeEach(async () => {
        const releaseDate = dayjs('18-03-2020 13:30', 'DD-MM-YYYY HH:mm');
        const endDate = dayjs('18-03-2020 15:30', 'DD-MM-YYYY HH:mm');

        course = new Course();
        course.id = 456;
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;

        lecture = new Lecture();
        lecture.id = 1;
        lecture.startDate = releaseDate;
        lecture.endDate = endDate;
        lecture.description = 'Test description';
        lecture.title = 'Test lecture';
        lecture.course = course;

        lectureUnit1 = getAttachmentVideoUnit(lecture, 1, releaseDate);
        lectureUnit2 = getAttachmentVideoUnit(lecture, 2, releaseDate);

        lectureUnit3 = new TextUnit();
        lectureUnit3.id = 3;
        lectureUnit3.name = 'Unit 3';
        lectureUnit3.releaseDate = releaseDate;
        lectureUnit3.lecture = lecture;
        lectureUnit3.visibleToStudents = true;

        lecture.lectureUnits = [lectureUnit1, lectureUnit2, lectureUnit3];

        let headers = new HttpHeaders();
        headers = headers.set('Content-Type', 'application/json; charset=utf-8');
        const response = of(new HttpResponse({ body: lecture, headers, status: 200 }));

        await TestBed.configureTestingModule({
            imports: [
                MockDirective(NgbTooltip),
                MockDirective(NgbCollapse),
                MockDirective(NgbPopover),
                FaIconComponent,
                CourseLectureDetailsComponent,
                MockComponent(AttachmentVideoUnitComponent),
                MockComponent(ExerciseUnitComponent),
                MockComponent(TextUnitComponent),
                MockComponent(OnlineUnitComponent),
                NotReleasedTagComponent,
                DifficultyBadgeComponent,
                IncludedInScoreBadgeComponent,
                MockPipe(HtmlForMarkdownPipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(CourseExerciseRowComponent),
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(SidePanelComponent),
                MockDirective(TranslateDirective),
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(DiscussionSectionComponent),
                MockComponent(InformationBoxComponent),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(LectureService, {
                    find: () => {
                        return response;
                    },
                    findWithDetails: () => {
                        return response;
                    },
                }),
                MockProvider(LectureUnitService, {
                    setCompletion: (_lectureUnitId: number, _lectureId: number, _completed: boolean) => EMPTY,
                    completeLectureUnit: vi.fn(),
                }),
                MockProvider(AlertService),
                { provide: FileService, useClass: MockFileService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ lectureId: '1' }),
                        queryParams: of({}),
                        parent: {
                            parent: {
                                params: of({ courseId: '1' }),
                                queryParams: of({}),
                            },
                        },
                    },
                },
                MockProvider(Router),
                MockProvider(ScienceService),
                MockProvider(IrisSettingsService),
                { provide: MetisConversationService, useClass: MockMetisConversationService },
            ],
        }).compileComponents();

        lectureService = TestBed.inject(LectureService);
        vi.spyOn(lectureService, 'findWithDetails').mockReturnValue(response);
        vi.spyOn(lectureService, 'find').mockReturnValue(response);

        fixture = TestBed.createComponent(CourseLectureDetailsComponent);
        courseLecturesDetailsComponent = fixture.componentInstance;
        debugElement = fixture.debugElement;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.changeDetectorRef.detectChanges();
        expect(courseLecturesDetailsComponent).not.toBeNull();
        courseLecturesDetailsComponent.ngOnDestroy();
    });

    it('should render information boxes for lecture start/end date', async () => {
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        const boxes = debugElement.queryAll(By.css('jhi-information-box'));
        expect(boxes).toHaveLength(2);
    });

    it('should display all three lecture units: 2 attachment video units and 1 text unit', async () => {
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        const attachmentVideoUnits = debugElement.queryAll(By.css('jhi-attachment-video-unit'));
        const textUnits = debugElement.queryAll(By.css('jhi-text-unit'));
        expect(attachmentVideoUnits).toHaveLength(2);
        expect(textUnits).toHaveLength(1);
    });

    it('should display download PDF button', async () => {
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        const downloadButton = debugElement.query(By.css('#downloadButton'));
        expect(downloadButton).not.toBeNull();
        expect(courseLecturesDetailsComponent.hasPdfLectureUnit()).toBe(true);
    });

    it('should not display download PDF button', async () => {
        lecture.lectureUnits = [lectureUnit3];
        courseLecturesDetailsComponent.lecture.set(lecture);
        courseLecturesDetailsComponent.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        const downloadButton = debugElement.query(By.css('#downloadButton'));
        expect(downloadButton).toBeNull();
        expect(courseLecturesDetailsComponent.hasPdfLectureUnit()).toBe(false);
    });

    it('should check attachment release date', async () => {
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        const attachment = getAttachmentVideoUnit(lecture, 1, dayjs().add(1, 'day')).attachment!;

        expect(courseLecturesDetailsComponent.attachmentNotReleased(attachment)).toBe(true);

        attachment.releaseDate = dayjs().subtract(1, 'day');
        expect(courseLecturesDetailsComponent.attachmentNotReleased(attachment)).toBe(false);

        attachment.releaseDate = undefined;
        expect(courseLecturesDetailsComponent.attachmentNotReleased(attachment)).toBe(false);
    });

    it('should get the attachment extension', async () => {
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        const attachment = getAttachmentVideoUnit(lecture, 1, dayjs()).attachment!;

        expect(courseLecturesDetailsComponent.attachmentExtension(attachment)).toBe('pdf');

        attachment.link = '/path/to/file/test.test.docx';
        expect(courseLecturesDetailsComponent.attachmentExtension(attachment)).toBe('docx');

        attachment.link = undefined;
        expect(courseLecturesDetailsComponent.attachmentExtension(attachment)).toBe('N/A');
    });

    it('should show discussion section when communication is enabled', async () => {
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        const discussionSection = fixture.nativeElement.querySelector('jhi-discussion-section');
        expect(discussionSection).toBeTruthy();
    });

    it('should not show discussion section when communication is disabled', async () => {
        const lecture = {
            ...lectureUnit3.lecture,
            course: { courseInformationSharingConfiguration: CourseInformationSharingConfiguration.DISABLED },
        };
        const response = of(new HttpResponse({ body: { ...lecture }, status: 200 }));
        vi.spyOn(TestBed.inject(LectureService), 'findWithDetails').mockReturnValue(response);

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        const discussionSection = fixture.nativeElement.querySelector('jhi-discussion-section');
        expect(discussionSection).toBeFalsy();
    });

    it('should download file for attachment', async () => {
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        const fileService = TestBed.inject(FileService);
        const downloadFileSpy = vi.spyOn(fileService, 'downloadFileByAttachmentName');
        const attachment = getAttachmentVideoUnit(lecture, 1, dayjs()).attachment!;

        courseLecturesDetailsComponent.downloadAttachment(attachment.link, attachment.name);

        expect(downloadFileSpy).toHaveBeenCalledTimes(1);
        expect(downloadFileSpy).toHaveBeenCalledWith(attachment.link, attachment.name);
        expect(courseLecturesDetailsComponent.isDownloadingLink()).toBeUndefined();
    });

    it('should download PDF file', async () => {
        fixture.changeDetectorRef.detectChanges();

        const downloadAttachmentStub = vi.spyOn(courseLecturesDetailsComponent, 'downloadMergedFiles');
        const downloadStreamStub = vi.spyOn(DownloadUtils, 'downloadStream').mockImplementation(() => {});
        const downloadButton = debugElement.query(By.css('#downloadButton'));
        expect(downloadButton).not.toBeNull();

        downloadButton.nativeElement.click();
        await fixture.whenStable();
        expect(downloadAttachmentStub).toHaveBeenCalledTimes(1);
        expect(downloadStreamStub).toHaveBeenCalledWith(null, 'application/pdf', 'Test lecture');
    });

    it('should set lecture unit as completed', async () => {
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        const lectureUnitService = TestBed.inject(LectureUnitService);
        const completeSpy = vi.spyOn(lectureUnitService, 'completeLectureUnit');

        courseLecturesDetailsComponent.lecture.set(lecture);
        courseLecturesDetailsComponent.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        expect(lectureUnit3.completed).toBeFalsy();
        courseLecturesDetailsComponent.completeLectureUnit({ lectureUnit: lectureUnit3, completed: true });
        expect(completeSpy).toHaveBeenCalledWith(lecture, { lectureUnit: lectureUnit3, completed: true });
    });

    describe('ensureValidDeepLinkTargets', () => {
        it('should preserve timestamp for unit with only video', () => {
            const videoUnit = new AttachmentVideoUnit();
            videoUnit.id = 100;
            videoUnit.videoSource = 'https://example.com/video.mp4';
            videoUnit.lecture = lecture;

            courseLecturesDetailsComponent.lectureUnits.set([videoUnit]);
            courseLecturesDetailsComponent.targetUnitId.set(100);
            courseLecturesDetailsComponent.targetVideoTimestamp.set(45.5);

            courseLecturesDetailsComponent['ensureValidDeepLinkTargets']();

            expect(courseLecturesDetailsComponent.targetVideoTimestamp()).toBe(45.5);
        });

        it('should preserve page for unit with only PDF', () => {
            const pdfUnit = new AttachmentVideoUnit();
            pdfUnit.id = 101;
            pdfUnit.attachment = new Attachment();
            pdfUnit.attachment.link = '/path/to/slides.pdf';
            pdfUnit.lecture = lecture;

            courseLecturesDetailsComponent.lectureUnits.set([pdfUnit]);
            courseLecturesDetailsComponent.targetUnitId.set(101);
            courseLecturesDetailsComponent.targetPdfPage.set(5);

            courseLecturesDetailsComponent['ensureValidDeepLinkTargets']();

            expect(courseLecturesDetailsComponent.targetPdfPage()).toBe(5);
        });

        it('should preserve timestamp when unit has both video and PDF', () => {
            const unitWithBoth = new AttachmentVideoUnit();
            unitWithBoth.id = 102;
            unitWithBoth.videoSource = 'https://example.com/video.mp4';
            unitWithBoth.attachment = new Attachment();
            unitWithBoth.attachment.link = '/path/to/slides.pdf';
            unitWithBoth.lecture = lecture;

            courseLecturesDetailsComponent.lectureUnits.set([unitWithBoth]);
            courseLecturesDetailsComponent.targetUnitId.set(102);
            courseLecturesDetailsComponent.targetVideoTimestamp.set(45.5);

            courseLecturesDetailsComponent['ensureValidDeepLinkTargets']();

            expect(courseLecturesDetailsComponent.targetVideoTimestamp()).toBe(45.5);
        });

        it('should preserve timestamp for unit with only YouTube video', () => {
            const youtubeUnit = new AttachmentVideoUnit();
            youtubeUnit.id = 103;
            youtubeUnit.youtubeVideoId = 'dQw4w9WgXcQ';
            youtubeUnit.lecture = lecture;

            courseLecturesDetailsComponent.lectureUnits.set([youtubeUnit]);
            courseLecturesDetailsComponent.targetUnitId.set(103);
            courseLecturesDetailsComponent.targetVideoTimestamp.set(30);

            courseLecturesDetailsComponent['ensureValidDeepLinkTargets']();

            expect(courseLecturesDetailsComponent.targetVideoTimestamp()).toBe(30);
        });

        it('should preserve timestamp and page for unit with both YouTube video and PDF', () => {
            const youtubeUnitWithPdf = new AttachmentVideoUnit();
            youtubeUnitWithPdf.id = 104;
            youtubeUnitWithPdf.youtubeVideoId = 'dQw4w9WgXcQ';
            youtubeUnitWithPdf.attachment = new Attachment();
            youtubeUnitWithPdf.attachment.link = '/path/to/slides.pdf';
            youtubeUnitWithPdf.lecture = lecture;

            courseLecturesDetailsComponent.lectureUnits.set([youtubeUnitWithPdf]);
            courseLecturesDetailsComponent.targetUnitId.set(104);
            courseLecturesDetailsComponent.targetVideoTimestamp.set(60);
            courseLecturesDetailsComponent.targetPdfPage.set(7);

            courseLecturesDetailsComponent['ensureValidDeepLinkTargets']();

            expect(courseLecturesDetailsComponent.targetVideoTimestamp()).toBe(60);
            expect(courseLecturesDetailsComponent.targetPdfPage()).toBe(7);
        });

        it('should clear timestamp for unit with neither video source nor YouTube video ID', () => {
            const unitWithoutVideo = new AttachmentVideoUnit();
            unitWithoutVideo.id = 105;
            unitWithoutVideo.attachment = new Attachment();
            unitWithoutVideo.attachment.link = '/path/to/document.pdf';
            unitWithoutVideo.lecture = lecture;

            courseLecturesDetailsComponent.lectureUnits.set([unitWithoutVideo]);
            courseLecturesDetailsComponent.targetUnitId.set(105);
            courseLecturesDetailsComponent.targetVideoTimestamp.set(45);

            courseLecturesDetailsComponent['ensureValidDeepLinkTargets']();

            expect(courseLecturesDetailsComponent.targetVideoTimestamp()).toBeUndefined();
        });
    });

    describe('Context Collection', () => {
        it('collectVisibleContexts: returns empty array when no units', () => {
            fixture.changeDetectorRef.detectChanges();

            const contexts = courseLecturesDetailsComponent['collectVisibleContexts']();

            expect(contexts).toEqual([]);
        });

        it('isElementVisible: returns false for null element', () => {
            const result = courseLecturesDetailsComponent['isElementVisible'](null);

            expect(result).toBe(false);
        });

        it('isElementVisible: returns true when element is in viewport', () => {
            const mockElement = document.createElement('div');
            vi.spyOn(mockElement, 'getBoundingClientRect').mockReturnValue({
                top: 100,
                bottom: 200,
                left: 50,
                right: 300,
                width: 250,
                height: 100,
                x: 50,
                y: 100,
                toJSON: () => {},
            } as DOMRect);

            Object.defineProperty(window, 'innerHeight', { value: 768, configurable: true });
            Object.defineProperty(window, 'innerWidth', { value: 1024, configurable: true });

            const result = courseLecturesDetailsComponent['isElementVisible'](mockElement);

            expect(result).toBe(true);
        });

        it('isElementVisible: returns false when element is below viewport', () => {
            const mockElement = document.createElement('div');
            vi.spyOn(mockElement, 'getBoundingClientRect').mockReturnValue({
                top: 1000,
                bottom: 1200,
                left: 50,
                right: 300,
                width: 250,
                height: 200,
                x: 50,
                y: 1000,
                toJSON: () => {},
            } as DOMRect);

            Object.defineProperty(window, 'innerHeight', { value: 768, configurable: true });
            Object.defineProperty(window, 'innerWidth', { value: 1024, configurable: true });

            const result = courseLecturesDetailsComponent['isElementVisible'](mockElement);

            expect(result).toBe(false);
        });

        it('contextProvider: returns a function that calls collectVisibleContexts', () => {
            const collectSpy = vi.spyOn(courseLecturesDetailsComponent as any, 'collectVisibleContexts').mockReturnValue([]);

            const provider = courseLecturesDetailsComponent.contextProvider();
            expect(provider).toBeDefined();

            const contexts = provider!();

            expect(collectSpy).toHaveBeenCalledTimes(1);
            expect(contexts).toEqual([]);
        });
    });

    describe('loadData branches', () => {
        it('should prefix attachment links with the public file prefix', async () => {
            const attachment = new Attachment();
            attachment.id = 42;
            attachment.link = 'files/attachments/lecture/1/slides.pdf';
            const lectureWithAttachment = { ...lecture, attachments: [attachment], lectureUnits: [] };
            const responseWithAttachment = of(new HttpResponse({ body: lectureWithAttachment, status: 200 }));
            vi.spyOn(lectureService, 'findWithDetails').mockReturnValue(responseWithAttachment);

            courseLecturesDetailsComponent.ngOnInit();
            fixture.changeDetectorRef.detectChanges();
            await fixture.whenStable();

            expect(attachment.linkUrl).toBeDefined();
            expect(attachment.linkUrl).toContain(attachment.link!);
            expect(courseLecturesDetailsComponent.isLoading()).toBe(false);
        });

        it('should build information boxes only for the dates that are present', async () => {
            const lectureStartOnly = { ...lecture, startDate: dayjs(), endDate: undefined, attachments: [], lectureUnits: [] };
            const startOnlyResponse = of(new HttpResponse({ body: lectureStartOnly, status: 200 }));
            vi.spyOn(lectureService, 'findWithDetails').mockReturnValue(startOnlyResponse);

            courseLecturesDetailsComponent.ngOnInit();
            fixture.changeDetectorRef.detectChanges();
            await fixture.whenStable();

            const boxes = courseLecturesDetailsComponent.informationBoxData();
            expect(boxes).toHaveLength(1);
            expect(boxes[0].title).toBe('artemisApp.courseOverview.lectureDetails.startDate');
        });

        it('should load iris settings when iris is enabled', async () => {
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature: string) => feature === MODULE_FEATURE_IRIS);

            const irisSettingsService = TestBed.inject(IrisSettingsService);
            const irisSettings = { rateLimit: 100 } as any;
            const irisSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(irisSettings));

            courseLecturesDetailsComponent.ngOnInit();
            fixture.changeDetectorRef.detectChanges();
            await fixture.whenStable();

            expect(courseLecturesDetailsComponent.irisEnabled).toBe(true);
            expect(irisSpy).toHaveBeenCalledWith(course.id);
            expect(courseLecturesDetailsComponent.irisSettings()).toBe(irisSettings);
        });

        it('should report an error via the alert service when loading fails', async () => {
            const alertService = TestBed.inject(AlertService);
            const errorSpy = vi.spyOn(alertService, 'error');
            const errorResponse = throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not Found' }));
            vi.spyOn(lectureService, 'findWithDetails').mockReturnValue(errorResponse as any);

            courseLecturesDetailsComponent.ngOnInit();
            fixture.changeDetectorRef.detectChanges();
            await fixture.whenStable();

            expect(errorSpy).toHaveBeenCalledWith('error.http.404');
            expect(courseLecturesDetailsComponent.isLoading()).toBe(false);
        });
    });

    describe('deep-link query params', () => {
        // Set up a lecture whose single unit (id 7) has both a video and a PDF, so parsed deep-link
        // targets survive the ensureValidDeepLinkTargets validation that runs after loadData.
        const setupUnitWithBoth = () => {
            const unitWithBoth = new AttachmentVideoUnit();
            unitWithBoth.id = 7;
            unitWithBoth.videoSource = 'https://example.com/video.mp4';
            unitWithBoth.attachment = new Attachment();
            unitWithBoth.attachment.link = '/path/to/slides.pdf';
            unitWithBoth.lecture = lecture;
            const lectureWithUnit = { ...lecture, lectureUnits: [unitWithBoth], attachments: [] };
            vi.spyOn(lectureService, 'findWithDetails').mockReturnValue(of(new HttpResponse({ body: lectureWithUnit, status: 200 })));
        };

        const reInitWithQueryParams = (queryParams: Record<string, unknown>) => {
            const activatedRoute = TestBed.inject(ActivatedRoute);
            // The route is provided as a plain value object, so we can swap the observable before re-running ngOnInit.
            (activatedRoute as unknown as { queryParams: unknown }).queryParams = of(queryParams);
            courseLecturesDetailsComponent.ngOnInit();
        };

        it('should read unit, timestamp and page from the query params', () => {
            setupUnitWithBoth();
            reInitWithQueryParams({ unit: '7', timestamp: '30', page: '4' });

            expect(courseLecturesDetailsComponent.targetUnitId()).toBe(7);
            expect(courseLecturesDetailsComponent.targetVideoTimestamp()).toBe(30);
            expect(courseLecturesDetailsComponent.targetPdfPage()).toBe(4);
        });

        it('should ignore an invalid timestamp and page while keeping the unit', () => {
            setupUnitWithBoth();
            reInitWithQueryParams({ unit: '7', timestamp: '-5', page: '0' });

            expect(courseLecturesDetailsComponent.targetUnitId()).toBe(7);
            expect(courseLecturesDetailsComponent.targetVideoTimestamp()).toBeUndefined();
            expect(courseLecturesDetailsComponent.targetPdfPage()).toBeUndefined();
        });

        it('should clear all deep-link targets when the unit param is not a positive integer', () => {
            courseLecturesDetailsComponent.targetUnitId.set(99);
            courseLecturesDetailsComponent.targetVideoTimestamp.set(10);
            courseLecturesDetailsComponent.targetPdfPage.set(2);

            reInitWithQueryParams({ unit: 'not-a-number' });

            expect(courseLecturesDetailsComponent.targetUnitId()).toBeUndefined();
            expect(courseLecturesDetailsComponent.targetVideoTimestamp()).toBeUndefined();
            expect(courseLecturesDetailsComponent.targetPdfPage()).toBeUndefined();
        });

        it('should re-validate deep-link targets when units are already loaded before the query params emit', () => {
            const ensureSpy = vi.spyOn(courseLecturesDetailsComponent as any, 'ensureValidDeepLinkTargets');
            setupUnitWithBoth();

            reInitWithQueryParams({ unit: '7' });

            // Called once from loadData and again from the queryParams handler (units already loaded).
            expect(ensureSpy.mock.calls.length).toBeGreaterThanOrEqual(2);
            expect(courseLecturesDetailsComponent.targetUnitId()).toBe(7);
        });
    });

    describe('ensureValidDeepLinkTargets edge cases', () => {
        it('should do nothing when there is no target unit', () => {
            courseLecturesDetailsComponent.targetUnitId.set(undefined);
            courseLecturesDetailsComponent.targetVideoTimestamp.set(12);
            courseLecturesDetailsComponent.targetPdfPage.set(3);

            courseLecturesDetailsComponent['ensureValidDeepLinkTargets']();

            // Values remain untouched because the method returns early.
            expect(courseLecturesDetailsComponent.targetVideoTimestamp()).toBe(12);
            expect(courseLecturesDetailsComponent.targetPdfPage()).toBe(3);
        });

        it('should clear all targets when the target unit is not in the list', () => {
            courseLecturesDetailsComponent.lectureUnits.set([lectureUnit3]);
            courseLecturesDetailsComponent.targetUnitId.set(9999);
            courseLecturesDetailsComponent.targetVideoTimestamp.set(12);
            courseLecturesDetailsComponent.targetPdfPage.set(3);

            courseLecturesDetailsComponent['ensureValidDeepLinkTargets']();

            expect(courseLecturesDetailsComponent.targetUnitId()).toBeUndefined();
            expect(courseLecturesDetailsComponent.targetVideoTimestamp()).toBeUndefined();
            expect(courseLecturesDetailsComponent.targetPdfPage()).toBeUndefined();
        });

        it('should clear timestamp and page for a non attachment/video target unit', () => {
            const textUnit = new TextUnit();
            textUnit.id = 200;
            textUnit.lecture = lecture;
            expect(textUnit.type).toBe(LectureUnitType.TEXT);

            courseLecturesDetailsComponent.lectureUnits.set([textUnit]);
            courseLecturesDetailsComponent.targetUnitId.set(200);
            courseLecturesDetailsComponent.targetVideoTimestamp.set(12);
            courseLecturesDetailsComponent.targetPdfPage.set(3);

            courseLecturesDetailsComponent['ensureValidDeepLinkTargets']();

            expect(courseLecturesDetailsComponent.targetUnitId()).toBe(200);
            expect(courseLecturesDetailsComponent.targetVideoTimestamp()).toBeUndefined();
            expect(courseLecturesDetailsComponent.targetPdfPage()).toBeUndefined();
        });
    });

    describe('collectVisibleContexts deep paths', () => {
        // Helper to fake an AttachmentVideoUnitComponent as exposed by the viewChildren signal.
        const fakeUnitComponent = (config: {
            unitId?: number;
            isCollapsed?: boolean;
            provider?:
                | {
                      getCurrentPdfPage?: () => number | undefined;
                      getCurrentVideoTimestamp?: () => number | undefined;
                      hasVideoBeenPlayed?: () => boolean;
                  }
                | undefined;
        }) =>
            ({
                lectureUnit: () => (config.unitId != undefined ? ({ id: config.unitId } as any) : undefined),
                isCollapsed: () => config.isCollapsed ?? false,
                contextProvider: () => config.provider,
            }) as unknown as AttachmentVideoUnitComponent;

        // Overrides the private viewChildren signal with the provided fake unit components.
        const setUnits = (units: AttachmentVideoUnitComponent[]) => {
            (courseLecturesDetailsComponent as unknown as { attachmentVideoUnits: () => AttachmentVideoUnitComponent[] }).attachmentVideoUnits = () => units;
        };

        // Creates a visible DOM element with the given data-unit-id, optionally embedding a PDF/video child.
        const createdElements: HTMLElement[] = [];
        const visibleRect = { top: 10, bottom: 100, left: 10, right: 100, width: 90, height: 90, x: 10, y: 10, toJSON: () => {} } as DOMRect;
        const createUnitElement = (unitId: number, children: { pdf?: boolean; video?: boolean; youtube?: boolean } = {}) => {
            const element = document.createElement('div');
            element.setAttribute('data-unit-id', String(unitId));
            vi.spyOn(element, 'getBoundingClientRect').mockReturnValue(visibleRect);
            if (children.pdf) {
                const pdf = document.createElement('jhi-pdf-viewer');
                vi.spyOn(pdf, 'getBoundingClientRect').mockReturnValue(visibleRect);
                element.appendChild(pdf);
            }
            if (children.video) {
                const video = document.createElement('jhi-video-player');
                vi.spyOn(video, 'getBoundingClientRect').mockReturnValue(visibleRect);
                element.appendChild(video);
            }
            if (children.youtube) {
                const youtube = document.createElement('jhi-youtube-player');
                vi.spyOn(youtube, 'getBoundingClientRect').mockReturnValue(visibleRect);
                element.appendChild(youtube);
            }
            document.body.appendChild(element);
            createdElements.push(element);
            return element;
        };

        beforeEach(() => {
            Object.defineProperty(window, 'innerHeight', { value: 768, configurable: true });
            Object.defineProperty(window, 'innerWidth', { value: 1024, configurable: true });
        });

        afterEach(() => {
            createdElements.forEach((element) => element.remove());
            createdElements.length = 0;
        });

        it('should skip units without an id or that are collapsed', () => {
            setUnits([fakeUnitComponent({ unitId: undefined }), fakeUnitComponent({ unitId: 1, isCollapsed: true })]);

            expect(courseLecturesDetailsComponent['collectVisibleContexts']()).toEqual([]);
        });

        it('should skip a unit whose DOM element is missing', () => {
            setUnits([fakeUnitComponent({ unitId: 1, provider: { getCurrentPdfPage: () => 3 } })]);
            // No element with data-unit-id="1" exists in the DOM.

            expect(courseLecturesDetailsComponent['collectVisibleContexts']()).toEqual([]);
        });

        it('should skip a unit whose element is not visible', () => {
            const element = document.createElement('div');
            element.setAttribute('data-unit-id', '1');
            vi.spyOn(element, 'getBoundingClientRect').mockReturnValue({
                top: 2000,
                bottom: 2100,
                left: 10,
                right: 100,
                width: 90,
                height: 100,
                x: 10,
                y: 2000,
                toJSON: () => {},
            } as DOMRect);
            document.body.appendChild(element);
            createdElements.push(element);
            setUnits([fakeUnitComponent({ unitId: 1, provider: { getCurrentPdfPage: () => 3 } })]);

            expect(courseLecturesDetailsComponent['collectVisibleContexts']()).toEqual([]);
        });

        it('should skip a visible unit that has no context provider', () => {
            createUnitElement(1, { pdf: true });
            setUnits([fakeUnitComponent({ unitId: 1, provider: undefined })]);

            expect(courseLecturesDetailsComponent['collectVisibleContexts']()).toEqual([]);
        });

        it('should collect a slides context for a visible PDF viewer', () => {
            createUnitElement(1, { pdf: true });
            setUnits([fakeUnitComponent({ unitId: 1, provider: { getCurrentPdfPage: () => 5 } })]);

            expect(courseLecturesDetailsComponent['collectVisibleContexts']()).toEqual([{ type: 'slides', lectureUnitId: 1, page: 5 }]);
        });

        it('should not collect a slides context when the PDF page is null', () => {
            createUnitElement(1, { pdf: true });
            setUnits([fakeUnitComponent({ unitId: 1, provider: { getCurrentPdfPage: () => undefined } })]);

            expect(courseLecturesDetailsComponent['collectVisibleContexts']()).toEqual([]);
        });

        it('should not collect a slides context when the PDF viewer is not visible', () => {
            const element = createUnitElement(1, {});
            const pdf = document.createElement('jhi-pdf-viewer');
            vi.spyOn(pdf, 'getBoundingClientRect').mockReturnValue({
                top: 2000,
                bottom: 2100,
                left: 10,
                right: 100,
                width: 90,
                height: 100,
                x: 10,
                y: 2000,
                toJSON: () => {},
            } as DOMRect);
            element.appendChild(pdf);
            setUnits([fakeUnitComponent({ unitId: 1, provider: { getCurrentPdfPage: () => 5 } })]);

            expect(courseLecturesDetailsComponent['collectVisibleContexts']()).toEqual([]);
        });

        it('should collect a video context for a visible, played video', () => {
            createUnitElement(1, { video: true });
            setUnits([fakeUnitComponent({ unitId: 1, provider: { hasVideoBeenPlayed: () => true, getCurrentVideoTimestamp: () => 42 } })]);

            expect(courseLecturesDetailsComponent['collectVisibleContexts']()).toEqual([{ type: 'video', lectureUnitId: 1, timestamp: 42 }]);
        });

        it('should not collect a video context when the video has not been played', () => {
            createUnitElement(1, { youtube: true });
            setUnits([fakeUnitComponent({ unitId: 1, provider: { hasVideoBeenPlayed: () => false, getCurrentVideoTimestamp: () => 42 } })]);

            expect(courseLecturesDetailsComponent['collectVisibleContexts']()).toEqual([]);
        });

        it('should not collect a video context when the timestamp is null', () => {
            createUnitElement(1, { video: true });
            setUnits([fakeUnitComponent({ unitId: 1, provider: { hasVideoBeenPlayed: () => true, getCurrentVideoTimestamp: () => undefined } })]);

            expect(courseLecturesDetailsComponent['collectVisibleContexts']()).toEqual([]);
        });

        it('should collect both slides and video contexts for a unit exposing both', () => {
            createUnitElement(1, { pdf: true, video: true });
            setUnits([fakeUnitComponent({ unitId: 1, provider: { getCurrentPdfPage: () => 2, hasVideoBeenPlayed: () => true, getCurrentVideoTimestamp: () => 15 } })]);

            expect(courseLecturesDetailsComponent['collectVisibleContexts']()).toEqual([
                { type: 'slides', lectureUnitId: 1, page: 2 },
                { type: 'video', lectureUnitId: 1, timestamp: 15 },
            ]);
        });
    });

    it('contextsProvider.getVisibleContexts should delegate to collectVisibleContexts', () => {
        const collectSpy = vi.spyOn(courseLecturesDetailsComponent as any, 'collectVisibleContexts').mockReturnValue([{ type: 'slides', lectureUnitId: 1, page: 1 }]);

        const result = courseLecturesDetailsComponent.contextsProvider.getVisibleContexts();

        expect(collectSpy).toHaveBeenCalledTimes(1);
        expect(result).toEqual([{ type: 'slides', lectureUnitId: 1, page: 1 }]);
    });
});

const getAttachmentVideoUnit = (lecture: Lecture, id: number, releaseDate: dayjs.Dayjs) => {
    const attachment = new Attachment();
    attachment.id = id;
    attachment.version = 1;
    attachment.attachmentType = AttachmentType.FILE;
    attachment.releaseDate = releaseDate;
    attachment.uploadDate = dayjs().year(2020).month(3).date(5);
    attachment.name = 'test';
    attachment.link = '/path/to/file/test.pdf';

    const attachmentVideoUnit = new AttachmentVideoUnit();
    attachmentVideoUnit.id = id;
    attachmentVideoUnit.name = 'Unit 1';
    attachmentVideoUnit.releaseDate = attachment.releaseDate;
    attachmentVideoUnit.lecture = lecture;
    attachmentVideoUnit.attachment = attachment;
    attachment.attachmentVideoUnit = attachmentVideoUnit;
    return attachmentVideoUnit;
};
