import dayjs from 'dayjs/esm';

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapse, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { FileService } from 'app/shared/http/file.service';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faFile, faFileCsv, faFileImage, faFilePdf } from '@fortawesome/free-solid-svg-icons';

describe('AttachmentUnitComponent', () => {
    let attachmentUnit: AttachmentUnit;
    let attachment: Attachment;

    let attachmentUnitComponentFixture: ComponentFixture<AttachmentUnitComponent>;
    let attachmentUnitComponent: AttachmentUnitComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                AttachmentUnitComponent,
                MockDirective(NgbCollapse),
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(NgbTooltip),
            ],
            providers: [MockProvider(FileService)],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                attachmentUnitComponentFixture = TestBed.createComponent(AttachmentUnitComponent);
                attachmentUnitComponent = attachmentUnitComponentFixture.componentInstance;

                attachment = new Attachment();
                attachment.id = 1;
                attachment.version = 1;
                attachment.attachmentType = AttachmentType.FILE;
                attachment.releaseDate = dayjs().year(2010).month(3).date(5);
                attachment.uploadDate = dayjs().year(2010).month(3).date(5);
                attachment.name = 'test';
                attachment.link = '/path/to/file/test.pdf';

                attachmentUnit = new AttachmentUnit();
                attachmentUnit.id = 1;
                attachmentUnit.description = 'lorem ipsum';
                attachmentUnit.attachment = attachment;
                attachmentUnitComponent.attachmentUnit = attachmentUnit;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        attachmentUnitComponentFixture.detectChanges();
        attachment.link = '/path/to/file/test.jpg';
        attachmentUnitComponentFixture.detectChanges();
        attachment.link = '/path/to/file/test.svg';
        attachmentUnitComponentFixture.detectChanges();
        attachment.link = '/path/to/file/test.zip';
        attachmentUnitComponentFixture.detectChanges();
        attachment.link = '/path/to/file/test.something';
        expect(attachmentUnitComponent).not.toBeNull();
    });

    it('should calculate correct fileName', () => {
        const getFileNameSpy = jest.spyOn(attachmentUnitComponent, 'getFileName');
        attachmentUnitComponentFixture.detectChanges();
        expect(getFileNameSpy).toHaveReturnedWith('test.pdf');
    });

    it.each([
        ['pdf', faFilePdf],
        ['csv', faFileCsv],
        ['png', faFileImage],
        ['exotic', faFile],
    ])('should use correct icon for extension', (extension: string, icon: IconDefinition) => {
        const getAttachmentIconSpy = jest.spyOn(attachmentUnitComponent, 'getAttachmentIcon');
        attachmentUnitComponent.attachmentUnit!.attachment!.link = `/path/to/file/test.${extension}`;
        attachmentUnitComponentFixture.detectChanges();
        expect(getAttachmentIconSpy).toHaveReturnedWith(icon);
    });

    it('should download attachment when clicked', () => {
        const fileService = TestBed.inject(FileService);
        const downloadFileStub = jest.spyOn(fileService, 'downloadFileWithAccessToken');
        const downloadButton = attachmentUnitComponentFixture.debugElement.nativeElement.querySelector('#downloadButton');
        expect(downloadButton).not.toBeNull();
        downloadButton.click();
        expect(downloadFileStub).toHaveBeenCalledOnce();
    });

    it('should call completion callback when downloaded', () => {
        return new Promise<void>((done) => {
            attachmentUnitComponent.onCompletion.subscribe((event) => {
                expect(event.lectureUnit).toEqual(attachmentUnit);
                expect(event.completed).toBeTrue();
                done();
            });
            attachmentUnitComponent.downloadAttachment();
        });
    }, 1000);

    it('should call completion callback when clicked', () => {
        return new Promise<void>((done) => {
            attachmentUnitComponent.onCompletion.subscribe((event) => {
                expect(event.lectureUnit).toEqual(attachmentUnit);
                expect(event.completed).toBeFalse();
                done();
            });
            attachmentUnitComponent.handleClick(new Event('click'), false);
        });
    }, 1000);
});
