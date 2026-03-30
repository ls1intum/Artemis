import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

// Mock pdfjs-dist BEFORE importing the component
vi.mock('pdfjs-dist/legacy/build/pdf.mjs', () => {
    return {
        __esModule: true,
        GlobalWorkerOptions: {
            workerSrc: '',
        },
        getDocument: vi.fn(() => ({ promise: Promise.resolve({ numPages: 0, getPage: vi.fn(), destroy: vi.fn() }) })),
    };
});

import { AttachmentVideoUnitComponent } from 'app/lecture/overview/course-lectures/attachment-video-unit/attachment-video-unit.component';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockProvider } from 'ng-mocks';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ScienceService } from 'app/shared/science/science.service';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { LectureTranscriptionDTO } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { of } from 'rxjs';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/shared/service/alert.service';
import {
    IconDefinition,
    faFile,
    faFileArchive,
    faFileCode,
    faFileCsv,
    faFileExcel,
    faFileImage,
    faFileLines,
    faFilePdf,
    faFilePen,
    faFilePowerpoint,
    faFileWord,
} from '@fortawesome/free-solid-svg-icons';
import { MockFileService } from 'test/helpers/mocks/service/mock-file.service';
import { FileService } from 'app/shared/service/file.service';
import urlParser from 'js-video-url-parser';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';

// Mock ResizeObserver for VideoPlayerComponent
class MockResizeObserver {
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
}
global.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver;

describe('AttachmentVideoUnitComponent', () => {
    setupTestBed({ zoneless: true });

    let scienceService: ScienceService;
    let fileService: FileService;
    let httpMock: HttpTestingController;
    let lectureTranscriptionService: LectureTranscriptionService;

    let component: AttachmentVideoUnitComponent;
    let fixture: ComponentFixture<AttachmentVideoUnitComponent>;

    const attachmentVideoUnit: AttachmentVideoUnit = {
        id: 1,
        description: 'lorem ipsum',
        attachment: {
            id: 1,
            version: 1,
            attachmentType: AttachmentType.FILE,
            name: 'test',
            link: '/path/to/file/test.pdf',
        },
    };

    let mockLectureTranscriptionService: any;

    function expectPlaylistRequest(url: string, response: string | null) {
        const req = httpMock.expectOne((request) => request.url === '/api/nebula/video-utils/tum-live-playlist' && request.params.get('url') === url);
        expect(req.request.method).toBe('GET');
        req.flush(response);
    }

    function mockTranscriptResponse(segments: any[]) {
        vi.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(
            of({
                lectureUnitId: 1,
                language: 'en',
                segments,
            }),
        );
    }

    beforeEach(async () => {
        mockLectureTranscriptionService = {
            getTranscription: vi.fn(),
            getTranscriptionStatus: vi.fn(() => of(undefined)),
        };

        await TestBed.configureTestingModule({
            imports: [AttachmentVideoUnitComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: FileService, useClass: MockFileService },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(ScienceService),
                { provide: LectureTranscriptionService, useValue: mockLectureTranscriptionService },
                AttachmentVideoUnitService,
                MockProvider(NgbModal),
                MockProvider(AlertService),
            ],
        }).compileComponents();

        scienceService = TestBed.inject(ScienceService);
        fileService = TestBed.inject(FileService);
        httpMock = TestBed.inject(HttpTestingController);
        lectureTranscriptionService = TestBed.inject(LectureTranscriptionService);

        fixture = TestBed.createComponent(AttachmentVideoUnitComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('lectureUnit', { ...attachmentVideoUnit });
        fixture.componentRef.setInput('courseId', 1);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should get file name', () => {
        fixture.detectChanges();
        expect(component.getFileName()).toBe('test.pdf');
    });

    it('should handle download', () => {
        const createStudentLinkSpy = vi.spyOn(fileService, 'createStudentLink');
        const downloadFileSpy = vi.spyOn(fileService, 'downloadFileByAttachmentName');
        const onCompletionEmitSpy = vi.spyOn(component.onCompletion, 'emit');

        component.handleDownload();

        expect(createStudentLinkSpy).toHaveBeenCalledTimes(1);
        expect(downloadFileSpy).toHaveBeenCalledTimes(1);
        expect(onCompletionEmitSpy).toHaveBeenCalledTimes(1);
    });

    it('should handle original version', () => {
        const downloadFileSpy = vi.spyOn(fileService, 'downloadFileByAttachmentName');
        const onCompletionEmitSpy = vi.spyOn(component.onCompletion, 'emit');

        component.handleOriginalVersion();

        expect(downloadFileSpy).toHaveBeenCalledTimes(1);
        expect(onCompletionEmitSpy).toHaveBeenCalledTimes(1);
    });

    it.each([
        ['pdf', faFilePdf],
        ['csv', faFileCsv],
        ['png', faFileImage],
        ['zip', faFileArchive],
        ['txt', faFileLines],
        ['doc', faFileWord],
        ['json', faFileCode],
        ['xls', faFileExcel],
        ['ppt', faFilePowerpoint],
        ['odf', faFilePen],
        ['exotic', faFile],
    ])('should use correct icon for extension %s', async (extension: string, icon: IconDefinition) => {
        const getAttachmentIconSpy = vi.spyOn(component, 'getAttachmentIcon');
        component.lectureUnit().attachment!.link = `/path/to/file/test.${extension}`;
        fixture.detectChanges();

        expect(getAttachmentIconSpy).toHaveReturnedWith(icon);
    });

    it('should download attachment when clicked', () => {
        const downloadFileSpy = vi.spyOn(fileService, 'downloadFileByAttachmentName');

        fixture.detectChanges();

        const viewIsolatedButton = fixture.debugElement.query(By.css('#view-isolated-button'));
        viewIsolatedButton.nativeElement.click();

        fixture.detectChanges();
        expect(downloadFileSpy).toHaveBeenCalledTimes(1);
    });

    it('should call completion callback when downloaded', () => {
        const scienceLogSpy = vi.spyOn(scienceService, 'logEvent');
        component.handleDownload();

        expect(scienceLogSpy).toHaveBeenCalledTimes(1);
    });

    it('should toggle completion', () => {
        const onCompletionEmitSpy = vi.spyOn(component.onCompletion, 'emit');

        component.handleDownload();

        expect(onCompletionEmitSpy).toHaveBeenCalledTimes(1);
    });

    it('videoUrl: handles allow-listed TUM Live URLs', () => {
        const src = 'https://live.rbg.tum.de/w/abcd/1234?video_only=1';
        component.lectureUnit().videoSource = src;
        fixture.detectChanges();
        expect(component.videoUrl()).toBe(src);
    });
    it('toggleCollapse(false): resets state, resolves playlist, fetches transcript', async () => {
        const src = 'https://live.rbg.tum.de/w/abcd/1234?video_only=1';
        const playlist = 'https://cdn.tum/live/abcd/1234/playlist.m3u8';
        component.lectureUnit().videoSource = src;

        mockTranscriptResponse([{ startTime: 0, endTime: 2, text: 'Hello world', slideNumber: 3 }]);

        component.transcriptSegments.set([{ startTime: 0, endTime: 1, text: 'old', slideNumber: 1 }]);
        component.playlistUrl.set('stale.m3u8');
        fixture.detectChanges();

        expect(component.isLoading()).toBe(false);
        component.toggleCollapse(false);

        expect(component.transcriptSegments()).toEqual([]);
        expect(component.playlistUrl()).toBeUndefined();
        expect(component.isLoading()).toBe(true);

        expectPlaylistRequest(src, playlist);
        await fixture.whenStable();

        expect(component.playlistUrl()).toBe(playlist);
        expect(component.transcriptSegments()).toHaveLength(1);
        expect(component.hasTranscript()).toBe(true);
        expect(component.isLoading()).toBe(false);
    });

    it('toggleCollapse(false): handles playlist errors and null responses', async () => {
        fixture.detectChanges();

        // Test playlist error
        component.lectureUnit().videoSource = 'https://live.rbg.tum.de/w/efgh/9999?video_only=1';
        const getTranscriptionSpy = vi.spyOn(lectureTranscriptionService, 'getTranscription');

        component.toggleCollapse(false);
        expect(component.isLoading()).toBe(true);

        const req = httpMock.expectOne((request) => request.url === '/api/nebula/video-utils/tum-live-playlist');
        req.flush('Not found', { status: 404, statusText: 'Not Found' });
        await fixture.whenStable();

        expect(getTranscriptionSpy).not.toHaveBeenCalled();
        expect(component.playlistUrl()).toBeUndefined();
        expect(component.hasTranscript()).toBe(false);
        expect(component.isLoading()).toBe(false);

        // Test null playlist response
        component.lectureUnit().videoSource = 'https://example.com/some-video';
        component.toggleCollapse(false);

        expectPlaylistRequest('https://example.com/some-video', null);
        await fixture.whenStable();

        expect(getTranscriptionSpy).not.toHaveBeenCalled();
        expect(component.playlistUrl()).toBeUndefined();
    });

    it.each([
        ['.pdf', faFilePdf, true],
        ['.PDF', faFilePdf, true],
        ['.docx', faFileWord, false],
    ])('hasPdf: detects PDF extension %s correctly', (extension: string, _icon: IconDefinition, isPdf: boolean) => {
        component.lectureUnit().attachment!.link = `/path/to/file/document${extension}`;
        fixture.detectChanges();

        expect(component.hasPdf()).toBe(isPdf);
    });

    describe('PDF functionality', () => {
        it('loadPdf: loads directly via URL, then falls back to blob on error', async () => {
            const testBlob = new Blob(['fake pdf content'], { type: 'application/pdf' });
            const mockBlobUrl = 'blob:http://localhost/fallback-pdf';
            const createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue(mockBlobUrl);

            component.lectureUnit().attachment!.link = '/path/to/file/test.pdf';
            fixture.detectChanges();

            expect(component.isPdfLoading()).toBe(false);

            // Trigger toggleCollapse to load PDF
            component.toggleCollapse(false);
            await fixture.whenStable();

            // PDF is loaded directly via URL, no HTTP request for blob
            expect(component.isPdfLoading()).toBe(true);
            expect(component.pdfUrl()).toBe('api/core/files//path/to/file/test.pdf');

            // Simulate PDF load error to trigger blob fallback
            component['onPdfLoadError']({ pdfUrl: 'api/core/files//path/to/file/test.pdf' });

            // Blob fallback should trigger an HTTP request
            const req = httpMock.expectOne((request) => request.url.includes('test.pdf') && request.responseType === 'blob');
            expect(req.request.method).toBe('GET');
            req.flush(testBlob);

            await fixture.whenStable();

            expect(component.isPdfLoading()).toBe(true);
            expect(component.pdfUrl()).toBe(mockBlobUrl);
            expect(createObjectURLSpy).toHaveBeenCalledWith(testBlob);

            // Complete loading
            component['onPdfPagesLoaded']({ pdfUrl: mockBlobUrl });
            expect(component.isPdfLoading()).toBe(false);

            // Test duplicate error prevention
            component['onPdfLoadError']({ pdfUrl: mockBlobUrl });
            const duplicateRequests = httpMock.match((request) => request.url.includes('test.pdf') && request.responseType === 'blob');
            expect(duplicateRequests).toHaveLength(0); // No new requests

            createObjectURLSpy.mockRestore();
        });

        it('toggleCollapse: resets pdfUrl when collapsed', async () => {
            component.pdfUrl.set('blob:http://localhost/old-pdf');
            component.lectureUnit().attachment!.link = '/path/to/file/test.pdf';
            fixture.detectChanges();

            component.toggleCollapse(true);

            expect(component.pdfUrl()).toBeUndefined();
        });

        it('toggleCollapse: loads both video and PDF when both present', async () => {
            const src = 'https://live.rbg.tum.de/w/abcd/1234?video_only=1';
            const playlist = 'https://cdn.tum/live/abcd/1234/playlist.m3u8';

            component.lectureUnit().videoSource = src;
            component.lectureUnit().attachment!.link = '/path/to/file/test.pdf';

            vi.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(undefined));

            fixture.detectChanges();

            component.toggleCollapse(false);

            // Mock video playlist request
            const videoReq = httpMock.expectOne((request) => request.url === '/api/nebula/video-utils/tum-live-playlist');
            videoReq.flush(playlist);

            await fixture.whenStable();

            expect(component.playlistUrl()).toBe(playlist);
            // PDF is now loaded directly via URL (no blob)
            expect(component.pdfUrl()).toBe('api/core/files//path/to/file/test.pdf');
        });

        it('ngOnDestroy: cleanup', async () => {
            const mockUrl = 'blob:http://localhost/test-pdf';
            component.pdfUrl.set(mockUrl);
            component.lectureUnit().attachment!.link = '/path/to/file/test.pdf';

            // Clean up any pending requests first
            httpMock
                .match(() => true)
                .forEach((req) => {
                    req.flush(new Blob());
                });

            expect(() => component.ngOnDestroy()).not.toThrow();
        });
    });
});
