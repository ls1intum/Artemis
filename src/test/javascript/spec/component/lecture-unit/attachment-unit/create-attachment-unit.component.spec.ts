import { Component, EventEmitter, Input, Output } from '@angular/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import dayjs from 'dayjs';
import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { AttachmentUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-unit-form/attachment-unit-form.component';
import { CreateAttachmentUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-attachment-unit/create-attachment-unit.component';
import { AttachmentService } from 'app/lecture/attachment.service';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { HttpResponse } from '@angular/common/http';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { By } from '@angular/platform-browser';

chai.use(sinonChai);
const expect = chai.expect;

@Component({ selector: 'jhi-attachment-unit-form', template: '' })
class AttachmentUnitFormStubComponent {
    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<AttachmentUnitFormData> = new EventEmitter<AttachmentUnitFormData>();
}

@Component({ selector: 'jhi-lecture-unit-layout', template: '<ng-content></ng-content>' })
class LectureUnitLayoutStubComponent {
    @Input()
    isLoading = false;
}

describe('CreateAttachmentUnitComponent', () => {
    let createAttachmentUnitComponentFixture: ComponentFixture<CreateAttachmentUnitComponent>;
    let createAttachmentUnitComponent: CreateAttachmentUnitComponent;
    const sandbox = sinon.createSandbox();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [AttachmentUnitFormStubComponent, LectureUnitLayoutStubComponent, CreateAttachmentUnitComponent],
            providers: [
                MockProvider(AttachmentService),
                MockProvider(AttachmentUnitService),
                MockProvider(FileUploaderService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
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
                                parent: {
                                    paramMap: of({
                                        get: (key: string) => {
                                            switch (key) {
                                                case 'courseId':
                                                    return 1;
                                            }
                                        },
                                    }),
                                },
                            },
                        },
                    },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                createAttachmentUnitComponentFixture = TestBed.createComponent(CreateAttachmentUnitComponent);
                createAttachmentUnitComponent = createAttachmentUnitComponentFixture.componentInstance;
            });
    });

    afterEach(function () {
        sandbox.restore();
    });

    it('should initialize', () => {
        createAttachmentUnitComponentFixture.detectChanges();
        expect(createAttachmentUnitComponent).to.be.ok;
    });

    it('should upload file, send POST for attachment and post for attachment unit', fakeAsync(() => {
        const router: Router = TestBed.inject(Router);
        const fileUploadService = TestBed.inject(FileUploaderService);
        const attachmentService = TestBed.inject(AttachmentService);
        const attachmentUnitService = TestBed.inject(AttachmentUnitService);

        const fakeBlob = new Blob([''], { type: 'application/pdf' });
        fakeBlob['name'] = 'Test-File.pdf';

        const formData: AttachmentUnitFormData = {
            formProperties: {
                name: 'test',
                description: 'lorem ipsum',
                releaseDate: dayjs().year(2010).month(3).date(5),
                version: 2,
                updateNotificationText: 'lorem ipsum',
            },
            fileProperties: {
                file: fakeBlob,
                fileName: 'lorem ipsum',
            },
        };

        const examplePath = '/path/to/file';
        const uploadFileStub = sandbox.stub(fileUploadService, 'uploadFile').resolves({ path: examplePath });

        const attachmentUnit = new AttachmentUnit();
        attachmentUnit.description = formData.formProperties.description;

        const attachmentUnitResponse: HttpResponse<AttachmentUnit> = new HttpResponse({
            body: attachmentUnit,
            status: 201,
        });
        const createAttachmentUnitStub = sandbox.stub(attachmentUnitService, 'create').returns(of(attachmentUnitResponse));

        const attachment = new Attachment();
        attachment.version = 1;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.releaseDate = formData.formProperties.releaseDate;
        attachment.name = formData.formProperties.name;
        attachment.link = examplePath;

        const attachmentResponse: HttpResponse<Attachment> = new HttpResponse({
            body: attachment,
            status: 201,
        });
        const createAttachmentStub = sandbox.stub(attachmentService, 'create').returns(of(attachmentResponse));
        const navigateSpy = sinon.spy(router, 'navigate');
        createAttachmentUnitComponentFixture.detectChanges();

        const attachmentUnitFormStubComponent: AttachmentUnitFormStubComponent = createAttachmentUnitComponentFixture.debugElement.query(
            By.directive(AttachmentUnitFormStubComponent),
        ).componentInstance;
        attachmentUnitFormStubComponent.formSubmitted.emit(formData);

        createAttachmentUnitComponentFixture.whenStable().then(() => {
            expect(uploadFileStub).to.have.been.calledWith(formData.fileProperties.file, formData.fileProperties.fileName, { keepFileName: true });
            expect(createAttachmentUnitStub).to.have.been.calledWith(attachmentUnit, 1);
            const attachmentArgument: Attachment = createAttachmentStub.getCall(0).args[0];
            expect(attachmentArgument.name).to.equal(attachment.name);
            expect(attachmentArgument.releaseDate).to.equal(attachment.releaseDate);
            expect(attachmentArgument.version).to.equal(attachment.version);
            expect(attachmentArgument.link).to.equal(attachment.link);
            expect(attachmentArgument.attachmentUnit).to.deep.equal(attachmentUnit);
            expect(navigateSpy).to.have.been.calledOnce;
            navigateSpy.restore();
        });
    }));
});
