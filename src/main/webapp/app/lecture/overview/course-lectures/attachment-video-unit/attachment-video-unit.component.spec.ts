import { AttachmentVideoUnitComponent } from 'app/lecture/overview/course-lectures/attachment-video-unit/attachment-video-unit.component';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockProvider } from 'ng-mocks';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ScienceService } from 'app/shared/science/science.service';
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
import { of } from 'rxjs';
import { TranscriptSegment } from 'app/lecture/shared/video-player/video-player.component';

describe('AttachmentVideoUnitComponent', () => {
    let scienceService: ScienceService;
    let fileService: FileService;
    let httpClient: HttpClient;

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
        videoSource: '', // default, override in tests
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AttachmentVideoUnitComponent],
            providers: [
                provideHttpClient(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: FileService, useClass: MockFileService },
                MockProvider(ScienceService),
            ],
        }).compileComponents();

        scienceService = TestBed.inject(ScienceService);
        fileService = TestBed.inject(FileService);
        httpClient = TestBed.inject(HttpClient);

        fixture = TestBed.createComponent(AttachmentVideoUnitComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('lectureUnit', attachmentVideoUnit);
        fixture.componentRef.setInput('courseId', 1);
    });

    afterEach(() => {
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
    ])('should use correct icon for extension', async (extension: string, icon: IconDefinition) => {
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

    it('should fetch transcript when expanded and videoSource is .m3u8', async () => {
        component.lectureUnit().videoSource = 'https://example.com/video.m3u8';
        const segmentsMock: TranscriptSegment[] = [{ startTime: 0, endTime: 5, text: 'Hello World', slideNumber: 1 }];
        const httpSpy = jest.spyOn(httpClient, 'get').mockReturnValue(of({ segments: segmentsMock }));

        component.toggleCollapse(false); // triggers fetchTranscript
        fixture.detectChanges();

        await fixture.whenStable(); // wait for async code in fetchTranscript()

        expect(httpSpy).toHaveBeenCalledWith('/api/lecture/lecture-unit/1/transcript');
        expect(component.transcriptSegments()).toEqual(segmentsMock);
    });

    it('should not fetch transcript if videoSource is not .m3u8', async () => {
        component.lectureUnit().videoSource = 'https://youtube.com/watch?v=abc123';
        const httpSpy = jest.spyOn(httpClient, 'get');

        component.toggleCollapse(false); // expand
        fixture.detectChanges();

        expect(httpSpy).not.toHaveBeenCalled();
        expect(component.transcriptSegments()).toEqual([]);
    });
});
