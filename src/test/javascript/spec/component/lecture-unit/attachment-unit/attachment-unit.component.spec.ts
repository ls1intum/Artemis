import { FileService } from 'app/shared/http/file.service';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockProvider } from 'ng-mocks';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
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
import { MockFileService } from '../../../helpers/mocks/service/mock-file.service';
import { fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

describe('AttachmentUnitComponent', () => {
    let scienceService: ScienceService;
    let fileService: FileService;

    let component: AttachmentUnitComponent;
    let fixture: ComponentFixture<AttachmentUnitComponent>;

    const attachmentUnit: AttachmentUnit = {
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
            imports: [AttachmentUnitComponent],
            providers: [
                provideHttpClient(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                { provide: FileService, useClass: MockFileService },
                MockProvider(ScienceService),
            ],
        }).compileComponents();

        scienceService = TestBed.inject(ScienceService);
        fileService = TestBed.inject(FileService);

        fixture = TestBed.createComponent(AttachmentUnitComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('lectureUnit', attachmentUnit);
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

    it('should handle download', fakeAsync(() => {
        const downloadFileSpy = jest.spyOn(fileService, 'downloadFile');
        const onCompletionEmitSpy = jest.spyOn(component.onCompletion, 'emit');

        jest.spyOn(component['attachmentService'], 'getAttachmentByParentAttachmentId').mockReturnValue(
            of(
                new HttpResponse({
                    body: { link: '/path/to/hidden/file.pdf' },
                    status: 200,
                }),
            ),
        );

        fixture.detectChanges();
        component.handleDownload();
        tick();

        expect(downloadFileSpy).toHaveBeenCalledWith('/path/to/hidden/file.pdf');
        expect(onCompletionEmitSpy).toHaveBeenCalledWith({ lectureUnit: attachmentUnit, completed: true });
    }));

    it('should use original attachment link if hidden attachment is not found', fakeAsync(() => {
        const downloadFileSpy = jest.spyOn(fileService, 'downloadFile');
        const originalLink = '/path/to/file/test.pdf';

        fixture.componentRef.setInput('lectureUnit', {
            attachment: { id: 1, link: originalLink },
        } as AttachmentUnit);

        jest.spyOn(component['attachmentService'], 'getAttachmentByParentAttachmentId').mockReturnValue(
            of(
                new HttpResponse<Attachment>({
                    status: 200,
                }),
            ),
        );

        fixture.detectChanges();
        component.handleDownload();
        tick();

        expect(downloadFileSpy).toHaveBeenCalledWith(originalLink);
    }));

    it('should show an error message if hidden attachment retrieval fails', fakeAsync(() => {
        const alertServiceSpy = jest.spyOn(component['alertService'], 'error');

        // Provide the lecture unit via setInput method
        fixture.componentRef.setInput('lectureUnit', {
            attachment: { id: 1, link: '/path/to/file/test.pdf' },
        } as AttachmentUnit);

        jest.spyOn(component['attachmentService'], 'getAttachmentByParentAttachmentId').mockReturnValue(throwError(() => new Error('Service error')));

        fixture.detectChanges();
        component.handleDownload();
        tick();

        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.hiddenAttachmentRetrievalError', { error: 'Service error' });
    }));

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

    it('should download attachment when clicked', fakeAsync(() => {
        const downloadFileSpy = jest.spyOn(fileService, 'downloadFile');

        jest.spyOn(component['attachmentService'], 'getAttachmentByParentAttachmentId').mockReturnValue(
            of(
                new HttpResponse({
                    body: { link: '/path/to/hidden/file.pdf' },
                    status: 200,
                }),
            ),
        );

        fixture.detectChanges();

        const viewIsolatedButton = fixture.debugElement.query(By.css('#view-isolated-button'));
        viewIsolatedButton.nativeElement.click();
        tick();

        fixture.detectChanges();

        expect(downloadFileSpy).toHaveBeenCalledWith('/path/to/hidden/file.pdf');
    }));

    it('should call completion callback when downloaded', () => {
        const scienceLogSpy = jest.spyOn(scienceService, 'logEvent');
        component.handleDownload();

        expect(scienceLogSpy).toHaveBeenCalled();
    });

    it('should toggle completion', async () => {
        const onCompletionEmitSpy = jest.spyOn(component.onCompletion, 'emit');

        jest.spyOn(component['attachmentService'], 'getAttachmentByParentAttachmentId').mockReturnValue(
            of(
                new HttpResponse({
                    body: { link: '/path/to/hidden/file.pdf' },
                    status: 200,
                }),
            ),
        );

        component.handleDownload();
        await fixture.whenStable();

        expect(onCompletionEmitSpy).toHaveBeenCalled();
    });
});
