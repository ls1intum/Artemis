import { Component, EventEmitter, Input, Output } from '@angular/core';
import dayjs from 'dayjs/esm';
import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { AttachmentUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-unit-form/attachment-unit-form.component';
import { CreateAttachmentUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-attachment-unit/create-attachment-unit.component';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { HttpResponse } from '@angular/common/http';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { By } from '@angular/platform-browser';
import { objectToJsonBlob } from 'app/utils/blob-util';

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

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [AttachmentUnitFormStubComponent, LectureUnitLayoutStubComponent, CreateAttachmentUnitComponent],
            providers: [
                MockProvider(AttachmentUnitService),
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
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should upload file, send POST for attachment and post for attachment unit', fakeAsync(() => {
        const router: Router = TestBed.inject(Router);
        const attachmentUnitService = TestBed.inject(AttachmentUnitService);

        const fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf' });

        const attachmentUnitFormData: AttachmentUnitFormData = {
            formProperties: {
                name: 'test',
                description: 'lorem ipsum',
                releaseDate: dayjs().year(2010).month(3).date(5),
                version: 2,
                updateNotificationText: 'lorem ipsum',
            },
            fileProperties: {
                file: fakeFile,
                fileName: 'lorem ipsum',
            },
        };

        const examplePath = '/path/to/file';

        const attachment = new Attachment();
        attachment.version = 1;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.releaseDate = attachmentUnitFormData.formProperties.releaseDate;
        attachment.name = attachmentUnitFormData.formProperties.name;
        attachment.link = examplePath;

        const attachmentUnit = new AttachmentUnit();
        attachmentUnit.description = attachmentUnitFormData.formProperties.description;
        attachmentUnit.attachment = attachment;

        const formData = new FormData();
        formData.append('file', fakeFile, attachmentUnitFormData.fileProperties.fileName);
        formData.append('attachment', objectToJsonBlob(attachment));
        formData.append('attachmentUnit', objectToJsonBlob(attachmentUnit));

        const attachmentUnitResponse: HttpResponse<AttachmentUnit> = new HttpResponse({
            body: attachmentUnit,
            status: 201,
        });
        const createAttachmentUnitStub = jest.spyOn(attachmentUnitService, 'create').mockReturnValue(of(attachmentUnitResponse));

        const navigateSpy = jest.spyOn(router, 'navigate');
        createAttachmentUnitComponentFixture.detectChanges();

        const attachmentUnitFormStubComponent: AttachmentUnitFormStubComponent = createAttachmentUnitComponentFixture.debugElement.query(
            By.directive(AttachmentUnitFormStubComponent),
        ).componentInstance;
        attachmentUnitFormStubComponent.formSubmitted.emit(attachmentUnitFormData);

        createAttachmentUnitComponentFixture.whenStable().then(() => {
            expect(createAttachmentUnitStub).toHaveBeenCalledWith(formData, 1);
            expect(navigateSpy).toHaveBeenCalledOnce();
        });
    }));
});
