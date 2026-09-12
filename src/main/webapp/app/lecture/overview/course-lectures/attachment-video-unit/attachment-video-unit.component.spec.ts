import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AttachmentVideoUnitComponent } from 'app/lecture/overview/course-lectures/attachment-video-unit/attachment-video-unit.component';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockComponent, MockProvider } from 'ng-mocks';
import { PdfViewerComponent } from 'app/lecture/shared/pdf-viewer/pdf-viewer.component';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ScienceService } from 'app/foundation/science/science.service';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { LectureTranscriptionDTO } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { of } from 'rxjs';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/foundation/service/alert.service';
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
import { FileService } from 'app/foundation/service/file.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { LectureChatbotComponent } from 'app/iris/overview/lecture-chatbot/lecture-chatbot.component';
import { IrisPointOut } from 'app/iris/shared/entities/iris-point-out.model';
import { WritableSignal, signal } from '@angular/core';

// Mock ResizeObserver for VideoPlayerComponent
class MockResizeObserver {
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
}
global.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver;

describe('AttachmentVideoUnitComponent', () => {
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
        const req = httpMock.expectOne((request) => request.url === '/api/videosource/playlist' && request.params.get('url') === url);
        expect(req.request.method).toBe('GET');
        req.flush(response);
    }

    beforeEach(async () => {
        mockLectureTranscriptionService = {
            getTranscription: vi.fn(() => of(undefined)),
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
                MockProvider(ProfileService),
                MockProvider(IrisChatService, { pointOut$: of() }),
            ],
        })
            // Replace the real engine-backed PDF viewer with a lightweight stub: the unit tests here drive the
            // viewer purely through its public input/output contract (and override `pdfViewer` where needed).
            // The Iris sidebar's chatbot goes the same way — it renders as soon as a test enables Iris and opens
            // the combined view, and it drives the whole Iris chat stack (down to PrimeNG's DialogService), none
            // of which this TestBed provides for. The Iris specs cover the chat itself.
            .overrideComponent(AttachmentVideoUnitComponent, {
                remove: { imports: [PdfViewerComponent, LectureChatbotComponent] },
                add: { imports: [MockComponent(PdfViewerComponent), MockComponent(LectureChatbotComponent)] },
            })
            .compileComponents();

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

    it('tracks the fullscreen state on fullscreen change', () => {
        component['onFullscreenChange'](true);
        expect(component.isFullscreen()).toBe(true);

        component['onFullscreenChange'](false);
        expect(component.isFullscreen()).toBe(false);
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
        expect(downloadFileSpy).toHaveBeenCalledWith(expect.any(String), attachmentVideoUnit.attachment!.name, attachmentVideoUnit.attachment!.version);
        expect(onCompletionEmitSpy).toHaveBeenCalledTimes(1);
    });

    it('should preserve regenerated student-version paths when downloading', () => {
        const downloadFileSpy = vi.spyOn(fileService, 'downloadFile');
        const downloadFileByAttachmentNameSpy = vi.spyOn(fileService, 'downloadFileByAttachmentName');
        const firstStudentVersion = 'attachments/attachment-unit/1/student/StudentVersionSlides_first.pdf';
        const secondStudentVersion = 'attachments/attachment-unit/1/student/StudentVersionSlides_second.pdf';

        fixture.componentRef.setInput('lectureUnit', {
            ...attachmentVideoUnit,
            attachment: { ...attachmentVideoUnit.attachment, studentVersion: firstStudentVersion },
        });
        component.handleDownload();
        fixture.componentRef.setInput('lectureUnit', {
            ...attachmentVideoUnit,
            attachment: { ...attachmentVideoUnit.attachment, studentVersion: secondStudentVersion },
        });
        component.handleDownload();

        expect(downloadFileSpy).toHaveBeenCalledTimes(2);
        expect(downloadFileSpy.mock.calls[0][0]).toContain(firstStudentVersion);
        expect(downloadFileSpy.mock.calls[1][0]).toContain(secondStudentVersion);
        expect(downloadFileSpy.mock.calls[0][0]).not.toBe(downloadFileSpy.mock.calls[1][0]);
        expect(downloadFileByAttachmentNameSpy).not.toHaveBeenCalled();
    });

    it('should handle original version', () => {
        const downloadFileSpy = vi.spyOn(fileService, 'downloadFileByAttachmentName');
        const onCompletionEmitSpy = vi.spyOn(component.onCompletion, 'emit');

        component.handleOriginalVersion();

        expect(downloadFileSpy).toHaveBeenCalledTimes(1);
        expect(downloadFileSpy).toHaveBeenCalledWith(expect.any(String), attachmentVideoUnit.attachment!.name, attachmentVideoUnit.attachment!.version);
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

        const req = httpMock.expectOne((request) => request.url === '/api/videosource/playlist');
        req.flush('Not found', { status: 404, statusText: 'Not Found' });
        await fixture.whenStable();

        expect(getTranscriptionSpy).not.toHaveBeenCalled();
        expect(component.playlistUrl()).toBeUndefined();
        expect(component.hasTranscript()).toBe(false);
        expect(component.isLoading()).toBe(false);

        // Test null playlist response
        component.lectureUnit().videoSource = 'https://example.com/some-video';
        component.playlistUrl.set('stale.m3u8');
        component.transcriptSegments.set([{ startTime: 0, endTime: 1, text: 'stale', slideNumber: 1 }]);
        component.isLoading.set(true);
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
        const req = httpMock.expectOne((request) => request.url === '/api/videosource/playlist' && request.params.get('url') === m3u8Url);
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
        const req = httpMock.expectOne((request) => request.url === '/api/videosource/playlist' && request.params.get('url') === nonTumLiveUrl);
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

    it('toggleCollapse(true): resets playerFailed, so a later point-out is not given up on', () => {
        // The failure latch describes the player instance that is torn down on collapse. Left standing it would
        // outlive that instance and make every later timestamp point-out for this unit be dropped as unreachable,
        // even though reopening builds a fresh player that loads fine.
        component.playlistUrl.set('https://cdn.example.com/playlist.m3u8');
        component.transcriptSegments.set([{ startTime: 0, endTime: 10, text: 'Slide 7', slideNumber: 7 }]);
        component.isLoading.set(false);
        component['isTranscriptLoading'].set(false);
        const pointOut = { lectureUnitId: 1, timestamp: 42 } as IrisPointOut;

        component.onPlayerFailed();

        expect(component.playerFailed()).toBe(true);
        expect(component['isPointOutUnreachable'](pointOut)).toBe(true);

        component.toggleCollapse(true);
        fixture.detectChanges();

        expect(component.playerFailed()).toBe(false);
        expect(component['isPointOutUnreachable'](pointOut)).toBe(false);
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

    describe('YouTube player branching (server metadata)', () => {
        it('renders YouTube player when DTO declares videoSourceType YOUTUBE and youtubeVideoId is present', () => {
            fixture.componentRef.setInput('initiallyExpanded', true);
            fixture.componentRef.setInput('lectureUnit', {
                id: 1,
                videoSourceType: 'YOUTUBE',
                youtubeVideoId: 'dQw4w9WgXcQ',
                videoSource: 'https://youtu.be/dQw4w9WgXcQ',
            } as any);
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('jhi-youtube-player')).toBeTruthy();
        });

        it('falls back to iframe with embed URL when playerFailed fires', () => {
            fixture.componentRef.setInput('initiallyExpanded', true);
            fixture.componentRef.setInput('lectureUnit', {
                id: 1,
                videoSourceType: 'YOUTUBE',
                youtubeVideoId: 'dQw4w9WgXcQ',
                videoSource: 'https://youtu.be/dQw4w9WgXcQ',
            } as any);
            fixture.detectChanges();
            component.onPlayerFailed();
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('jhi-youtube-player')).toBeFalsy();
            expect(fixture.nativeElement.querySelector('iframe')).toBeTruthy();
            // iframeFallbackUrl should use privacy-enhanced embed URL, not the raw watch URL
            expect(component.iframeFallbackUrl()).toBe('https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ');
        });

        it('uses raw video source URL for non-YouTube iframe fallback', () => {
            fixture.componentRef.setInput('lectureUnit', {
                id: 5,
                videoSource: 'https://vimeo.com/123456',
            } as any);
            fixture.detectChanges();
            expect(component.iframeFallbackUrl()).toBe('https://vimeo.com/123456');
        });

        it('renders TUM Live player when playlistUrl present (regression guard)', async () => {
            const src = 'https://live.rbg.tum.de/w/abcd/1234?video_only=1';
            const playlist = 'https://cdn.tum/live/abcd/1234/playlist.m3u8';
            const mockTranscriptDTO: LectureTranscriptionDTO = {
                lectureUnitId: 2,
                language: 'en',
                segments: [{ startTime: 0, endTime: 2, text: 'Hello world', slideNumber: 1 }],
            };
            vi.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(mockTranscriptDTO));

            // Set lectureUnit first, then expand (initiallyExpanded triggers toggleCollapse)
            fixture.componentRef.setInput('lectureUnit', {
                id: 2,
                videoSourceType: 'TUM_LIVE',
                videoSource: src,
            } as any);
            fixture.componentRef.setInput('initiallyExpanded', true);
            fixture.detectChanges();

            // Flush the HTTP request triggered by initiallyExpanded → toggleCollapse(false)
            expectPlaylistRequest(src, playlist);
            await fixture.whenStable();
            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('jhi-video-player')).toBeTruthy();
        });

        it('renders iframe fallback for non-YouTube, non-TUM-Live source', async () => {
            fixture.componentRef.setInput('lectureUnit', {
                id: 3,
                videoSource: 'https://youtu.be/dQw4w9WgXcQ',
            } as any);
            fixture.componentRef.setInput('initiallyExpanded', true);
            fixture.detectChanges();

            // initiallyExpanded triggers toggleCollapse → playlist request
            expectPlaylistRequest('https://youtu.be/dQw4w9WgXcQ', null);
            await fixture.whenStable();
            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('iframe')).toBeTruthy();
        });

        it('playerFailed resets when the lecture unit changes', () => {
            fixture.componentRef.setInput('initiallyExpanded', true);
            fixture.componentRef.setInput('lectureUnit', {
                id: 10,
                videoSourceType: 'YOUTUBE',
                youtubeVideoId: 'aaa',
                videoSource: 'https://youtu.be/aaa',
            } as any);
            fixture.detectChanges();
            component.onPlayerFailed();
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('jhi-youtube-player')).toBeFalsy();
            fixture.componentRef.setInput('lectureUnit', {
                id: 11,
                videoSourceType: 'YOUTUBE',
                youtubeVideoId: 'bbb',
                videoSource: 'https://youtu.be/bbb',
            } as any);
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('jhi-youtube-player')).toBeTruthy();
        });
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
            expect(component.pdfUrl()).toBe('api/core/files//path/to/file/test.pdf?version=1');

            // Simulate PDF load error to trigger blob fallback
            component['onPdfLoadError']({ pdfUrl: 'api/core/files//path/to/file/test.pdf?version=1' });

            // Blob fallback should trigger only one request even if the direct-load error fires twice
            component['onPdfLoadError']({ pdfUrl: 'api/core/files//path/to/file/test.pdf?version=1' });
            expect(getBlobFromUrlSpy).toHaveBeenCalledTimes(1);
            expect(getBlobFromUrlSpy).toHaveBeenCalledWith('api/core/files//path/to/file/test.pdf?version=1');

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
            const videoReq = httpMock.expectOne((request) => request.url === '/api/videosource/playlist');
            videoReq.flush(playlist);

            await fixture.whenStable();

            expect(component.playlistUrl()).toBe(playlist);
            // PDF is now loaded directly via URL (no blob)
            expect(component.pdfUrl()).toBe('api/core/files//path/to/file/test.pdf?version=1');
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
        it('openFullscreen: resets split sizes to defaults for three-panel layout', () => {
            // Set up required data
            component.lectureUnit().attachment!.link = '/path/to/file/test.pdf';
            fixture.componentRef.setInput('irisSettings', {
                settings: { enabled: true },
            });
            component.lectureUnit().lecture = { id: 1, isTutorialLecture: false } as any;

            // Mock hasFullscreenContent to return true
            vi.spyOn(component, 'hasFullscreenContent').mockReturnValue(true);

            // Mock lectureUnitCard to return a card that is not collapsed
            const mockCard = { isCollapsed: () => false };
            const mockCardSignal = () => mockCard;
            Object.defineProperty(component, 'lectureUnitCard', {
                value: mockCardSignal,
                writable: true,
                configurable: true,
            });

            // Mock the layout component to capture the open call
            const mockLayout = { open: vi.fn() };
            // Create a mock signal function that returns the mock layout
            const mockLayoutSignal = () => mockLayout;
            Object.defineProperty(component, 'fullscreenLayout', {
                value: mockLayoutSignal,
                writable: true,
                configurable: true,
            });

            component.openFullscreen();

            // The layout component's open() method should be called
            expect(mockLayout.open).toHaveBeenCalledTimes(1);

            // Simulate what the layout component would do: emit split size changes
            component['onVerticalSplitSizesChange']([66.67, 33.33]);
            component['onHorizontalSplitSizesChange']([50, 50]);

            expect(component.verticalSplitSizes()).toEqual([66.67, 33.33]);
            expect(component.horizontalSplitSizes()).toEqual([50, 50]);
        });
    });

    describe('Fullscreen behavior', () => {
        it('navigates to the first PDF page when a display page number appears multiple times', () => {
            // Display number 8 occurs on PDF pages 2 and 3; the first occurrence (page 2) is used as the sync target.
            component.lectureUnit().attachment!.displayPageNumbers = [7, 8, 8];
            component.playlistUrl.set('https://cdn.example.com/playlist.m3u8');
            component.transcriptSegments.set([{ startTime: 0, endTime: 5, text: 'Slide 8', slideNumber: 8 }]);

            expect(component.synchronizationAvailable()).toBe(true);

            const goToPage = vi.fn();
            (component as any).pdfViewer = () => ({ getCurrentPage: () => 1, goToPage });

            component['onSynchronizationToggleChange'](true);
            component['onVideoSlideNumberChange'](8);

            expect(goToPage).toHaveBeenCalledWith(2);
        });

        it('keeps synchronization available when a single slide has no detected display page number (-1)', () => {
            // Slide 2 (index 1) was not detected → -1. It has no sync partner and is skipped,
            // but still occupies PDF page 2, so slide with display number 9 stays on PDF page 3.
            component.lectureUnit().attachment!.displayPageNumbers = [7, -1, 9];
            component.playlistUrl.set('https://cdn.example.com/playlist.m3u8');
            component.transcriptSegments.set([
                { startTime: 0, endTime: 5, text: 'Slide 7', slideNumber: 7 },
                { startTime: 10, endTime: 15, text: 'Slide 9', slideNumber: 9 },
            ]);

            expect(component.synchronizationAvailable()).toBe(true);

            const goToPage = vi.fn();
            (component as any).pdfViewer = () => ({ getCurrentPage: () => 1, goToPage });

            component['onSynchronizationToggleChange'](true);
            component['onVideoSlideNumberChange'](9);

            expect(goToPage).toHaveBeenCalledWith(3);
        });

        it('keeps synchronization available when multiple slides have no detected display page number (-1)', () => {
            // Two undetected slides no longer disable sync; they are simply skipped as having no partner.
            component.lectureUnit().attachment!.displayPageNumbers = [7, -1, -1, 9];
            component.playlistUrl.set('https://cdn.example.com/playlist.m3u8');
            component.transcriptSegments.set([
                { startTime: 0, endTime: 5, text: 'Slide 7', slideNumber: 7 },
                { startTime: 10, endTime: 15, text: 'Slide 9', slideNumber: 9 },
            ]);

            expect(component.synchronizationAvailable()).toBe(true);
        });

        it('disables synchronization when no slide has a detected display page number (all -1)', () => {
            component.lectureUnit().attachment!.displayPageNumbers = [-1, -1];
            component.playlistUrl.set('https://cdn.example.com/playlist.m3u8');
            component.transcriptSegments.set([{ startTime: 0, endTime: 5, text: 'Slide 1', slideNumber: 1 }]);

            expect(component.synchronizationAvailable()).toBe(false);
        });

        it('allows video slides without PDF correspondence and keeps PDF position', () => {
            component.lectureUnit().attachment!.displayPageNumbers = [8, 9];
            component.playlistUrl.set('https://cdn.example.com/playlist.m3u8');
            component.transcriptSegments.set([
                { startTime: 0, endTime: 5, text: 'Intro', slideNumber: 7 },
                { startTime: 10, endTime: 15, text: 'Slide 8', slideNumber: 8 },
            ]);

            // Sync should be available despite extra video slides
            expect(component.synchronizationAvailable()).toBe(true);

            // PDF should stay when video shows slide not in PDF, then move when slide is in PDF
            const goToPage = vi.fn();
            (component as any).pdfViewer = () => ({ getCurrentPage: () => 2, goToPage });

            component['onSynchronizationToggleChange'](true);
            component['onVideoSlideNumberChange'](7);

            expect(goToPage).not.toHaveBeenCalled();

            component['onVideoSlideNumberChange'](8);

            expect(goToPage).toHaveBeenCalledWith(1);
        });

        it('disables synchronization when video and PDF have no overlapping pages', () => {
            component.lectureUnit().attachment!.displayPageNumbers = [25, 26, 28, 42, 100];
            component.playlistUrl.set('https://cdn.example.com/playlist.m3u8');
            component.transcriptSegments.set([
                { startTime: 0, endTime: 5, text: 'Slide 17', slideNumber: 17 },
                { startTime: 10, endTime: 15, text: 'Slide 18', slideNumber: 18 },
                { startTime: 20, endTime: 25, text: 'Slide 19', slideNumber: 19 },
            ]);

            expect(component.synchronizationAvailable()).toBe(false);
        });

        it('syncs the PDF viewer when the active video slide changes', () => {
            component.lectureUnit().attachment!.displayPageNumbers = [7, 8, 9];
            component.playlistUrl.set('https://cdn.example.com/playlist.m3u8');
            component.transcriptSegments.set([{ startTime: 0, endTime: 5, text: 'Slide 8', slideNumber: 8 }]);

            const goToPage = vi.fn();
            (component as any).pdfViewer = () => ({ getCurrentPage: () => 1, goToPage });

            component['onSynchronizationToggleChange'](true);
            component['onVideoSlideNumberChange'](8);

            expect(goToPage).toHaveBeenCalledWith(2);
        });

        it('seeks the video when the PDF page changes', () => {
            component.lectureUnit().attachment!.displayPageNumbers = [7, 8, 9];
            component.playlistUrl.set('https://cdn.example.com/playlist.m3u8');
            component.transcriptSegments.set([
                { startTime: 2, endTime: 5, text: 'Slide 7', slideNumber: 7 },
                { startTime: 9, endTime: 12, text: 'Slide 8', slideNumber: 8 },
            ]);

            const seekTo = vi.fn();
            (component as any).videoPlayer = () => ({ seekTo, isPlaying: () => false, getCurrentSlideNumber: () => undefined });

            component['onSynchronizationToggleChange'](true);
            component['onPdfCurrentPageChange'](2);

            expect(seekTo).toHaveBeenCalledWith(9, false);
        });

        it('does not bounce the PDF when a sync-initiated seek synchronously re-emits a slide', () => {
            // Scrolling to PDF page 2 (display number 8) seeks the video. The player re-emits the active
            // slide synchronously from inside seekTo; if it resolves to the neighbouring slide 7 (a segment
            // boundary), the echo must be suppressed so the PDF is NOT dragged back to page 1.
            component.lectureUnit().attachment!.displayPageNumbers = [7, 8, 9];
            component.playlistUrl.set('https://cdn.example.com/playlist.m3u8');
            component.transcriptSegments.set([
                { startTime: 0, endTime: 5, text: 'Slide 7', slideNumber: 7 },
                { startTime: 5, endTime: 10, text: 'Slide 8', slideNumber: 8 },
            ]);

            const goToPage = vi.fn();
            const seekTo = vi.fn().mockImplementation(() => {
                // Simulate the synchronous echo of the previous slide at the shared boundary.
                component['onVideoSlideNumberChange'](7);
            });
            (component as any).pdfViewer = () => ({ getCurrentPage: () => 2, goToPage });
            (component as any).videoPlayer = () => ({ seekTo, isPlaying: () => false, getCurrentSlideNumber: () => undefined });

            component['onSynchronizationToggleChange'](true);
            component['onPdfCurrentPageChange'](2);

            expect(seekTo).toHaveBeenCalledWith(5, false);
            expect(goToPage).not.toHaveBeenCalled();
        });

        it('openFullscreen: returns immediately when no fullscreen content is available', () => {
            component.lectureUnit().videoSource = undefined;
            component.lectureUnit().attachment = undefined;
            const fullscreenChangeSpy = vi.spyOn(component as any, 'onFullscreenChange');

            component.openFullscreen();

            expect(fullscreenChangeSpy).not.toHaveBeenCalled();
            expect(component.isFullscreen()).toBe(false);
        });

        it('targetCombinedView: opens the combined view once content is there, and only once', () => {
            // A point-out marker clicked from elsewhere in the app arrives as a deep link, and has to end up in the
            // same view as clicking it on this page does. The content is still being resolved when the unit is first
            // built, so the opening waits for it — but must not overrule the student closing the view again.
            const openFullscreen = vi.spyOn(component, 'openFullscreen').mockImplementation(() => {});
            // The combined view also needs the Iris sidebar to be available, as hasFullscreenContent requires both.
            fixture.componentRef.setInput('irisSettings', { settings: { enabled: true } });
            component.lectureUnit().lecture = { id: 1, isTutorialLecture: false } as any;
            component.lectureUnit().videoSource = undefined;
            component.lectureUnit().attachment = undefined;
            fixture.componentRef.setInput('targetCombinedView', true);
            fixture.detectChanges();

            // Nothing to show yet, so nothing is opened and the request is still outstanding.
            expect(openFullscreen).not.toHaveBeenCalled();

            component.lectureUnit().videoSource = 'https://live.rbg.tum.de/w/abcd/1234?video_only=1';
            fixture.componentRef.setInput('lectureUnit', { ...component.lectureUnit() });
            fixture.detectChanges();

            expect(openFullscreen).toHaveBeenCalledOnce();

            // A later run — the student having closed the view in between — must not reopen it.
            component['onFullscreenChange'](false);
            fixture.componentRef.setInput('lectureUnit', { ...component.lectureUnit() });
            fixture.detectChanges();

            expect(openFullscreen).toHaveBeenCalledOnce();
        });

        it('openFullscreen: expands collapsed card before activating fullscreen', () => {
            component.lectureUnit().videoSource = 'https://live.rbg.tum.de/w/abcd/1234?video_only=1';
            fixture.componentRef.setInput('irisSettings', {
                settings: { enabled: true },
            });
            component.lectureUnit().lecture = { id: 1, isTutorialLecture: false } as any;

            const toggleCollapse = vi.fn();
            const mockCard = { isCollapsed: () => true, toggleCollapse };
            const mockCardSignal = vi.fn().mockReturnValue(mockCard);
            Object.defineProperty(component, 'lectureUnitCard', {
                get: () => mockCardSignal,
                configurable: true,
            });

            const mockLayout = { open: vi.fn() };
            const mockLayoutSignal = vi.fn().mockReturnValue(mockLayout);
            Object.defineProperty(component, 'fullscreenLayout', {
                get: () => mockLayoutSignal,
                configurable: true,
            });

            component.openFullscreen();

            // toggleCollapse is called first
            expect(toggleCollapse).toHaveBeenCalledTimes(1);
            // layout.open() will be called via afterNextRender, but we can't easily verify timing here
            // The important part is that toggleCollapse was called
        });

        it('closeFullscreen: delegates to layout component', () => {
            const mockLayout = { close: vi.fn(), open: vi.fn() };
            const mockLayoutSignal = vi.fn().mockReturnValue(mockLayout);
            Object.defineProperty(component, 'fullscreenLayout', {
                get: () => mockLayoutSignal,
                configurable: true,
            });

            component.closeFullscreen();

            expect(mockLayout.close).toHaveBeenCalledTimes(1);
        });

        it('fullscreen flow: open sets state, close resets state', () => {
            expect(component.isFullscreen()).toBe(false);

            // Simulate the layout component emitting fullscreenChange(true)
            component['onFullscreenChange'](true);
            expect(component.isFullscreen()).toBe(true);

            // Simulate the layout component emitting fullscreenChange(false)
            component['onFullscreenChange'](false);
            expect(component.isFullscreen()).toBe(false);
        });
    });

    describe('Context Provider', () => {
        it('contextProvider: returns object with getCurrentPdfPage function', () => {
            const mockPdfViewer = {
                currentPageSignal: vi.fn().mockReturnValue(5),
            };
            Object.defineProperty(component, 'pdfViewer', {
                value: vi.fn().mockReturnValue(mockPdfViewer),
                writable: true,
                configurable: true,
            });

            const provider = component.contextProvider();

            expect(provider).toBeDefined();
            expect(provider.getCurrentPdfPage).toBeDefined();
            expect(provider.getCurrentPdfPage!()).toBe(5);
        });

        it('contextProvider: getCurrentPdfPage returns undefined when no PDF viewer', () => {
            Object.defineProperty(component, 'pdfViewer', {
                value: vi.fn().mockReturnValue(undefined),
                writable: true,
                configurable: true,
            });

            const provider = component.contextProvider();

            expect(provider.getCurrentPdfPage!()).toBeUndefined();
        });

        it('contextProvider: getCurrentVideoTimestamp returns video player time', () => {
            const mockVideoPlayer = {
                getCurrentTime: vi.fn().mockReturnValue(42.5),
            };
            Object.defineProperty(component, 'videoPlayer', {
                value: vi.fn().mockReturnValue(mockVideoPlayer),
                writable: true,
                configurable: true,
            });

            const provider = component.contextProvider();

            expect(provider.getCurrentVideoTimestamp!()).toBe(42.5);
        });

        it('contextProvider: getCurrentVideoTimestamp returns YouTube player time when no video player', () => {
            const mockYoutubePlayer = {
                getCurrentTime: vi.fn().mockReturnValue(125.5),
            };
            Object.defineProperty(component, 'videoPlayer', {
                value: vi.fn().mockReturnValue(undefined),
                writable: true,
                configurable: true,
            });
            Object.defineProperty(component, 'youtubePlayer', {
                value: vi.fn().mockReturnValue(mockYoutubePlayer),
                writable: true,
                configurable: true,
            });

            const provider = component.contextProvider();

            expect(provider.getCurrentVideoTimestamp!()).toBe(125.5);
        });

        it('contextProvider: hasVideoBeenPlayed returns true from video player', () => {
            const mockVideoPlayer = {
                hasBeenPlayed: vi.fn().mockReturnValue(true),
            };
            Object.defineProperty(component, 'videoPlayer', {
                value: vi.fn().mockReturnValue(mockVideoPlayer),
                writable: true,
                configurable: true,
            });

            const provider = component.contextProvider();

            expect(provider.hasVideoBeenPlayed!()).toBe(true);
        });

        it('contextProvider: hasVideoBeenPlayed returns true from YouTube player', () => {
            const mockYoutubePlayer = {
                hasBeenPlayed: vi.fn().mockReturnValue(true),
            };
            Object.defineProperty(component, 'videoPlayer', {
                value: vi.fn().mockReturnValue(undefined),
                writable: true,
                configurable: true,
            });
            Object.defineProperty(component, 'youtubePlayer', {
                value: vi.fn().mockReturnValue(mockYoutubePlayer),
                writable: true,
                configurable: true,
            });

            const provider = component.contextProvider();

            expect(provider.hasVideoBeenPlayed!()).toBe(true);
        });

        it('contextProvider: hasVideoBeenPlayed returns false when neither player has been played', () => {
            Object.defineProperty(component, 'videoPlayer', {
                value: vi.fn().mockReturnValue(undefined),
                writable: true,
                configurable: true,
            });
            Object.defineProperty(component, 'youtubePlayer', {
                value: vi.fn().mockReturnValue(undefined),
                writable: true,
                configurable: true,
            });

            const provider = component.contextProvider();

            expect(provider.hasVideoBeenPlayed!()).toBe(false);
        });
    });

    describe('Iris point-out command handling', () => {
        let chatService: IrisChatService;
        let ackSpy: ReturnType<typeof vi.spyOn>;

        function pointOutRequest(overrides: Partial<IrisPointOut>): IrisPointOut {
            return { correlationId: 'corr', lectureUnitId: 1, ...overrides } as IrisPointOut;
        }

        /**
         * Installs stand-ins for the pdfViewer and videoPlayer viewChildren, mirroring the real ones in the two
         * respects these tests turn on. They refuse what the real ones refuse — a page the loaded document does not
         * have, a position past the end of the video — and they echo an applied navigation back the way the real
         * ones emit it: the viewer reports the new page from inside goToPage, the player its active slide from
         * inside seekTo. Without that echo neither pane could drag the other, which is what the synchronization
         * tests are about; where synchronization is off the component ignores the echo, as it does in production.
         *
         * The page count comes from a signal so that "the document finished loading" re-runs the pending-point-out
         * effect the same way it does in production.
         */
        function mockViewers(totalPages: WritableSignal<number>, duration = 300) {
            let currentPage = 1;
            let currentSlideNumber: number | undefined;
            const canGoToPage = (page: number) => page >= 1 && page <= totalPages();
            const canSeekTo = (seconds: number) => seconds >= 0 && seconds <= duration;
            const goToPage = vi.fn((page: number) => {
                if (!canGoToPage(page)) {
                    return false;
                }
                currentPage = page;
                component['onPdfCurrentPageChange'](page);
                return true;
            });
            // Resolves the slide the way VideoPlayerComponent.updateCurrentSegment does, including its 0.3s
            // tolerance, so a timestamp on a shared boundary lands on the earlier segment here just as it does there.
            const seekTo = vi.fn((seconds: number) => {
                if (!canSeekTo(seconds)) {
                    return false;
                }
                currentSlideNumber = component.transcriptSegments().find((segment) => seconds >= segment.startTime - 0.3 && seconds <= segment.endTime + 0.3)?.slideNumber;
                component['onVideoSlideNumberChange'](currentSlideNumber);
                return true;
            });
            Object.defineProperty(component, 'pdfViewer', {
                value: () => ({ goToPage, canGoToPage, getTotalPages: () => totalPages(), getCurrentPage: () => currentPage }),
                writable: true,
                configurable: true,
            });
            Object.defineProperty(component, 'videoPlayer', {
                value: () => ({ seekTo, canSeekTo, isSeekable: () => true, isUnbounded: () => false, isPlaying: () => false, getCurrentSlideNumber: () => currentSlideNumber }),
                writable: true,
                configurable: true,
            });
            return { goToPage, seekTo };
        }

        /**
         * Makes `hasFullscreenContent()` true, which is what `handlePointOut` consults before holding a marker
         * click open: there has to be content to show and Iris available on a non-tutorial lecture to show it next to.
         */
        function makeCombinedViewOpenable() {
            // A fresh reference rather than a mutation: the computeds behind hasFullscreenContent have already been
            // evaluated by the beforeEach above, and writing into the existing unit would not invalidate them.
            fixture.componentRef.setInput('lectureUnit', { ...component.lectureUnit(), lecture: { id: 1, isTutorialLecture: false } });
            fixture.componentRef.setInput('irisSettings', { settings: { enabled: true } });
        }

        beforeEach(() => {
            chatService = TestBed.inject(IrisChatService);
            ackSpy = vi.spyOn(chatService, 'sendCommandAck').mockImplementation(() => {});
            fixture.detectChanges();
        });

        it('answers a point-out it cannot carry out, and stays silent about one that is not its own', () => {
            // A server-pushed point-out into a closed combined view is answered at once: no view means no navigation,
            // and saying so releases the waiting pipeline instead of making it sit out the ack timeout.
            component['fullscreenState'].set(false);
            component['handlePointOut'](pointOutRequest({ correlationId: 'c1', page: 3 }));

            expect(ackSpy).toHaveBeenCalledExactlyOnceWith('c1', false);

            // A request for another unit is not this unit's to answer — the addressed unit answers it, or the
            // server-side timeout does. Answering it here would deny a point-out that another unit is applying.
            ackSpy.mockClear();
            component['fullscreenState'].set(true);
            component['handlePointOut'](pointOutRequest({ correlationId: 'c2', lectureUnitId: 999, page: 3 }));

            expect(ackSpy).not.toHaveBeenCalled();
        });

        it('acknowledges as not applied when the deck does not have the requested page', () => {
            // Iris names a page the deck does not have, so the viewer stays put. Reporting success here would leave
            // Iris claiming a jump that never happened and persist a point-out chip that does nothing when clicked.
            const { goToPage } = mockViewers(signal(4));
            component['fullscreenState'].set(true);

            component['handlePointOut'](pointOutRequest({ correlationId: 'c8', page: 99 }));
            fixture.detectChanges();

            // The viewer is asked whether it has the page, never told to go there.
            expect(goToPage).not.toHaveBeenCalled();
            expect(ackSpy).toHaveBeenCalledWith('c8', false);
            // Settled either way: the target is known to be unreachable, so it must not linger and fire later.
            expect(component['pendingPointOut']()).toBeUndefined();
        });

        // A point-out naming both a page and a timestamp is all or nothing. Applying the half that holds up would
        // move one pane and leave the other where it was, while the whole thing is reported as not applied and gets
        // no marker — so the student sits in a half-position that nothing in the chat leads back to and that Iris'
        // answer describes wrongly. Both orderings are covered because the two targets are applied one after another.
        it.each([
            ['the page does not exist', { page: 99, timestamp: 12 }],
            ['the timestamp lies past the end of the video', { page: 2, timestamp: 9999 }],
        ])('applies neither half of a combined point-out when %s', (_reason, target) => {
            const { goToPage, seekTo } = mockViewers(signal(4));
            component['fullscreenState'].set(true);
            // Synchronization has to be genuinely available, or the toggle would go off on its own and the
            // assertion below would pass without saying anything about the point-out.
            component.lectureUnit().attachment!.displayPageNumbers = [7, 8, 9];
            component.playlistUrl.set('https://cdn.example.com/playlist.m3u8');
            component.transcriptSegments.set([{ startTime: 0, endTime: 10, text: 'Slide 7', slideNumber: 7 }]);
            component.synchronizeVideoAndSlides.set(true);

            component['handlePointOut'](pointOutRequest({ correlationId: 'c17', ...target }));
            fixture.detectChanges();

            expect(goToPage).not.toHaveBeenCalled();
            expect(seekTo).not.toHaveBeenCalled();
            // The toggle is part of applying a point-out, so it must not have given way for one that never happened.
            expect(component.synchronizeVideoAndSlides()).toBe(true);
            expect(component.syncDisabledByPointOut()).toBeUndefined();
            expect(ackSpy).toHaveBeenCalledWith('c17', false);
            expect(component['pendingPointOut']()).toBeUndefined();
        });

        it('defers the ack until the document has loaded and the navigation has actually been applied', () => {
            // The viewer component renders before its document does and reports 0 pages until then. Acting on that
            // would reject every target; the point-out has to stay pending until the page count is known.
            const totalPages = signal(0);
            const { goToPage } = mockViewers(totalPages);
            component['fullscreenState'].set(true);

            component['handlePointOut'](pointOutRequest({ correlationId: 'c9', page: 3 }));

            // The ack is never sent synchronously — it waits for the navigation effect to run.
            expect(ackSpy).not.toHaveBeenCalled();

            fixture.detectChanges(); // flush the pendingPointOut effect

            expect(goToPage).not.toHaveBeenCalled();
            expect(ackSpy).not.toHaveBeenCalled();
            expect(component['pendingPointOut']()).toBeDefined();

            totalPages.set(10);
            fixture.detectChanges();

            expect(goToPage).toHaveBeenCalledWith(3);
            expect(ackSpy).toHaveBeenCalledWith('c9', true);
        });

        it('holds a marker click until the view it opens is up, and drops it when there is no view to open', () => {
            const { goToPage } = mockViewers(signal(10));
            const openFullscreen = vi.spyOn(component, 'openFullscreen').mockImplementation(() => {});
            component['fullscreenState'].set(false);

            // Iris is not available on this unit yet, so openFullscreen() is a no-op and the view never opens — and
            // never closes either, which is the only transition that drops a stale target. Held on regardless, this
            // one would survive until the student opens the combined view themselves and make it jump out of nowhere.
            component['handlePointOut'](pointOutRequest({ correlationId: undefined, page: 3, forceOpen: true }));

            expect(openFullscreen).not.toHaveBeenCalled();
            expect(component['pendingPointOut']()).toBeUndefined();

            // With a view to open, the click survives the wait for it: openFullscreen() does not set the fullscreen
            // state synchronously but goes through the layout, which reports back via onFullscreenChange, so a
            // forceOpen point-out starts out with isFullscreen() === false.
            makeCombinedViewOpenable();
            component['handlePointOut'](pointOutRequest({ correlationId: undefined, page: 5, forceOpen: true }));
            fixture.detectChanges();

            // Still closed: nothing applied yet, but the target must not have been dropped.
            expect(goToPage).not.toHaveBeenCalled();
            expect(component['pendingPointOut']()).toBeDefined();

            // The layout reports the view as open; only now does the navigation run.
            component['onFullscreenChange'](true);
            fixture.detectChanges();

            expect(goToPage).toHaveBeenCalledWith(5);
            expect(component['pendingPointOut']()).toBeUndefined();
        });

        it('releases a pending point-out when a newer one replaces it or the view closes, unless nobody waits on its answer', () => {
            // No PDF viewer is available here, so a request stays pending and is still unacknowledged when the next
            // one arrives — without releasing it there, its pipeline would wait out the full server-side timeout.
            component['fullscreenState'].set(true);
            component['handlePointOut'](pointOutRequest({ correlationId: 'c5', page: 3 }));
            fixture.detectChanges();

            component['handlePointOut'](pointOutRequest({ correlationId: 'c6', page: 7 }));

            expect(ackSpy).toHaveBeenCalledExactlyOnceWith('c5', false);
            expect(component['pendingPointOut']()!.correlationId).toBe('c6');

            // A marker click supersedes c6, which is released in turn. The click itself has nobody waiting on it, so
            // being superseded by c7 right after passes without a word.
            ackSpy.mockClear();
            component['handlePointOut'](pointOutRequest({ correlationId: undefined, page: 3 }));
            fixture.detectChanges();
            component['handlePointOut'](pointOutRequest({ correlationId: 'c7', page: 7 }));

            expect(ackSpy).toHaveBeenCalledExactlyOnceWith('c6', false);

            // Closing the view is the other way a pending target ends: it is dropped, so a later unrelated reopen
            // does not make the view jump, and its pipeline hears about it right away.
            ackSpy.mockClear();
            component['onFullscreenChange'](false);

            expect(component['pendingPointOut']()).toBeUndefined();
            expect(ackSpy).toHaveBeenCalledExactlyOnceWith('c7', false);
        });

        // A viewer that failed to load is replaced by an error message for as long as the view stays open, and a unit
        // without a PDF never had one to show. Either way waiting would never end and the pipeline would sit out its
        // full ack timeout without ever being answered.
        it.each([
            ['the PDF has failed to load', () => component.pdfLoadError.set(true)],
            ['the unit has no PDF at all', () => fixture.componentRef.setInput('lectureUnit', { ...attachmentVideoUnit, attachment: undefined })],
        ])('gives up on a page target once %s', (_reason, makeUnreachable) => {
            makeUnreachable();
            component['fullscreenState'].set(true);

            component['handlePointOut'](pointOutRequest({ correlationId: 'c10', page: 3 }));
            fixture.detectChanges();

            expect(ackSpy).toHaveBeenCalledWith('c10', false);
            expect(component['pendingPointOut']()).toBeUndefined();
        });

        // Both end in the bare iframe, which cannot be seeked, so no player will ever show up for the target to be
        // applied to: no playlist and no YouTube video at all, or a resolved playlist whose transcript came back
        // empty — the video player renders only with both. Treating the playlist alone as proof of a player would
        // leave the target pending forever and make the waiting pipeline sit out its full ack timeout.
        it.each([
            ['no seekable player can appear', undefined],
            ['the playlist resolved but the transcript came back empty', 'https://cdn.example.com/playlist.m3u8'],
        ])('gives up on a timestamp target when %s', (_reason, playlistUrl) => {
            component['fullscreenState'].set(true);
            component.isLoading.set(false);
            component['isTranscriptLoading'].set(false);
            component.playlistUrl.set(playlistUrl);
            component.transcriptSegments.set([]);

            component['handlePointOut'](pointOutRequest({ correlationId: 'c12', timestamp: 42 }));
            fixture.detectChanges();

            expect(ackSpy).toHaveBeenCalledWith('c12', false);
            expect(component['pendingPointOut']()).toBeUndefined();
        });

        it('gives up on a timestamp target once the rendered player reports an unbounded stream', () => {
            // A live stream has no length to place a position in and never will, so the wait for seekability could
            // not end. Held on, the target would outlive the server's ack timeout and navigate on a later
            // durationchange — after Pyris was told it did not happen and no marker was written for it.
            Object.defineProperty(component, 'videoPlayer', {
                value: () => ({ isSeekable: () => false, isUnbounded: () => true, seekTo: vi.fn(), canSeekTo: () => false }),
                writable: true,
                configurable: true,
            });
            component['fullscreenState'].set(true);
            component.isLoading.set(false);
            component['isTranscriptLoading'].set(false);
            component.playlistUrl.set('https://cdn.example.com/live.m3u8');
            component.transcriptSegments.set([{ startTime: 0, endTime: 10, text: 'Slide 7', slideNumber: 7 }]);

            component['handlePointOut'](pointOutRequest({ correlationId: 'c18', timestamp: 42 }));
            fixture.detectChanges();

            expect(ackSpy).toHaveBeenCalledWith('c18', false);
            expect(component['pendingPointOut']()).toBeUndefined();
        });

        it('keeps a timestamp target pending while the video source and its transcript are still being resolved', () => {
            // The playlist is only known once loading has finished, and the transcript is requested just before the
            // loading flag clears and settles only after it — so an empty transcript is not an answer at either point.
            // Judging by the loading flag alone would report a perfectly good point-out as not applied.
            component['fullscreenState'].set(true);
            component.isLoading.set(true);

            component['handlePointOut'](pointOutRequest({ correlationId: 'c13', timestamp: 42 }));
            fixture.detectChanges();

            expect(ackSpy).not.toHaveBeenCalled();
            expect(component['pendingPointOut']()).toBeDefined();

            // Loading has finished, but the transcript request it started is still in flight.
            component.isLoading.set(false);
            component['isTranscriptLoading'].set(true);
            component.playlistUrl.set('https://cdn.example.com/playlist.m3u8');
            component.transcriptSegments.set([]);
            fixture.detectChanges();

            expect(ackSpy).not.toHaveBeenCalled();
            expect(component['pendingPointOut']()).toBeDefined();

            // Once the request settles without segments the answer is final and the target is released right away.
            component['isTranscriptLoading'].set(false);
            fixture.detectChanges();

            expect(ackSpy).toHaveBeenCalledWith('c13', false);
            expect(component['pendingPointOut']()).toBeUndefined();
        });

        it('waits for a player that can judge the target, not merely for its wrapper component', () => {
            // Angular creates the wrapper long before the YouTube iframe API hands over the real player, and a player
            // that cannot yet state a duration accepts any target and reports it back unchanged. Treating either as
            // readiness would acknowledge a navigation that never happened, so the target stays pending until the
            // player can actually judge it.
            const isSeekable = signal(false);
            const seekTo = vi.fn(() => isSeekable());
            Object.defineProperty(component, 'youtubePlayer', {
                value: () => ({ isSeekable, seekTo, canSeekTo: () => isSeekable() }),
                writable: true,
                configurable: true,
            });
            fixture.componentRef.setInput('lectureUnit', { ...attachmentVideoUnit, youtubeVideoId: 'dQw4w9WgXcQ' });
            component['fullscreenState'].set(true);
            component.isLoading.set(false);
            component.playlistUrl.set(undefined);

            component['handlePointOut'](pointOutRequest({ correlationId: 'c14', timestamp: 42 }));
            fixture.detectChanges();

            expect(seekTo).not.toHaveBeenCalled();
            expect(ackSpy).not.toHaveBeenCalled();
            expect(component['pendingPointOut']()).toBeDefined();

            isSeekable.set(true);
            fixture.detectChanges();

            expect(seekTo).toHaveBeenCalledWith(42, false);
            expect(ackSpy).toHaveBeenCalledWith('c14', true);
        });

        describe('interaction with slide/video synchronization', () => {
            // PDF page 1/2/3 carry display page numbers 7/8/9; the video shows slide 7 from 0s, slide 8 from 10s
            // and slide 9 from 20s. So page 2 and timestamp 12 mean the same slide, while page 2 and timestamp 25
            // contradict each other.
            const transcript = [
                { startTime: 0, endTime: 10, text: 'Slide 7', slideNumber: 7 },
                { startTime: 10, endTime: 20, text: 'Slide 8', slideNumber: 8 },
                { startTime: 20, endTime: 30, text: 'Slide 9', slideNumber: 9 },
            ];

            beforeEach(() => {
                component.lectureUnit().attachment!.displayPageNumbers = [7, 8, 9];
                component.playlistUrl.set('https://cdn.example.com/playlist.m3u8');
                component.transcriptSegments.set(transcript);
                component['fullscreenState'].set(true);
                component.synchronizeVideoAndSlides.set(true);
            });

            it('keeps the toggle on and both panes on Iris position when the two positions agree', () => {
                const { goToPage, seekTo } = mockViewers(signal(3));

                component['handlePointOut'](pointOutRequest({ correlationId: 's1', page: 2, displayPage: 8, timestamp: 12 }));
                fixture.detectChanges();

                // The page jump lets synchronization seek to the slide's segment start first; what counts is that
                // Iris's more precise timestamp lands last and that the echo does not drag the PDF off page 2.
                expect(seekTo).toHaveBeenLastCalledWith(12, false);
                expect(goToPage).toHaveBeenCalledTimes(1);
                expect(goToPage).toHaveBeenCalledWith(2);
                expect(component.synchronizeVideoAndSlides()).toBe(true);
                expect(component.syncDisabledByPointOut()).toBeUndefined();
                expect(ackSpy).toHaveBeenCalledWith('s1', true);
            });

            // Page 2 is slide 8, while timestamp 25 is slide 9. Timestamp 10 ends slide 7's segment and starts slide
            // 8's, where the video player reports the earlier slide 7 — so treating that one as agreeing with page 2
            // would let the echo drag the PDF to slide 7's page 1, off the page Iris named. Either way synchronization
            // is the weaker statement and gives way rather than dragging the PDF anywhere.
            it.each([
                [25, '0:25'],
                [10, '0:10'],
            ])('turns the toggle off and applies both positions when page 2 and timestamp %is show different slides', (timestamp, time) => {
                const { goToPage, seekTo } = mockViewers(signal(3));

                component['handlePointOut'](pointOutRequest({ correlationId: 's2', page: 2, displayPage: 8, timestamp }));
                fixture.detectChanges();

                expect(goToPage).toHaveBeenCalledTimes(1);
                expect(goToPage).toHaveBeenCalledWith(2);
                expect(seekTo).toHaveBeenCalledTimes(1);
                expect(seekTo).toHaveBeenCalledWith(timestamp, false);
                expect(component.synchronizeVideoAndSlides()).toBe(false);
                // Labelled with the number printed on the slide, so the notice agrees with the chip in the chat.
                expect(component.syncDisabledByPointOut()).toEqual({ page: 8, time });
                expect(ackSpy).toHaveBeenCalledWith('s2', true);
            });

            it('lets synchronization derive the video position from a point-out that only names a page', () => {
                // Nothing contradicts synchronization here: the video following Iris to the slide it named is the
                // whole point of the toggle.
                const { seekTo } = mockViewers(signal(3));

                component['handlePointOut'](pointOutRequest({ correlationId: 's3', page: 2, displayPage: 8 }));
                fixture.detectChanges();

                expect(seekTo).toHaveBeenCalledWith(10, false);
                expect(component.synchronizeVideoAndSlides()).toBe(true);
                expect(component.syncDisabledByPointOut()).toBeUndefined();
            });

            it('drops the explanation once the student decides about the toggle themselves', () => {
                mockViewers(signal(3));
                component['handlePointOut'](pointOutRequest({ correlationId: 's5', page: 2, displayPage: 8, timestamp: 25 }));
                fixture.detectChanges();
                expect(component.syncDisabledByPointOut()).toBeDefined();

                component['onSynchronizationToggleChange'](true);

                expect(component.synchronizeVideoAndSlides()).toBe(true);
                expect(component.syncDisabledByPointOut()).toBeUndefined();
            });
        });
    });
});
