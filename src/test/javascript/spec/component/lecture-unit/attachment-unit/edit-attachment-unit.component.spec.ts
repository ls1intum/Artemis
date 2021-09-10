import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import dayjs from 'dayjs';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { AttachmentUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-unit-form/attachment-unit-form.component';
import { AttachmentService } from 'app/lecture/attachment.service';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { EditAttachmentUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/edit-attachment-unit/edit-attachment-unit.component';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';

chai.use(sinonChai);
const expect = chai.expect;

@Component({ selector: 'jhi-attachment-unit-form', template: '' })
class AttachmentUnitFormStubComponent {
    errorMessage: string;
    @Input() isEditMode = false;
    @Input() formData: AttachmentUnitFormData;
    @Output() formSubmitted: EventEmitter<AttachmentUnitFormData> = new EventEmitter<AttachmentUnitFormData>();

    setFileUploadError(errorMessage: string) {
        this.errorMessage = errorMessage;
    }
}

@Component({ selector: 'jhi-lecture-unit-layout', template: '<ng-content></ng-content>' })
class LectureUnitLayoutStubComponent {
    @Input()
    isLoading = false;
}

describe('EditAttachmentUnitComponent', () => {
    let editAttachmentUnitComponentFixture: ComponentFixture<EditAttachmentUnitComponent>;
    let editAttachmentUnitComponent: EditAttachmentUnitComponent;
    const sandbox = sinon.createSandbox();
    let fileUploadService;
    let attachmentService;
    let attachmentUnitService;
    let router: Router;
    let uploadFileStub: sinon.SinonStub;
    let updateAttachmentStub: sinon.SinonStub;
    let updateAttachmentUnitStub: sinon.SinonStub;
    let attachment: Attachment;
    let attachmentUnit: AttachmentUnit;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [AttachmentUnitFormStubComponent, LectureUnitLayoutStubComponent, EditAttachmentUnitComponent],
            providers: [
                MockProvider(AttachmentService),
                MockProvider(AttachmentUnitService),
                MockProvider(FileUploaderService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        paramMap: of({
                            get: (key: string) => {
                                switch (key) {
                                    case 'attachmentUnitId':
                                        return 1;
                                }
                            },
                        }),
                        parent: {
                            parent: {
                                paramMap: of({
                                    get: (key: string) => {
                                        switch (key) {
                                            case 'lectureId':
                                                return 1;
                                        }
                                    },
                                }),
                            },
                        },
                    },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                editAttachmentUnitComponentFixture = TestBed.createComponent(EditAttachmentUnitComponent);
                editAttachmentUnitComponent = editAttachmentUnitComponentFixture.componentInstance;
                router = TestBed.inject(Router);
                fileUploadService = TestBed.inject(FileUploaderService);
                attachmentService = TestBed.inject(AttachmentService);
                attachmentUnitService = TestBed.inject(AttachmentUnitService);

                attachment = new Attachment();
                attachment.id = 1;
                attachment.version = 1;
                attachment.attachmentType = AttachmentType.FILE;
                attachment.releaseDate = dayjs().year(2010).month(3).day(5);
                attachment.uploadDate = dayjs().year(2010).month(3).day(5);
                attachment.name = 'test';
                attachment.link = '/path/to/file';

                attachmentUnit = new AttachmentUnit();
                attachmentUnit.id = 1;
                attachmentUnit.description = 'lorem ipsum';
                attachmentUnit.attachment = attachment;
                sandbox.stub(attachmentUnitService, 'findById').returns(
                    of(
                        new HttpResponse({
                            body: attachmentUnit,
                            status: 200,
                        }),
                    ),
                );
                uploadFileStub = sandbox.stub(fileUploadService, 'uploadFile');
                updateAttachmentUnitStub = sandbox.stub(attachmentUnitService, 'update');
                updateAttachmentStub = sandbox.stub(attachmentService, 'update');
            });
    });

    afterEach(function () {
        sandbox.restore();
    });

    it('should initialize', () => {
        editAttachmentUnitComponentFixture.detectChanges();
        expect(editAttachmentUnitComponent).to.be.ok;
    });

    it('should set form data correctly', () => {
        editAttachmentUnitComponentFixture.detectChanges();
        const attachmentUnitFormStubComponent: AttachmentUnitFormStubComponent = editAttachmentUnitComponentFixture.debugElement.query(
            By.directive(AttachmentUnitFormStubComponent),
        ).componentInstance;

        expect(attachmentUnitFormStubComponent.formData.formProperties.name).to.equal(attachment.name);
        expect(attachmentUnitFormStubComponent.formData.formProperties.releaseDate).to.equal(attachment.releaseDate);
        expect(attachmentUnitFormStubComponent.formData.formProperties.updateNotificationText).to.be.undefined;
        expect(attachmentUnitFormStubComponent.formData.formProperties.version).to.equal(1);
        expect(attachmentUnitFormStubComponent.formData.formProperties.description).to.equal(attachmentUnit.description);
        expect(attachmentUnitFormStubComponent.formData.fileProperties.fileName).to.equal(attachment.link);
        expect(attachmentUnitFormStubComponent.formData.fileProperties.file).to.be.undefined;
    });

    it('should upload file before performing update when file HAS changed', () => {
        editAttachmentUnitComponentFixture.detectChanges();
        const attachmentUnitFormStubComponent: AttachmentUnitFormStubComponent = editAttachmentUnitComponentFixture.debugElement.query(
            By.directive(AttachmentUnitFormStubComponent),
        ).componentInstance;

        const fakeBlob = new Blob([''], { type: 'application/pdf' });
        fakeBlob['name'] = 'Test-File.pdf';
        const formData: AttachmentUnitFormData = {
            formProperties: {
                name: attachment.name,
                description: attachmentUnit.description,
                releaseDate: attachment.releaseDate,
                version: 1,
                updateNotificationText: 'UPDATED FILE',
            },
            fileProperties: {
                file: fakeBlob,
                fileName: 'updated file',
            },
        };

        uploadFileStub.resolves({ path: '/path/to/new/file' });
        attachmentUnitFormStubComponent.formSubmitted.emit(formData);
        expect(uploadFileStub).to.have.been.calledWith(fakeBlob, formData.fileProperties.fileName, { keepFileName: true });
    });

    it('should not update file before performing update when file HAS NOT changed', () => {
        editAttachmentUnitComponentFixture.detectChanges();
        const attachmentUnitFormStubComponent: AttachmentUnitFormStubComponent = editAttachmentUnitComponentFixture.debugElement.query(
            By.directive(AttachmentUnitFormStubComponent),
        ).componentInstance;

        const fakeBlob = new Blob([''], { type: 'application/pdf' });
        fakeBlob['name'] = 'Test-File.pdf';
        const formData: AttachmentUnitFormData = {
            formProperties: {
                name: attachment.name,
                description: attachmentUnit.description,
                releaseDate: attachment.releaseDate,
                version: 1,
                updateNotificationText: 'UPDATED FILE',
            },
            fileProperties: {
                file: undefined,
                fileName: undefined,
            },
        };

        attachmentUnitFormStubComponent.formSubmitted.emit(formData);
        expect(uploadFileStub).to.not.have.been.called;
    });

    it('should set file file upload error on form', fakeAsync(() => {
        editAttachmentUnitComponentFixture.detectChanges();
        const attachmentUnitFormStubComponent: AttachmentUnitFormStubComponent = editAttachmentUnitComponentFixture.debugElement.query(
            By.directive(AttachmentUnitFormStubComponent),
        ).componentInstance;

        const fakeBlob = new Blob([''], { type: 'application/pdf' });
        fakeBlob['name'] = 'Test-File.pdf';
        const formData: AttachmentUnitFormData = {
            formProperties: {
                name: attachment.name,
                description: attachmentUnit.description,
                releaseDate: attachment.releaseDate,
                version: 1,
                updateNotificationText: 'UPDATED FILE',
            },
            fileProperties: {
                file: fakeBlob,
                fileName: 'updated filename',
            },
        };

        const performUpdateSpy = sinon.spy(editAttachmentUnitComponent, 'performUpdate');
        uploadFileStub.rejects(new Error('some error'));
        attachmentUnitFormStubComponent.formSubmitted.emit(formData);
        editAttachmentUnitComponentFixture.whenStable().then(() => {
            expect(attachmentUnitFormStubComponent.errorMessage).to.equal('some error');
            expect(performUpdateSpy).to.not.have.been.called;
            performUpdateSpy.restore();
        });
    }));

    it('should send PUT request for attachment and attachment unit upon form submission and navigate', fakeAsync(() => {
        editAttachmentUnitComponentFixture.detectChanges();
        const attachmentUnitFormStubComponent: AttachmentUnitFormStubComponent = editAttachmentUnitComponentFixture.debugElement.query(
            By.directive(AttachmentUnitFormStubComponent),
        ).componentInstance;

        const fakeBlob = new Blob([''], { type: 'application/pdf' });
        fakeBlob['name'] = 'Test-File.pdf';
        const formData: AttachmentUnitFormData = {
            formProperties: {
                name: attachment.name,
                description: attachmentUnit.description,
                releaseDate: attachment.releaseDate,
                version: 1,
                updateNotificationText: 'UPDATED FILE',
            },
            fileProperties: {
                file: fakeBlob,
                fileName: 'updated filename',
            },
        };

        uploadFileStub.resolves({ path: '/path/to/new/file' });
        updateAttachmentStub.returns(of(new Attachment()));
        updateAttachmentUnitStub.returns(of(new AttachmentUnit()));
        const navigateSpy = sinon.spy(router, 'navigate');

        attachmentUnitFormStubComponent.formSubmitted.emit(formData);

        editAttachmentUnitComponentFixture.whenStable().then(() => {
            expect(navigateSpy).to.have.been.calledOnce;
            expect(updateAttachmentUnitStub).to.have.been.calledOnce;
            expect(updateAttachmentStub).to.have.been.calledOnce;
            navigateSpy.restore();
        });
    }));
});
