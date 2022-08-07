import dayjs from 'dayjs/esm';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { AttachmentUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-unit-form/attachment-unit-form.component';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { EditAttachmentUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/edit-attachment-unit/edit-attachment-unit.component';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { base64StringToBlob } from 'app/utils/blob-util';

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

    let attachmentUnitService;
    let router: Router;
    let navigateSpy: jest.SpyInstance;
    let updateAttachmentUnitSpy: jest.SpyInstance;
    let attachment: Attachment;
    let attachmentUnit: AttachmentUnit;
    let baseFormData: FormData;
    let fakeBlob: Blob;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [AttachmentUnitFormStubComponent, LectureUnitLayoutStubComponent, EditAttachmentUnitComponent],
            providers: [
                MockProvider(AttachmentUnitService),
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
                router = TestBed.inject(Router);
                attachmentUnitService = TestBed.inject(AttachmentUnitService);

                attachment = new Attachment();
                attachment.id = 1;
                attachment.version = 1;
                attachment.attachmentType = AttachmentType.FILE;
                attachment.releaseDate = dayjs().year(2010).month(3).date(5);
                attachment.uploadDate = dayjs().year(2010).month(3).date(5);
                attachment.name = 'test';
                attachment.link = '/path/to/file';

                attachmentUnit = new AttachmentUnit();
                attachmentUnit.id = 1;
                attachmentUnit.description = 'lorem ipsum';
                attachmentUnit.attachment = attachment;

                fakeBlob = new Blob([''], { type: 'application/pdf' });
                fakeBlob['name'] = 'Test-File.pdf';

                baseFormData = new FormData();
                baseFormData.append('file', fakeBlob, 'updated file');
                baseFormData.append('attachment', base64StringToBlob(Buffer.from(JSON.stringify(attachment)).toString('base64')));
                baseFormData.append('attachmentUnit', base64StringToBlob(Buffer.from(JSON.stringify(attachmentUnit)).toString('base64')));

                jest.spyOn(attachmentUnitService, 'findById').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: attachmentUnit,
                            status: 200,
                        }),
                    ),
                );
                updateAttachmentUnitSpy = jest.spyOn(attachmentUnitService, 'update');
                navigateSpy = jest.spyOn(router, 'navigate');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set form data correctly', () => {
        editAttachmentUnitComponentFixture.detectChanges();
        const attachmentUnitFormStubComponent: AttachmentUnitFormStubComponent = editAttachmentUnitComponentFixture.debugElement.query(
            By.directive(AttachmentUnitFormStubComponent),
        ).componentInstance;

        expect(attachmentUnitFormStubComponent.formData.formProperties.name).toEqual(attachment.name);
        expect(attachmentUnitFormStubComponent.formData.formProperties.releaseDate).toEqual(attachment.releaseDate);
        expect(attachmentUnitFormStubComponent.formData.formProperties.updateNotificationText).toBeUndefined();
        expect(attachmentUnitFormStubComponent.formData.formProperties.version).toBe(1);
        expect(attachmentUnitFormStubComponent.formData.formProperties.description).toEqual(attachmentUnit.description);
        expect(attachmentUnitFormStubComponent.formData.fileProperties.fileName).toEqual(attachment.link);
        expect(attachmentUnitFormStubComponent.formData.fileProperties.file).toBeUndefined();
    });

    it('should update attachment unit with file change without notification', () => {
        editAttachmentUnitComponentFixture.detectChanges();
        const attachmentUnitFormStubComponent: AttachmentUnitFormStubComponent = editAttachmentUnitComponentFixture.debugElement.query(
            By.directive(AttachmentUnitFormStubComponent),
        ).componentInstance;

        const fileName = 'updated file';

        const attachmentUnitFormData: AttachmentUnitFormData = {
            formProperties: {
                name: attachment.name,
                description: attachmentUnit.description,
                releaseDate: attachment.releaseDate,
                version: 1,
                updateNotificationText: undefined,
            },
            fileProperties: {
                file: fakeBlob,
                fileName,
            },
        };

        updateAttachmentUnitSpy.mockReturnValue(of({ body: attachmentUnit, status: 200 }));
        attachmentUnitFormStubComponent.formSubmitted.emit(attachmentUnitFormData);
        editAttachmentUnitComponentFixture.detectChanges();

        expect(updateAttachmentUnitSpy).toHaveBeenCalledWith(1, 1, baseFormData, undefined);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should update attachment unit with file change with notification', () => {
        editAttachmentUnitComponentFixture.detectChanges();
        const attachmentUnitFormStubComponent: AttachmentUnitFormStubComponent = editAttachmentUnitComponentFixture.debugElement.query(
            By.directive(AttachmentUnitFormStubComponent),
        ).componentInstance;

        const fileName = 'updated file';

        const notification = 'test notification';

        const attachmentUnitFormData: AttachmentUnitFormData = {
            formProperties: {
                name: attachment.name,
                description: attachmentUnit.description,
                releaseDate: attachment.releaseDate,
                version: 1,
                updateNotificationText: notification,
            },
            fileProperties: {
                file: fakeBlob,
                fileName,
            },
        };

        updateAttachmentUnitSpy.mockReturnValue(of({ body: attachmentUnit, status: 200 }));
        attachmentUnitFormStubComponent.formSubmitted.emit(attachmentUnitFormData);
        editAttachmentUnitComponentFixture.detectChanges();

        expect(updateAttachmentUnitSpy).toHaveBeenCalledWith(1, 1, baseFormData, notification);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should update attachment unit without file change without notification', () => {
        editAttachmentUnitComponentFixture.detectChanges();
        const attachmentUnitFormStubComponent: AttachmentUnitFormStubComponent = editAttachmentUnitComponentFixture.debugElement.query(
            By.directive(AttachmentUnitFormStubComponent),
        ).componentInstance;

        const attachmentUnitFormData: AttachmentUnitFormData = {
            formProperties: {
                name: attachment.name,
                description: attachmentUnit.description,
                releaseDate: attachment.releaseDate,
                version: 1,
                updateNotificationText: undefined,
            },
            fileProperties: {},
        };

        const formData = new FormData();
        formData.append('attachment', base64StringToBlob(Buffer.from(JSON.stringify(attachment)).toString('base64')));
        formData.append('attachmentUnit', base64StringToBlob(Buffer.from(JSON.stringify(attachmentUnit)).toString('base64')));

        updateAttachmentUnitSpy.mockReturnValue(of({ body: attachmentUnit, status: 200 }));
        attachmentUnitFormStubComponent.formSubmitted.emit(attachmentUnitFormData);
        editAttachmentUnitComponentFixture.detectChanges();

        expect(updateAttachmentUnitSpy).toHaveBeenCalledWith(1, 1, formData, undefined);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });
});
