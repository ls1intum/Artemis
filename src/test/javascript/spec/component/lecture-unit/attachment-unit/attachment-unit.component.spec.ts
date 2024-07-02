import { FileService } from 'app/shared/http/file.service';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { AttachmentType } from 'app/entities/attachment.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockProvider } from 'ng-mocks';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ScienceService } from 'app/shared/science/science.service';
import { MockScienceService } from '../../../helpers/mocks/service/mock-science-service';
import { IconDefinition, faFile, faFileCsv, faFileImage, faFilePdf } from '@fortawesome/free-solid-svg-icons';

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
                MockProvider(FileService),
                { provide: ScienceService, useClass: MockScienceService },
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

    it('should handle download', () => {
        const downloadFileSpy = jest.spyOn(fileService, 'downloadFile');
        const onCompletionEmitSpy = jest.spyOn(component.onCompletion, 'emit');

        fixture.detectChanges();
        component.handleDownload();

        expect(downloadFileSpy).toHaveBeenCalledOnce();
        expect(onCompletionEmitSpy).toHaveBeenCalledOnce();
    });

    it.each([
        ['pdf', faFilePdf],
        ['csv', faFileCsv],
        ['png', faFileImage],
        ['exotic', faFile],
    ])('should use correct icon for extension', async (extension: string, icon: IconDefinition) => {
        const getAttachmentIconSpy = jest.spyOn(component, 'getAttachmentIcon');
        component.lectureUnit().attachment!.link = `/path/to/file/test.${extension}`;
        fixture.detectChanges();

        expect(getAttachmentIconSpy).toHaveReturnedWith(icon);
    });

    it('should download attachment when clicked', () => {
        const downloadFileSpy = jest.spyOn(fileService, 'downloadFile');

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
});
