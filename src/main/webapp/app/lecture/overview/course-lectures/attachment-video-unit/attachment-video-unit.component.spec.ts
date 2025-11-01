import { AttachmentVideoUnitComponent } from 'app/lecture/overview/course-lectures/attachment-video-unit/attachment-video-unit.component';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { ComponentFixture, TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AttachmentVideoUnitComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: FileService, useClass: MockFileService },
                MockProvider(ScienceService),
                MockProvider(LectureTranscriptionService),
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
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should get file name', () => {
        const getFileNameSpy = jest.spyOn(component, 'getFileName');
        fixture.detectChanges();
        expect(getFileNameSpy).toHaveReturnedWith('test.pdf');
    });

    it('should handle download', () => {
        const createStudentLinkSpy = jest.spyOn(fileService, 'createStudentLink');
        const downloadFileSpy = jest.spyOn(fileService, 'downloadFileByAttachmentName');
        const onCompletionEmitSpy = jest.spyOn(component.onCompletion, 'emit');

        component.handleDownload();

        expect(createStudentLinkSpy).toHaveBeenCalledOnce();
        expect(downloadFileSpy).toHaveBeenCalledOnce();
        expect(onCompletionEmitSpy).toHaveBeenCalledOnce();
    });

    it('should handle original version', () => {
        const downloadFileSpy = jest.spyOn(fileService, 'downloadFileByAttachmentName');
        const onCompletionEmitSpy = jest.spyOn(component.onCompletion, 'emit');

        component.handleOriginalVersion();

        expect(downloadFileSpy).toHaveBeenCalledOnce();
        expect(onCompletionEmitSpy).toHaveBeenCalledOnce();
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
        const getAttachmentIconSpy = jest.spyOn(component, 'getAttachmentIcon');
        component.lectureUnit().attachment!.link = `/path/to/file/test.${extension}`;
        fixture.detectChanges();

        expect(getAttachmentIconSpy).toHaveReturnedWith(icon);
    });

    it('should download attachment when clicked', () => {
        const downloadFileSpy = jest.spyOn(fileService, 'downloadFileByAttachmentName');

        fixture.detectChanges();

        const viewIsolatedButton = fixture.debugElement.query(By.css('#view-isolated-button'));
        viewIsolatedButton.nativeElement.click();

        fixture.detectChanges();
        expect(downloadFileSpy).toHaveBeenCalledOnce();
    });

    it('should call completion callback when downloaded', () => {
        const scienceLogSpy = jest.spyOn(scienceService, 'logEvent');
        component.handleDownload();

        expect(scienceLogSpy).toHaveBeenCalledOnce();
    });

    it('should toggle completion', () => {
        const onCompletionEmitSpy = jest.spyOn(component.onCompletion, 'emit');

        component.handleDownload();

        expect(onCompletionEmitSpy).toHaveBeenCalledOnce();
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
        const parseSpy = jest.spyOn(urlParser, 'parse').mockReturnValue({} as any);

        component.lectureUnit().videoSource = src;
        fixture.detectChanges();

        expect(component.videoUrl()).toBe(src);
        parseSpy.mockRestore();
    });

    it('videoUrl: returns undefined when not allowlisted and parser returns undefined', () => {
        const src = 'https://example.com/not-a-video';
        // @ts-ignore - default export object has parse()
        const parseSpy = jest.spyOn(urlParser, 'parse').mockReturnValue(undefined as any);

        component.lectureUnit().videoSource = src;
        fixture.detectChanges();

        expect(component.videoUrl()).toBeUndefined();
        parseSpy.mockRestore();
    });
    it('toggleCollapse(false): resets state, resolves playlist, fetches transcript (happy path)', fakeAsync(() => {
        // Arrange BEFORE first detectChanges so the computed() caches the right value
        const src = 'https://live.rbg.tum.de/w/abcd/1234?video_only=1';
        const playlist = 'https://cdn.tum/live/abcd/1234/playlist.m3u8';
        component.lectureUnit().videoSource = src;

        (component as any).resolveTumLivePlaylist = jest.fn().mockResolvedValue(playlist);

        const mockTranscriptDTO: LectureTranscriptionDTO = {
            lectureUnitId: 1,
            language: 'en',
            segments: [{ startTime: 0, endTime: 2, text: 'Hello world', slideNumber: 3 }],
        };
        jest.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(mockTranscriptDTO));

        component.transcriptSegments.set([{ startTime: 0, endTime: 1, text: 'old', slideNumber: 1 }]);
        component.playlistUrl.set('stale.m3u8');

        fixture.detectChanges();

        // Act
        component.toggleCollapse(false);

        // state reset happens synchronously
        expect(component.transcriptSegments()).toEqual([]);
        expect(component.playlistUrl()).toBeUndefined();

        // Let the resolveTumLivePlaylist promise run (this triggers fetchTranscript)
        flushMicrotasks();

        expect(component.playlistUrl()).toBe(playlist);
        expect(component.transcriptSegments()).toHaveLength(1);
        expect(component.hasTranscript()).toBeTrue();
    }));

    it('fetchTranscript: handles server error and keeps segments empty', fakeAsync(() => {
        fixture.detectChanges();

        // Mock service to return undefined (simulating error)
        jest.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(undefined));

        // Call the private method directly to isolate error handling
        (component as any).fetchTranscript();

        // Let the Promise chain settle
        flushMicrotasks();

        // Component state remains empty
        expect(component.transcriptSegments()).toEqual([]);
        expect(component.hasTranscript()).toBeFalse();
    }));

    it('toggleCollapse(false): playlist resolve fails -> no transcript fetch', fakeAsync(() => {
        fixture.detectChanges();

        component.lectureUnit().videoSource = 'https://live.rbg.tum.de/w/efgh/9999?video_only=1';

        const resolveSpy = jest
            // @ts-ignore accessing private for test
            .spyOn(component as any, 'resolveTumLivePlaylist')
            .mockResolvedValue(undefined);

        const getTranscriptionSpy = jest.spyOn(lectureTranscriptionService, 'getTranscription');

        component.toggleCollapse(false);

        // Let the Promise chain finish
        flushMicrotasks();

        // Ensure no transcript service call was made
        expect(getTranscriptionSpy).not.toHaveBeenCalled();

        expect(component.playlistUrl()).toBeUndefined();
        expect(component.hasTranscript()).toBeFalse();

        resolveSpy.mockRestore();
    }));

    it('toggleCollapse(false): .m3u8 URL uses playlist directly without resolver', fakeAsync(() => {
        const m3u8Url = 'https://example.com/video/playlist.m3u8';
        component.lectureUnit().videoSource = m3u8Url;

        const resolveSpy = jest
            // @ts-ignore accessing private for test
            .spyOn(component as any, 'resolveTumLivePlaylist')
            .mockResolvedValue('should-not-be-called');

        const mockTranscriptDTO: LectureTranscriptionDTO = {
            lectureUnitId: 1,
            language: 'en',
            segments: [{ startTime: 0, endTime: 5, text: 'Direct HLS transcript', slideNumber: 1 }],
        };
        jest.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(mockTranscriptDTO));

        fixture.detectChanges();

        // Act
        component.toggleCollapse(false);

        // The resolver should NOT be called for .m3u8 URLs
        expect(resolveSpy).not.toHaveBeenCalled();

        // The playlist URL should be set immediately
        expect(component.playlistUrl()).toBe(m3u8Url);

        // Let any pending microtasks finish
        flushMicrotasks();

        expect(component.transcriptSegments()).toHaveLength(1);
        expect(component.hasTranscript()).toBeTrue();
        expect(component.transcriptSegments()[0].text).toBe('Direct HLS transcript');

        resolveSpy.mockRestore();
    }));

    it('hasAttachment / hasVideo and getFileName() when no attachment', () => {
        // initial has attachment
        expect(component.hasAttachment()).toBeTrue();

        // no video by default
        expect(component.hasVideo()).toBeFalse();

        // remove attachment => name becomes empty
        const lu = component.lectureUnit();
        lu.attachment = undefined;
        fixture.detectChanges();

        expect(component.hasAttachment()).toBeFalse();
        expect(component.getFileName()).toBe('');
    });
});
