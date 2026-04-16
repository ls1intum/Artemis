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
        const req = httpMock.expectOne((request) => request.url === '/api/tumlive/playlist' && request.params.get('url') === url);
        expect(req.request.method).toBe('GET');
        req.flush(response);
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

    it('toggleCollapse(false): resets state, resolves playlist, fetches transcript', async () => {
        const src = 'https://live.rbg.tum.de/w/abcd/1234?video_only=1';
        const playlist = 'https://cdn.tum/live/abcd/1234/playlist.m3u8';
        component.lectureUnit().videoSource = src;

        const mockTranscriptDTO: LectureTranscriptionDTO = {
            lectureUnitId: 1,
            language: 'en',
            segments: [{ startTime: 0, endTime: 2, text: 'Hello world', slideNumber: 3 }],
        };
        vi.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(mockTranscriptDTO));

        component['_transcriptSegments'].set([{ startTime: 0, endTime: 1, text: 'old', slideNumber: 1 }]);
        component['_playlistUrl'].set('stale.m3u8');
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

        const req = httpMock.expectOne((request) => request.url === '/api/tumlive/playlist');
        req.flush('Not found', { status: 404, statusText: 'Not Found' });
        await fixture.whenStable();

        expect(getTranscriptionSpy).not.toHaveBeenCalled();
        expect(component.playlistUrl()).toBeUndefined();
        expect(component.hasTranscript()).toBe(false);
        expect(component.isLoading()).toBe(false);

        // Test null playlist response
        component.lectureUnit().videoSource = 'https://example.com/some-video';
        component['_playlistUrl'].set('stale.m3u8');
        component['_transcriptSegments'].set([{ startTime: 0, endTime: 1, text: 'stale', slideNumber: 1 }]);
        component['_isLoading'].set(true);
        component.toggleCollapse(false);

        expectPlaylistRequest('https://example.com/some-video', null);
        await fixture.whenStable();

        expect(getTranscriptionSpy).not.toHaveBeenCalled();
        expect(component.playlistUrl()).toBeUndefined();
        expect(component.hasTranscript()).toBe(false);
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

        expect(component.isLoading()).toBe(false);

        component.toggleCollapse(false);

        expect(component.isLoading()).toBe(true);

        // Mock the HTTP request (even .m3u8 URLs go through the API)
        const req = httpMock.expectOne((request) => request.url === '/api/tumlive/playlist' && request.params.get('url') === m3u8Url);
        expect(req.request.method).toBe('GET');
        req.flush(m3u8Url);

        await fixture.whenStable();

        expect(component.playlistUrl()).toBe(m3u8Url);
        expect(component.transcriptSegments()).toHaveLength(1);
        expect(component.hasTranscript()).toBe(true);
        expect(component.transcriptSegments()[0].text).toBe('Direct HLS transcript');
        expect(component.isLoading()).toBe(false);
    });

    it('fetchTranscript: handles empty transcription response and keeps segments empty', async () => {
        fixture.detectChanges();

        vi.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(undefined));

        (component as any).fetchTranscript();
        await fixture.whenStable();

        expect(component.transcriptSegments()).toEqual([]);
        expect(component.hasTranscript()).toBe(false);
    });

    it('toggleCollapse(false): non-TUM Live URL does not trigger transcript fetch when resolver returns null', async () => {
        const nonTumLiveUrl = 'https://example.com/some-video';
        component.lectureUnit().videoSource = nonTumLiveUrl;

        const getTranscriptionSpy = vi.spyOn(lectureTranscriptionService, 'getTranscription');

        fixture.detectChanges();

        expect(component.isLoading()).toBe(false);

        component.toggleCollapse(false);

        expect(component.isLoading()).toBe(true);

        // Mock the HTTP request to return null (no playlist found)
        const req = httpMock.expectOne((request) => request.url === '/api/tumlive/playlist' && request.params.get('url') === nonTumLiveUrl);
        req.flush(null);

        await fixture.whenStable();

        expect(getTranscriptionSpy).not.toHaveBeenCalled();
        expect(component.playlistUrl()).toBeUndefined();
        expect(component.hasTranscript()).toBe(false);
        expect(component.isLoading()).toBe(false);
    });

    it('toggleCollapse(false): sets isLoading to false immediately when no video source', () => {
        component.lectureUnit().videoSource = undefined;
        fixture.detectChanges();

        expect(component.isLoading()).toBe(false);

        component.toggleCollapse(false);

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

            expect(component.hasPdf()).toBe(true);
        });

        it('isPdf: returns false for non-PDF file extensions', () => {
            component.lectureUnit().attachment!.link = '/path/to/file/document.docx';
            fixture.detectChanges();

            expect(component.hasPdf()).toBe(false);
        });

        it('isPdf: handles uppercase PDF extension', () => {
            component.lectureUnit().attachment!.link = '/path/to/file/document.PDF';
            fixture.detectChanges();

            expect(component.hasPdf()).toBe(true);
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

        it('loadPdf: loads directly via URL, then falls back to blob on error', async () => {
            const testBlob = new Blob(['fake pdf content'], { type: 'application/pdf' });
            const mockBlobUrl = 'blob:http://localhost/fallback-pdf';
            const createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue(mockBlobUrl);
            const getBlobFromUrlSpy = vi.spyOn(fileService, 'getBlobFromUrl').mockReturnValue(of(testBlob));

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

            // Blob fallback should trigger only one request even if the direct-load error fires twice
            component['onPdfLoadError']({ pdfUrl: 'api/core/files//path/to/file/test.pdf' });
            expect(getBlobFromUrlSpy).toHaveBeenCalledTimes(1);
            expect(getBlobFromUrlSpy).toHaveBeenCalledWith('api/core/files//path/to/file/test.pdf');

            expect(component.isPdfLoading()).toBe(true);
            expect(component.pdfUrl()).toBe(mockBlobUrl);
            expect(createObjectURLSpy).toHaveBeenCalledWith(testBlob);

            // Complete loading
            component['onPdfPageRendered']({ pdfUrl: mockBlobUrl });
            expect(component.isPdfLoading()).toBe(false);

            createObjectURLSpy.mockRestore();
        });

        it('onPdfLoadError: ignores errors for non-matching URLs', () => {
            component.pdfUrl.set('api/core/files/test.pdf');
            component['onPdfLoadError']({ pdfUrl: 'different-url.pdf' });

            expect(component.pdfUrl()).toBe('api/core/files/test.pdf'); // unchanged
            expect(component.pdfLoadError()).toBe(false);
        });

        it('onPdfLoadError: sets error when blob URL fails', () => {
            const blobUrl = 'blob:http://localhost/test';
            component.pdfUrl.set(blobUrl);
            const revokeSpy = vi.spyOn(URL, 'revokeObjectURL');

            component['onPdfLoadError']({ pdfUrl: blobUrl });

            expect(component.pdfUrl()).toBeUndefined();
            expect(component.pdfLoadError()).toBe(true);
            expect(component.isPdfLoading()).toBe(false);
            expect(revokeSpy).toHaveBeenCalledWith(blobUrl);
        });

        it('onPdfPageRendered: stops loading when first page is rendered', () => {
            const url = 'api/core/files/test.pdf';
            component.pdfUrl.set(url);
            component.isPdfLoading.set(true);

            component['onPdfPageRendered']({ pdfUrl: url });

            expect(component.isPdfLoading()).toBe(false);
        });

        it('onPdfPageRendered: ignores events for non-matching URLs', () => {
            component.pdfUrl.set('api/core/files/test.pdf');
            component.isPdfLoading.set(true);

            component['onPdfPageRendered']({ pdfUrl: 'different.pdf' });

            expect(component.isPdfLoading()).toBe(true); // unchanged
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
            const videoReq = httpMock.expectOne((request) => request.url === '/api/tumlive/playlist');
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

    describe('Resizable Splitters', () => {
        it('resetSplitSizesForFullscreen: uses ~33/33/33 defaults for three-panel layout', () => {
            component.lectureUnit().videoSource = 'https://live.rbg.tum.de/w/abcd/1234?video_only=1';
            component.lectureUnit().attachment!.link = '/path/to/file/test.pdf';
            fixture.componentRef.setInput('irisSettings', {
                settings: { enabled: true },
            });
            component.lectureUnit().lecture = { id: 1, isTutorialLecture: false } as any;

            component['resetSplitSizesForFullscreen']();

            expect(component.verticalSplitSizes()).toEqual([66.67, 33.33]);
            expect(component.horizontalSplitSizes()).toEqual([50, 50]);
        });
    });

    describe('Fullscreen behavior', () => {
        it('openFullscreen: returns immediately when no fullscreen content is available', () => {
            component.lectureUnit().videoSource = undefined;
            component.lectureUnit().attachment = undefined;
            const activateSpy = vi.spyOn(component as any, 'activateFullscreen');

            component.openFullscreen();

            expect(activateSpy).not.toHaveBeenCalled();
        });

        it('openFullscreen: expands collapsed card before activating fullscreen', async () => {
            component.lectureUnit().videoSource = 'https://live.rbg.tum.de/w/abcd/1234?video_only=1';
            fixture.componentRef.setInput('irisSettings', {
                settings: { enabled: true },
            });
            component.lectureUnit().lecture = { id: 1, isTutorialLecture: false } as any;
            const toggleCollapse = vi.fn();
            const activateSpy = vi.spyOn(component as any, 'activateFullscreen').mockImplementation(() => {});
            vi.spyOn(component, 'lectureUnitCard').mockReturnValue({
                isCollapsed: () => true,
                toggleCollapse,
            } as any);

            component.openFullscreen();
            await fixture.whenStable();

            expect(toggleCollapse).toHaveBeenCalledTimes(1);
            expect(activateSpy).toHaveBeenCalledTimes(1);
        });

        it('onEscapePressed: delegates to closeFullscreen', () => {
            const closeSpy = vi.spyOn(component, 'closeFullscreen');
            const event = new KeyboardEvent('keydown', { key: 'Escape', cancelable: true });
            component['_isFullscreen'].set(true);

            component.onEscapePressed(event);

            expect(closeSpy).toHaveBeenCalledTimes(1);
        });

        it('onEscapePressed: does not close when pdf fullscreen is active', () => {
            const closeSpy = vi.spyOn(component, 'closeFullscreen');
            component['_isFullscreen'].set(true);
            component['onPdfFullscreenChange'](true);
            const event = new KeyboardEvent('keydown', { key: 'Escape', cancelable: true });

            component.onEscapePressed(event);

            expect(closeSpy).not.toHaveBeenCalled();
        });
    });
});
