import { ComponentFixture, fakeAsync, TestBed, tick, getTestBed } from '@angular/core/testing';
import dayjs from 'dayjs';
import { ArtemisTestModule } from '../../test.module';
import { ActivatedRoute } from '@angular/router';
import { By } from '@angular/platform-browser';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { Lecture } from 'app/entities/lecture.model';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { LectureAttachmentsComponent } from 'app/lecture/lecture-attachments.component';
import { AttachmentService } from 'app/lecture/attachment.service';
import { FileService } from 'app/shared/http/file.service';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MockPipe, MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { MockFileService } from '../../helpers/mocks/service/mock-file.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { NgModel } from '@angular/forms';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

describe('LectureAttachmentsComponent', () => {
    let comp: LectureAttachmentsComponent;
    let fixture: ComponentFixture<LectureAttachmentsComponent>;
    let injector: TestBed;
    let fileUploaderService: FileUploaderService;
    let attachmentService: AttachmentService;
    let attachmentServiceFindAllByLectureIdStub: jest.SpyInstance;

    const lecture = {
        id: 4,
        title: 'Second Test Lecture2',
        description:
            'Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.',
        startDate: dayjs('2019-04-15T14:00:19+02:00'),
        endDate: dayjs('2019-04-15T15:30:20+02:00'),
        course: {
            id: 1,
            title: 'Refactoring CSS',
            description:
                'Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.',
            shortName: 'RCSS',
            studentGroupName: 'artemis-dev',
            teachingAssistantGroupName: 'tumuser',
            instructorGroupName: 'tumuser',
            startDate: dayjs('2018-12-15T16:11:00+01:00'),
            endDate: dayjs('2019-06-15T16:11:14+02:00'),
            onlineCourse: false,
            color: '#691b0b',
            registrationEnabled: false,
        },
    } as Lecture;

    const attachments = [
        {
            id: 50,
            name: 'test',
            link: '/api/files/attachments/lecture/4/Mein_Test_PDF4.pdf',
            version: 2,
            uploadDate: dayjs('2019-05-05T10:05:25+02:00'),
            attachmentType: 'FILE',
        },
        {
            id: 52,
            name: 'test2',
            link: '/api/files/attachments/lecture/4/Mein_Test_PDF3.pdf',
            version: 1,
            uploadDate: dayjs('2019-05-07T08:49:59+02:00'),
            attachmentType: 'FILE',
        },
    ] as Attachment[];

    const newAttachment = {
        id: 53,
        name: 'TestFile',
        link: '/api/files/attachments/lecture/4/Mein_Test_PDF3.pdf',
        version: 1,
        uploadDate: dayjs('2019-05-07T08:49:59+02:00'),
        attachmentType: 'FILE',
    } as Attachment;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                LectureAttachmentsComponent,
                MockComponent(FormDateTimePickerComponent),
                MockComponent(AlertErrorComponent),
                MockDirective(DeleteButtonDirective),
                MockDirective(NgModel),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: { parent: { data: of({ lecture }) } } },
                { provide: FileService, useClass: MockFileService },
                MockProvider(AttachmentService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LectureAttachmentsComponent);
                comp = fixture.componentInstance;
                injector = getTestBed();
                fileUploaderService = injector.get(FileUploaderService);
                attachmentService = injector.get(AttachmentService);
                attachmentServiceFindAllByLectureIdStub = jest.spyOn(attachmentService, 'findAllByLectureId').mockReturnValue(of(new HttpResponse({ body: [...attachments] })));
            });
    });

    afterEach(() => {
        comp.attachments = [...attachments];
        jest.restoreAllMocks();
    });

    it('should accept file and add attachment to list', fakeAsync(() => {
        fixture.detectChanges();
        jest.spyOn(attachmentService, 'create').mockReturnValue(of(new HttpResponse({ body: newAttachment })));
        const addAttachmentButton = fixture.debugElement.query(By.css('#add-attachment'));
        expect(comp.attachmentToBeCreated).toBe(undefined);
        expect(addAttachmentButton).not.toBe(null);
        addAttachmentButton.nativeElement.click();
        fixture.detectChanges();
        const fakeBlob = new Blob([''], { type: 'application/pdf' });
        fakeBlob['name'] = 'Test-File.pdf';
        comp.attachmentFile = fakeBlob;
        const uploadAttachmentButton = fixture.debugElement.query(By.css('#upload-attachment'));
        expect(uploadAttachmentButton).not.toBe(null);
        expect(comp.attachmentToBeCreated).not.toBe(null);
        comp.attachmentToBeCreated!.name = 'Test File Name';
        jest.spyOn(fileUploaderService, 'uploadFile').mockReturnValue(Promise.resolve({ path: 'test' }));
        uploadAttachmentButton.nativeElement.click();
        fixture.detectChanges();
        tick();
        expect(comp.attachments.length).toBe(3);
        expect(attachmentServiceFindAllByLectureIdStub).toHaveBeenCalledTimes(1);
    }));

    it('should not accept too large file', fakeAsync(() => {
        fixture.detectChanges();
        const addAttachmentButton = fixture.debugElement.query(By.css('#add-attachment'));
        expect(comp.attachmentToBeCreated).toBe(undefined);
        expect(addAttachmentButton).not.toBe(null);
        addAttachmentButton.nativeElement.click();
        fixture.detectChanges();
        const fakeBlob = {};
        fakeBlob['name'] = 'Test-File.pdf';
        fakeBlob['size'] = 100000000000000000;
        comp.attachmentFile = fakeBlob as Blob;
        const uploadAttachmentButton = fixture.debugElement.query(By.css('#upload-attachment'));
        expect(uploadAttachmentButton).not.toBe(null);
        expect(comp.attachmentToBeCreated).not.toBe(null);
        comp.attachmentToBeCreated!.name = 'Test File Name';
        uploadAttachmentButton.nativeElement.click();
        tick();
        fixture.detectChanges();
        const fileAlert = fixture.debugElement.query(By.css('#too-large-file-alert'));
        expect(comp.attachments.length).toBe(2);
        expect(fileAlert).not.toBe(null);
        expect(attachmentServiceFindAllByLectureIdStub).toHaveBeenCalledTimes(1);
    }));

    it('should exit saveAttachment', fakeAsync(() => {
        fixture.detectChanges();
        comp.attachmentToBeCreated = undefined;
        comp.saveAttachment();
        expect(attachmentServiceFindAllByLectureIdStub).toHaveBeenCalledTimes(1);
        expect(comp.attachmentToBeCreated).toEqual({
            lecture: comp.lecture,
            attachmentType: AttachmentType.FILE,
            version: 0,
            uploadDate: comp.attachmentToBeCreated!.uploadDate,
        } as Attachment);
    }));

    it('should create Attachment', fakeAsync(() => {
        fixture.detectChanges();
        comp.attachmentToBeCreated = {
            id: 1,
            lecture: comp.lecture,
            attachmentType: AttachmentType.FILE,
            version: 1,
            uploadDate: dayjs(),
        } as Attachment;
        comp.notificationText = 'wow how did i get here';
        const attachmentServiceUpdateStub = jest.spyOn(attachmentService, 'update').mockReturnValue(
            of(
                new HttpResponse({
                    body: {
                        id: 52,
                        name: 'TestFile',
                        link: '/api/files/attachments/lecture/4/Mein_Test_PDF3.pdf',
                        version: 2,
                        uploadDate: dayjs('2019-05-07T08:49:59+02:00'),
                        attachmentType: 'FILE',
                    } as Attachment,
                }),
            ),
        );
        comp.saveAttachment();
        expect(attachmentServiceUpdateStub).toHaveBeenCalledTimes(1);
        expect(comp.attachments[1].version).toBe(2);
        expect(attachmentServiceFindAllByLectureIdStub).toHaveBeenCalledTimes(1);
    }));

    it('should edit attachment', fakeAsync(() => {
        fixture.detectChanges();
        comp.attachmentToBeCreated = undefined;
        expect(comp.attachmentToBeCreated).toBe(undefined);
        comp.editAttachment(newAttachment);
        expect(comp.attachmentToBeCreated).toBe(newAttachment);
        expect(comp.attachmentBackup).toEqual(newAttachment);
        expect(attachmentServiceFindAllByLectureIdStub).toHaveBeenCalledTimes(1);
    }));

    it('should delete attachment', fakeAsync(() => {
        fixture.detectChanges();
        const toDelete = {
            id: 52,
            name: 'test2',
            link: '/api/files/attachments/lecture/4/Mein_Test_PDF3.pdf',
            version: 1,
            uploadDate: dayjs('2019-05-07T08:49:59+02:00'),
            attachmentType: 'FILE',
        } as Attachment;
        const attachmentServiceDeleteStub = jest.spyOn(attachmentService, 'delete').mockReturnValue(of(new HttpResponse({ body: newAttachment })));
        comp.deleteAttachment(toDelete);
        expect(comp.attachments.length).toBe(1);
        expect(attachmentServiceDeleteStub).toHaveBeenCalledTimes(1);
        expect(attachmentServiceFindAllByLectureIdStub).toHaveBeenCalledTimes(1);
    }));

    it('should call cancel', fakeAsync(() => {
        fixture.detectChanges();
        const toCancel = {
            id: 52,
            name: 'test34',
            link: '/api/files/attachments/lecture/4/Mein_Test_PDF34.pdf',
            version: 5,
            uploadDate: dayjs('2019-05-07T08:49:59+02:00'),
            attachmentType: 'FILE',
        } as Attachment;
        comp.attachmentBackup = toCancel;
        comp.cancel();
        expect(comp.attachments[1]).toBe(toCancel);
        expect(attachmentServiceFindAllByLectureIdStub).toHaveBeenCalledTimes(1);
    }));

    it('should download attachment', fakeAsync(() => {
        fixture.detectChanges();
        comp.isDownloadingAttachmentLink = undefined;
        expect(comp.isDownloadingAttachmentLink).toBe(undefined);
        comp.downloadAttachment('https://my/own/download/url');
        expect(comp.isDownloadingAttachmentLink).toBe(undefined);
    }));

    it('should set lecture attachment', fakeAsync(() => {
        fixture.detectChanges();
        const myBlob1 = { size: 1024, name: '/api/files/attachments/lecture/4/NewTest34.pdf' };
        const myBlob2 = { size: 1024, name: '/api/files/attachments/lecture/4/NewTest100.pdf' };
        const object = {
            target: {
                files: [myBlob1, myBlob2],
            },
        };
        comp.attachmentToBeCreated = newAttachment;
        comp.setLectureAttachment(object);
        expect(comp.attachmentFile).toBe(myBlob1);
        expect(comp.attachmentToBeCreated.link).toBe(myBlob1.name);
        expect(attachmentServiceFindAllByLectureIdStub).toHaveBeenCalledTimes(1);
    }));
});
