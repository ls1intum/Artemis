import dayjs from 'dayjs/esm';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { AttachmentUnitFormComponent, AttachmentUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-unit-form/attachment-unit-form.component';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { EditAttachmentUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/edit-attachment-unit/edit-attachment-unit.component';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { objectToJsonBlob } from 'app/utils/blob-util';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { ArtemisTestModule } from '../../../test.module';

describe('EditAttachmentUnitComponent', () => {
    let fixture: ComponentFixture<EditAttachmentUnitComponent>;

    let attachmentUnitService;
    let router: Router;
    let navigateSpy: jest.SpyInstance;
    let updateAttachmentUnitSpy: jest.SpyInstance;
    let attachment: Attachment;
    let attachmentUnit: AttachmentUnit;
    let baseFormData: FormData;
    let fakeFile: File;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, OwlNativeDateTimeModule],
            providers: [
                MockProvider(AttachmentUnitService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({ courseId: 1 }),
                        },
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
        }).compileComponents();
        fixture = TestBed.createComponent(EditAttachmentUnitComponent);
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

        fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf' });

        baseFormData = new FormData();
        baseFormData.append('file', fakeFile, 'updated file');
        baseFormData.append('attachment', objectToJsonBlob(attachment));
        baseFormData.append('attachmentUnit', objectToJsonBlob(attachmentUnit));

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

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set form data correctly', async () => {
        fixture.detectChanges();
        const attachmentUnitFormStubComponent: AttachmentUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentUnitFormComponent)).componentInstance;

        expect(attachmentUnitFormStubComponent.formData()?.formProperties.name).toEqual(attachment.name);
        expect(attachmentUnitFormStubComponent.formData()?.formProperties.releaseDate).toEqual(attachment.releaseDate);
        expect(attachmentUnitFormStubComponent.formData()?.formProperties.updateNotificationText).toBeUndefined();
        expect(attachmentUnitFormStubComponent.formData()?.formProperties.version).toBe(1);
        expect(attachmentUnitFormStubComponent.formData()?.formProperties.description).toEqual(attachmentUnit.description);
        expect(attachmentUnitFormStubComponent.formData()?.fileProperties.fileName).toEqual(attachment.link);
        expect(attachmentUnitFormStubComponent.formData()?.fileProperties.file).toBeUndefined();
    });

    it('should update attachment unit with file change without notification', async () => {
        fixture.detectChanges();
        const attachmentUnitFormStubComponent: AttachmentUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentUnitFormComponent)).componentInstance;

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
                file: fakeFile,
                fileName,
            },
        };

        updateAttachmentUnitSpy.mockReturnValue(of({ body: attachmentUnit, status: 200 }));
        attachmentUnitFormStubComponent.formSubmitted.emit(attachmentUnitFormData);
        fixture.detectChanges();

        expect(updateAttachmentUnitSpy).toHaveBeenCalledWith(1, 1, baseFormData, undefined);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should update attachment unit with file change with notification', async () => {
        fixture.detectChanges();
        const attachmentUnitFormStubComponent: AttachmentUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentUnitFormComponent)).componentInstance;

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
                file: fakeFile,
                fileName,
            },
        };

        updateAttachmentUnitSpy.mockReturnValue(of({ body: attachmentUnit, status: 200 }));
        attachmentUnitFormStubComponent.formSubmitted.emit(attachmentUnitFormData);
        fixture.detectChanges();

        expect(updateAttachmentUnitSpy).toHaveBeenCalledWith(1, 1, baseFormData, notification);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should update attachment unit without file change without notification', async () => {
        fixture.detectChanges();
        const attachmentUnitFormStubComponent: AttachmentUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentUnitFormComponent)).componentInstance;

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
        formData.append('attachment', objectToJsonBlob(attachment));
        formData.append('attachmentUnit', objectToJsonBlob(attachmentUnit));

        updateAttachmentUnitSpy.mockReturnValue(of({ body: attachmentUnit, status: 200 }));
        attachmentUnitFormStubComponent.formSubmitted.emit(attachmentUnitFormData);
        fixture.detectChanges();

        expect(updateAttachmentUnitSpy).toHaveBeenCalledWith(1, 1, formData, undefined);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });
});
