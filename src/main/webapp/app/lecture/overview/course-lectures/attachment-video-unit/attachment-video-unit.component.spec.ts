import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

// Mock pdfjs-dist BEFORE importing the component
vi.mock('pdfjs-dist', () => {
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
        const getFileNameSpy = vi.spyOn(component, 'getFileName');
        fixture.detectChanges();
        expect(getFileNameSpy).toHaveReturnedWith('test.pdf');
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

    it('videoUrl: returns source for allow-listed TUM Live URL', () => {
        const src = 'https://live.rbg.tum.de/w/abcd/1234?video_only=1';
        component.lectureUnit().videoSource = src;
        fixture.detectChanges();

        expect(component.videoUrl()).toBe(src);
    });

    it('videoUrl: returns source when parser recognizes non-allowlisted URL', () => {
        const src = 'https://example.com/some-video';
        // @ts-ignore - default export object has parse()
        const parseSpy = vi.spyOn(urlParser, 'parse').mockReturnValue({} as any);

        component.lectureUnit().videoSource = src;
        fixture.detectChanges();

        expect(component.videoUrl()).toBe(src);
        parseSpy.mockRestore();
    });

    it('videoUrl: returns undefined when parser returns undefined and URL is not in allow list', () => {
        const src = 'https://example.com/not-a-video';
        // @ts-ignore - default export object has parse()
        const parseSpy = vi.spyOn(urlParser, 'parse').mockReturnValue(undefined as any);

        component.lectureUnit().videoSource = src;
        fixture.detectChanges();

        // The URL is not in allow list and parser doesn't recognize it, so it should return undefined
        expect(component.videoUrl()).toBeUndefined();
        parseSpy.mockRestore();
    });
    it('toggleCollapse(false): resets state, resolves playlist, fetches transcript (happy path)', async () => {
        // Arrange BEFORE first detectChanges so the computed() caches the right value
        const src = 'https://live.rbg.tum.de/w/abcd/1234?video_only=1';
        const playlist = 'https://cdn.tum/live/abcd/1234/playlist.m3u8';
        component.lectureUnit().videoSource = src;

        const mockTranscriptDTO: LectureTranscriptionDTO = {
            lectureUnitId: 1,
            language: 'en',
            segments: [{ startTime: 0, endTime: 2, text: 'Hello world', slideNumber: 3 }],
        };
        vi.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(mockTranscriptDTO));

        component.transcriptSegments.set([{ startTime: 0, endTime: 1, text: 'old', slideNumber: 1 }]);
        component.playlistUrl.set('stale.m3u8');

        fixture.detectChanges();

        // Initial state: isLoading should be false
        expect(component.isLoading()).toBe(false);

        // Act
        component.toggleCollapse(false);

        // state reset happens synchronously
        expect(component.transcriptSegments()).toEqual([]);
        expect(component.playlistUrl()).toBeUndefined();
        // isLoading should be true immediately after toggleCollapse
        expect(component.isLoading()).toBe(true);

        // Mock the HTTP request for getPlaylistUrl
        const req = httpMock.expectOne((request) => request.url === '/api/nebula/video-utils/tum-live-playlist' && request.params.get('url') === src);
        expect(req.request.method).toBe('GET');
        req.flush(playlist);

        // Let the Observable chain finish
        await fixture.whenStable();

        expect(component.playlistUrl()).toBe(playlist);
        expect(component.transcriptSegments()).toHaveLength(1);
        expect(component.hasTranscript()).toBe(true);
        // isLoading should be false after request completes
        expect(component.isLoading()).toBe(false);
    });

    it('fetchTranscript: handles server error and keeps segments empty', async () => {
        fixture.detectChanges();

        // Mock service to return undefined (simulating error)
        vi.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(undefined));

        // Call the private method directly to isolate error handling
        (component as any).fetchTranscript();

        // Let the Promise chain settle
        await fixture.whenStable();

        // Component state remains empty
        expect(component.transcriptSegments()).toEqual([]);
        expect(component.hasTranscript()).toBe(false);
    });

    it('toggleCollapse(false): playlist resolve fails -> no transcript fetch', async () => {
        fixture.detectChanges();

        component.lectureUnit().videoSource = 'https://live.rbg.tum.de/w/efgh/9999?video_only=1';

        const getTranscriptionSpy = vi.spyOn(lectureTranscriptionService, 'getTranscription');

        // Initial state: isLoading should be false
        expect(component.isLoading()).toBe(false);

        component.toggleCollapse(false);

        // isLoading should be true immediately after toggleCollapse
        expect(component.isLoading()).toBe(true);

        // Mock the HTTP request to return an error
        const req = httpMock.expectOne((request) => request.url === '/api/nebula/video-utils/tum-live-playlist');
        req.flush('Not found', { status: 404, statusText: 'Not Found' });

        // Let the Observable chain finish
        await fixture.whenStable();

        // Ensure no transcript service call was made
        expect(getTranscriptionSpy).not.toHaveBeenCalled();

        expect(component.playlistUrl()).toBeUndefined();
        expect(component.hasTranscript()).toBe(false);
        // isLoading should be false after error
        expect(component.isLoading()).toBe(false);
    });

    it('toggleCollapse(false): .m3u8 URL is resolved through API like any other URL', async () => {
        const m3u8Url = 'https://live.rbg.tum.de/some/path/playlist.m3u8';
        component.lectureUnit().videoSource = m3u8Url;

        const mockTranscriptDTO: LectureTranscriptionDTO = {
            lectureUnitId: 1,
            language: 'en',
            segments: [{ startTime: 0, endTime: 5, text: 'Direct HLS transcript', slideNumber: 1 }],
        };
        vi.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(mockTranscriptDTO));

        fixture.detectChanges();

        // Initial state: isLoading should be false
        expect(component.isLoading()).toBe(false);

        // Act
        component.toggleCollapse(false);

        // isLoading should be true immediately after toggleCollapse
        expect(component.isLoading()).toBe(true);

        // Mock the HTTP request (even .m3u8 URLs go through the API)
        const req = httpMock.expectOne((request) => request.url === '/api/nebula/video-utils/tum-live-playlist' && request.params.get('url') === m3u8Url);
        expect(req.request.method).toBe('GET');
        req.flush(m3u8Url);

        // Let any pending microtasks finish
        await fixture.whenStable();

        expect(component.playlistUrl()).toBe(m3u8Url);
        expect(component.transcriptSegments()).toHaveLength(1);
        expect(component.hasTranscript()).toBe(true);
        expect(component.transcriptSegments()[0].text).toBe('Direct HLS transcript');
        // isLoading should be false after request completes
        expect(component.isLoading()).toBe(false);
    });

    it('toggleCollapse(false): non-TUM Live URL does not trigger transcript fetch when resolver returns null', async () => {
        const nonTumLiveUrl = 'https://example.com/some-video';
        component.lectureUnit().videoSource = nonTumLiveUrl;

        const getTranscriptionSpy = vi.spyOn(lectureTranscriptionService, 'getTranscription');

        fixture.detectChanges();

        // Initial state: isLoading should be false
        expect(component.isLoading()).toBe(false);

        // Act
        component.toggleCollapse(false);

        // isLoading should be true immediately after toggleCollapse
        expect(component.isLoading()).toBe(true);

        // Mock the HTTP request to return null (no playlist found)
        const req = httpMock.expectOne((request) => request.url === '/api/nebula/video-utils/tum-live-playlist' && request.params.get('url') === nonTumLiveUrl);
        req.flush(null);

        // Let any pending microtasks finish
        await fixture.whenStable();

        // No transcript fetch should occur since no playlist was found
        expect(getTranscriptionSpy).not.toHaveBeenCalled();

        // Playlist should remain undefined
        expect(component.playlistUrl()).toBeUndefined();
        expect(component.hasTranscript()).toBe(false);
        // isLoading should be false after request completes (even if no playlist found)
        expect(component.isLoading()).toBe(false);
    });

    it('toggleCollapse(false): sets isLoading to false immediately when no video source', () => {
        component.lectureUnit().videoSource = undefined;
        fixture.detectChanges();

        // Initial state: isLoading should be false
        expect(component.isLoading()).toBe(false);

        // Act
        component.toggleCollapse(false);

        // isLoading should be set to false immediately when no video source
        expect(component.isLoading()).toBe(false);
    });

    it('hasAttachment / hasVideo and getFileName() when no attachment', () => {
        // initial has attachment
        expect(component.hasAttachment()).toBe(true);

        // no video by default
        expect(component.hasVideo()).toBe(false);

        // remove attachment => name becomes empty
        const lu = component.lectureUnit();
        lu.attachment = undefined;
        fixture.detectChanges();

        expect(component.hasAttachment()).toBe(false);
        expect(component.getFileName()).toBe('');
    });

    describe('PDF functionality', () => {
        it('isPdf: returns true for PDF file extension', () => {
            component.lectureUnit().attachment!.link = '/path/to/file/document.pdf';
            fixture.detectChanges();

            expect(component.isPdf()).toBe(true);
        });

        it('isPdf: returns false for non-PDF file extensions', () => {
            component.lectureUnit().attachment!.link = '/path/to/file/document.docx';
            fixture.detectChanges();

            expect(component.isPdf()).toBe(false);
        });

        it('isPdf: handles uppercase PDF extension', () => {
            component.lectureUnit().attachment!.link = '/path/to/file/document.PDF';
            fixture.detectChanges();

            expect(component.isPdf()).toBe(true);
        });

        it('hasPdf: returns true when has attachment and is PDF', () => {
            component.lectureUnit().attachment!.link = '/path/to/file/test.pdf';
            fixture.detectChanges();

            expect(component.hasPdf()).toBe(true);
        });

        it('hasPdf: returns false when no attachment', () => {
            component.lectureUnit().attachment = undefined;
            fixture.detectChanges();

            expect(component.hasPdf()).toBe(false);
        });

        it('hasPdf: returns false when attachment is not PDF', () => {
            component.lectureUnit().attachment!.link = '/path/to/file/test.docx';
            fixture.detectChanges();

            expect(component.hasPdf()).toBe(false);
        });

        it('loadPdf: loads PDF as blob and creates object URL', async () => {
            const testBlob = new Blob(['fake pdf content'], { type: 'application/pdf' });
            const mockUrl = 'blob:http://localhost/test-pdf';
            const createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue(mockUrl);

            component.lectureUnit().attachment!.link = '/path/to/file/test.pdf';
            fixture.detectChanges();

            expect(component.isPdfLoading()).toBe(false);

            // Trigger toggleCollapse to load PDF
            component.toggleCollapse(false);

            expect(component.isPdfLoading()).toBe(true);

            // Mock the HTTP request for PDF file
            const req = httpMock.expectOne((request) => request.url.includes('test.pdf') && request.responseType === 'blob');
            expect(req.request.method).toBe('GET');
            req.flush(testBlob);

            await fixture.whenStable();

            expect(component.isPdfLoading()).toBe(false);
            expect(component.pdfUrl()).toBe(mockUrl);
            expect(createObjectURLSpy).toHaveBeenCalledWith(testBlob);

            createObjectURLSpy.mockRestore();
        });

        it('loadPdf: handles error gracefully', async () => {
            component.lectureUnit().attachment!.link = '/path/to/file/test.pdf';
            fixture.detectChanges();

            component.toggleCollapse(false);

            expect(component.isPdfLoading()).toBe(true);

            // Mock the HTTP request to return an error with proper blob error response
            const req = httpMock.expectOne((request) => request.url.includes('test.pdf') && request.responseType === 'blob');
            req.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });

            await fixture.whenStable();

            expect(component.isPdfLoading()).toBe(false);
            expect(component.pdfUrl()).toBeUndefined();
        });

        it('toggleCollapse: resets pdfUrl when collapsed', async () => {
            component.pdfUrl.set('blob:http://localhost/old-pdf');
            component.lectureUnit().attachment!.link = '/path/to/file/test.pdf';
            fixture.detectChanges();

            component.toggleCollapse(false);

            expect(component.pdfUrl()).toBeUndefined();

            // Handle the PDF request that gets triggered
            const req = httpMock.expectOne((request) => request.url.includes('test.pdf') && request.responseType === 'blob');
            req.flush(new Blob());

            await fixture.whenStable();
        });

        it('toggleCollapse: loads both video and PDF when both present', async () => {
            const src = 'https://live.rbg.tum.de/w/abcd/1234?video_only=1';
            const playlist = 'https://cdn.tum/live/abcd/1234/playlist.m3u8';
            const testBlob = new Blob(['fake pdf content'], { type: 'application/pdf' });
            const mockUrl = 'blob:http://localhost/test-pdf';

            component.lectureUnit().videoSource = src;
            component.lectureUnit().attachment!.link = '/path/to/file/test.pdf';

            vi.spyOn(URL, 'createObjectURL').mockReturnValue(mockUrl);
            vi.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(undefined));

            fixture.detectChanges();

            component.toggleCollapse(false);

            // Mock video playlist request
            const videoReq = httpMock.expectOne((request) => request.url === '/api/nebula/video-utils/tum-live-playlist');
            videoReq.flush(playlist);

            // Mock PDF request
            const pdfReq = httpMock.expectOne((request) => request.url.includes('test.pdf') && request.responseType === 'blob');
            pdfReq.flush(testBlob);

            await fixture.whenStable();

            expect(component.playlistUrl()).toBe(playlist);
            expect(component.pdfUrl()).toBe(mockUrl);
        });

        it('ngOnDestroy: cleanup', async () => {
            const mockUrl = 'blob:http://localhost/test-pdf';
            component.pdfUrl.set(mockUrl);
            component.lectureUnit().attachment!.link = '/path/to/file/test.pdf';

            // Clean up any pending requests first
            httpMock.match((req) => true).forEach((req) => req.flush(new Blob()));

            expect(() => component.ngOnDestroy()).not.toThrow();
        });
    });
});
