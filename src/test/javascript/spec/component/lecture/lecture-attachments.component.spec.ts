import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import * as moment from 'moment';
import { TranslateModule } from '@ngx-translate/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { TreeviewModule } from 'ngx-treeview';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ActivatedRoute } from '@angular/router';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { By } from '@angular/platform-browser';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { Lecture } from 'app/entities/lecture.model';
import { Attachment } from 'app/entities/attachment.model';
import { LectureAttachmentsComponent } from 'app/lecture/lecture-attachments.component';
import { AttachmentService } from 'app/lecture/attachment.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('LectureAttachmentsComponent', () => {
    let comp: LectureAttachmentsComponent;
    let fixture: ComponentFixture<LectureAttachmentsComponent>;
    let fileUploaderService: FileUploaderService;

    const lecture = {
        id: 4,
        title: 'Second Test Lecture2',
        description:
            'Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.',
        startDate: moment('2019-04-15T14:00:19+02:00'),
        endDate: moment('2019-04-15T15:30:20+02:00'),
        course: {
            id: 1,
            title: 'Refactoring CSS',
            description:
                'Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.',
            shortName: 'RCSS',
            studentGroupName: 'artemis-dev',
            teachingAssistantGroupName: 'tumuser',
            instructorGroupName: 'tumuser',
            startDate: moment('2018-12-15T16:11:00+01:00'),
            endDate: moment('2019-06-15T16:11:14+02:00'),
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
            uploadDate: moment('2019-05-05T10:05:25+02:00'),
            attachmentType: 'FILE',
        },
        {
            id: 52,
            name: 'test2',
            link: '/api/files/attachments/lecture/4/Mein_Test_PDF3.pdf',
            version: 1,
            uploadDate: moment('2019-05-07T08:49:59+02:00'),
            attachmentType: 'FILE',
        },
    ] as Attachment[];

    const newAttachment = {
        id: 53,
        name: 'TestFile',
        link: '/api/files/attachments/lecture/4/Mein_Test_PDF3.pdf',
        version: 1,
        uploadDate: moment('2019-05-07T08:49:59+02:00'),
        attachmentType: 'FILE',
    } as Attachment;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, TreeviewModule.forRoot(), RouterTestingModule.withRoutes([]), ArtemisSharedModule, FormDateTimePickerModule],
            declarations: [LectureAttachmentsComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        data: {
                            subscribe: (fn: (value: any) => void) =>
                                fn({
                                    lecture,
                                }),
                        },
                    },
                },
                {
                    provide: AttachmentService,
                    useValue: {
                        create() {
                            return {
                                subscribe: (fn: (value: any) => void) =>
                                    fn({
                                        body: newAttachment,
                                    }),
                            };
                        },
                        findAllByLectureId() {
                            return {
                                subscribe: (fn: (value: any) => void) =>
                                    fn({
                                        body: [...attachments],
                                    }),
                            };
                        },
                    },
                },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LectureAttachmentsComponent);
                comp = fixture.componentInstance;
                fileUploaderService = TestBed.inject(FileUploaderService);
            });
    });

    afterEach(() => {
        comp.attachments = [...attachments];
    });

    it('should accept file and add attachment to list', fakeAsync(() => {
        fixture.detectChanges();
        const addAttachmentButton = fixture.debugElement.query(By.css('#add-attachment'));
        expect(comp.attachmentToBeCreated).to.be.undefined;
        expect(addAttachmentButton).to.exist;
        addAttachmentButton.nativeElement.click();
        fixture.detectChanges();
        const fakeBlob = new Blob([''], { type: 'application/pdf' });
        fakeBlob['name'] = 'Test-File.pdf';
        comp.attachmentFile = fakeBlob;
        const uploadAttachmentButton = fixture.debugElement.query(By.css('#upload-attachment'));
        expect(uploadAttachmentButton).to.exist;
        expect(comp.attachmentToBeCreated).to.exist;
        comp.attachmentToBeCreated!.name = 'Test File Name';
        spyOn(fileUploaderService, 'uploadFile').and.returnValue(Promise.resolve({ path: 'test' }));
        uploadAttachmentButton.nativeElement.click();

        fixture.detectChanges();
        tick();
        expect(comp.attachments.length).to.equal(3);
    }));

    it('should not accept too large file', fakeAsync(() => {
        fixture.detectChanges();
        const addAttachmentButton = fixture.debugElement.query(By.css('#add-attachment'));
        expect(comp.attachmentToBeCreated).to.be.undefined;
        expect(addAttachmentButton).to.exist;
        addAttachmentButton.nativeElement.click();
        fixture.detectChanges();
        const fakeBlob = {};
        fakeBlob['name'] = 'Test-File.pdf';
        fakeBlob['size'] = 100000000000000000;
        comp.attachmentFile = fakeBlob as Blob;
        const uploadAttachmentButton = fixture.debugElement.query(By.css('#upload-attachment'));
        expect(uploadAttachmentButton).to.exist;
        expect(comp.attachmentToBeCreated).to.exist;
        comp.attachmentToBeCreated!.name = 'Test File Name';
        uploadAttachmentButton.nativeElement.click();
        tick();
        fixture.detectChanges();
        const fileAlert = fixture.debugElement.query(By.css('#too-large-file-alert'));
        expect(comp.attachments.length).to.equal(2);
        expect(fileAlert).to.exist;
    }));
});
