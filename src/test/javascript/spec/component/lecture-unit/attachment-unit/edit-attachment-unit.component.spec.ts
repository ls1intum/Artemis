import dayjs from 'dayjs/esm';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { AttachmentUnitFormComponent, AttachmentUnitFormData } from 'app/lecture/manage/lecture-units/attachment-unit-form/attachment-unit-form.component';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/attachment-video-unit.service';
import { EditAttachmentUnitComponent } from 'app/lecture/manage/lecture-units/edit-attachment-unit/edit-attachment-unit.component';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentVideoUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { objectToJsonBlob } from 'app/shared/util/blob-util';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { ProfileService } from '../../../../../../main/webapp/app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';

describe('EditAttachmentUnitComponent', () => {
    let fixture: ComponentFixture<EditAttachmentUnitComponent>;

    let attachmentUnitService;
    let router: Router;
    let navigateSpy: jest.SpyInstance;
    let updateAttachmentUnitSpy: jest.SpyInstance;
    let attachment: Attachment;
    let attachmentUnit: AttachmentVideoUnit;
    let baseFormData: FormData;
    let fakeFile: File;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule],
            providers: [
                MockProvider(AttachmentVideoUnitService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                { provide: ProfileService, useClass: MockProfileService },
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
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
            schemas: [],
        }).compileComponents();
        fixture = TestBed.createComponent(EditAttachmentUnitComponent);
        router = TestBed.inject(Router);
        attachmentUnitService = TestBed.inject(AttachmentVideoUnitService);

        attachment = new Attachment();
        attachment.id = 1;
        attachment.version = 1;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.releaseDate = dayjs().year(2010).month(3).date(5);
        attachment.uploadDate = dayjs().year(2010).month(3).date(5);
        attachment.name = 'test';
        attachment.link = '/path/to/file';

        attachmentUnit = new AttachmentVideoUnit();
        attachmentUnit.id = 1;
        attachmentUnit.name = 'test';
        attachmentUnit.description = 'lorem ipsum';
        attachmentUnit.attachment = attachment;
        attachmentUnit.releaseDate = dayjs().year(2010).month(3).date(5);
        attachmentUnit.videoSource = 'https://live.rbg.tum.de';

        fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf' });

        baseFormData = new FormData();
        baseFormData.append('file', fakeFile, 'updated file');
        baseFormData.append('attachment', objectToJsonBlob(attachment));
        baseFormData.append('attachmentVideoUnit', objectToJsonBlob(attachmentUnit));

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
        const attachmentUnitFormComponent: AttachmentUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentUnitFormComponent)).componentInstance;

        expect(attachmentUnitFormComponent.formData()?.formProperties.name).toEqual(attachmentUnit.name);
        expect(attachmentUnitFormComponent.formData()?.formProperties.releaseDate).toEqual(attachmentUnit.releaseDate);
        expect(attachmentUnitFormComponent.formData()?.formProperties.updateNotificationText).toBeUndefined();
        expect(attachmentUnitFormComponent.formData()?.formProperties.version).toBe(1);
        expect(attachmentUnitFormComponent.formData()?.formProperties.description).toEqual(attachmentUnit.description);
        expect(attachmentUnitFormComponent.formData()?.formProperties.videoSource).toEqual(attachmentUnit.videoSource);
        expect(attachmentUnitFormComponent.formData()?.fileProperties.fileName).toEqual(attachment.link);
        expect(attachmentUnitFormComponent.formData()?.fileProperties.file).toBeUndefined();
    });

    it('should update attachment unit with file change without notification', async () => {
        fixture.detectChanges();
        const attachmentUnitFormComponent: AttachmentUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentUnitFormComponent)).componentInstance;

        const fileName = 'updated file';

        const attachmentUnitFormData: AttachmentUnitFormData = {
            formProperties: {
                name: attachmentUnit.name,
                description: attachmentUnit.description,
                releaseDate: attachmentUnit.releaseDate,
                videoSource: attachmentUnit.videoSource,
                version: 1,
                updateNotificationText: undefined,
            },
            fileProperties: {
                file: fakeFile,
                fileName,
            },
        };

        updateAttachmentUnitSpy.mockReturnValue(of({ body: attachmentUnit, status: 200 }));
        attachmentUnitFormComponent.formSubmitted.emit(attachmentUnitFormData);
        fixture.detectChanges();

        expect(updateAttachmentUnitSpy).toHaveBeenCalledWith(1, 1, baseFormData, undefined);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should update attachment unit with file change with notification', async () => {
        fixture.detectChanges();
        const attachmentUnitFormComponent: AttachmentUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentUnitFormComponent)).componentInstance;

        const fileName = 'updated file';

        const notification = 'test notification';

        const attachmentUnitFormData: AttachmentUnitFormData = {
            formProperties: {
                name: attachmentUnit.name,
                description: attachmentUnit.description,
                releaseDate: attachmentUnit.releaseDate,
                version: 1,
                updateNotificationText: notification,
            },
            fileProperties: {
                file: fakeFile,
                fileName,
            },
        };

        updateAttachmentUnitSpy.mockReturnValue(of({ body: attachmentUnit, status: 200 }));
        attachmentUnitFormComponent.formSubmitted.emit(attachmentUnitFormData);
        fixture.detectChanges();

        expect(updateAttachmentUnitSpy).toHaveBeenCalledWith(1, 1, baseFormData, notification);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should update attachment unit without file change without notification', async () => {
        fixture.detectChanges();
        const attachmentUnitFormComponent: AttachmentUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentUnitFormComponent)).componentInstance;

        const attachmentUnitFormData: AttachmentUnitFormData = {
            formProperties: {
                name: attachmentUnit.name,
                description: attachmentUnit.description,
                releaseDate: attachmentUnit.releaseDate,
                videoSource: attachmentUnit.videoSource,
                version: 1,
                updateNotificationText: undefined,
            },
            fileProperties: {},
        };

        const formData = new FormData();
        formData.append('attachment', objectToJsonBlob(attachment));
        formData.append('attachmentVideoUnit', objectToJsonBlob(attachmentUnit));

        updateAttachmentUnitSpy.mockReturnValue(of({ body: attachmentUnit, status: 200 }));
        attachmentUnitFormComponent.formSubmitted.emit(attachmentUnitFormData);
        fixture.detectChanges();

        expect(updateAttachmentUnitSpy).toHaveBeenCalledWith(1, 1, formData, undefined);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });
});
