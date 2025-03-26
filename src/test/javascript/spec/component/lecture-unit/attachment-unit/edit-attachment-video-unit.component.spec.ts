import dayjs from 'dayjs/esm';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/attachment-video-unit.service';
import { EditAttachmentVideoUnitComponent } from 'app/lecture/manage/lecture-units/edit-attachment-video-unit/edit-attachment-video-unit.component';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentVideoUnit } from 'app/entities/lecture-unit/attachmentVideoUnit.model';
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

describe('EditAttachmentVideoUnitComponent', () => {
    let fixture: ComponentFixture<EditAttachmentVideoUnitComponent>;

    let attachmentVideoUnitService;
    let router: Router;
    let navigateSpy: jest.SpyInstance;
    let updateAttachmentVideoUnitSpy: jest.SpyInstance;
    let attachment: Attachment;
    let attachmentVideoUnit: AttachmentVideoUnit;
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
        fixture = TestBed.createComponent(EditAttachmentVideoUnitComponent);
        router = TestBed.inject(Router);
        attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);

        attachment = new Attachment();
        attachment.id = 1;
        attachment.version = 1;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.releaseDate = dayjs().year(2010).month(3).date(5);
        attachment.uploadDate = dayjs().year(2010).month(3).date(5);
        attachment.name = 'test';
        attachment.link = '/path/to/file';

        attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.id = 1;
        attachmentVideoUnit.name = 'test';
        attachmentVideoUnit.description = 'lorem ipsum';
        attachmentVideoUnit.attachment = attachment;
        attachmentVideoUnit.releaseDate = dayjs().year(2010).month(3).date(5);
        attachmentVideoUnit.videoSource = 'https://live.rbg.tum.de';

        fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf' });

        baseFormData = new FormData();
        baseFormData.append('file', fakeFile, 'updated file');
        baseFormData.append('attachment', objectToJsonBlob(attachment));
        baseFormData.append('attachmentVideoUnit', objectToJsonBlob(attachmentVideoUnit));

        jest.spyOn(attachmentVideoUnitService, 'findById').mockReturnValue(
            of(
                new HttpResponse({
                    body: attachmentVideoUnit,
                    status: 200,
                }),
            ),
        );
        updateAttachmentVideoUnitSpy = jest.spyOn(attachmentVideoUnitService, 'update');
        navigateSpy = jest.spyOn(router, 'navigate');
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set form data correctly', async () => {
        fixture.detectChanges();
        const attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;

        expect(attachmentVideoUnitFormComponent.formData()?.formProperties.name).toEqual(attachmentVideoUnit.name);
        expect(attachmentVideoUnitFormComponent.formData()?.formProperties.releaseDate).toEqual(attachmentVideoUnit.releaseDate);
        expect(attachmentVideoUnitFormComponent.formData()?.formProperties.updateNotificationText).toBeUndefined();
        expect(attachmentVideoUnitFormComponent.formData()?.formProperties.version).toBe(1);
        expect(attachmentVideoUnitFormComponent.formData()?.formProperties.description).toEqual(attachmentVideoUnit.description);
        expect(attachmentVideoUnitFormComponent.formData()?.formProperties.videoSource).toEqual(attachmentVideoUnit.videoSource);
        expect(attachmentVideoUnitFormComponent.formData()?.fileProperties.fileName).toEqual(attachment.link);
        expect(attachmentVideoUnitFormComponent.formData()?.fileProperties.file).toBeUndefined();
    });

    it('should update attachment unit with file change without notification', async () => {
        fixture.detectChanges();
        const attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;

        const fileName = 'updated file';

        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: attachmentVideoUnit.name,
                description: attachmentVideoUnit.description,
                releaseDate: attachmentVideoUnit.releaseDate,
                videoSource: attachmentVideoUnit.videoSource,
                version: 1,
                updateNotificationText: undefined,
            },
            fileProperties: {
                file: fakeFile,
                fileName,
            },
        };

        updateAttachmentVideoUnitSpy.mockReturnValue(of({ body: attachmentVideoUnit, status: 200 }));
        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
        fixture.detectChanges();

        expect(updateAttachmentVideoUnitSpy).toHaveBeenCalledWith(1, 1, baseFormData, undefined);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should update attachment unit with file change with notification', async () => {
        fixture.detectChanges();
        const attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;

        const fileName = 'updated file';

        const notification = 'test notification';

        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: attachmentVideoUnit.name,
                description: attachmentVideoUnit.description,
                releaseDate: attachmentVideoUnit.releaseDate,
                version: 1,
                updateNotificationText: notification,
            },
            fileProperties: {
                file: fakeFile,
                fileName,
            },
        };

        updateAttachmentVideoUnitSpy.mockReturnValue(of({ body: attachmentVideoUnit, status: 200 }));
        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
        fixture.detectChanges();

        expect(updateAttachmentVideoUnitSpy).toHaveBeenCalledWith(1, 1, baseFormData, notification);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should update attachment unit without file change without notification', async () => {
        fixture.detectChanges();
        const attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;

        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: attachmentVideoUnit.name,
                description: attachmentVideoUnit.description,
                releaseDate: attachmentVideoUnit.releaseDate,
                videoSource: attachmentVideoUnit.videoSource,
                version: 1,
                updateNotificationText: undefined,
            },
            fileProperties: {},
        };

        const formData = new FormData();
        formData.append('attachment', objectToJsonBlob(attachment));
        formData.append('attachmentVideoUnit', objectToJsonBlob(attachmentVideoUnit));

        updateAttachmentVideoUnitSpy.mockReturnValue(of({ body: attachmentVideoUnit, status: 200 }));
        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
        fixture.detectChanges();

        expect(updateAttachmentVideoUnitSpy).toHaveBeenCalledWith(1, 1, formData, undefined);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });
});
