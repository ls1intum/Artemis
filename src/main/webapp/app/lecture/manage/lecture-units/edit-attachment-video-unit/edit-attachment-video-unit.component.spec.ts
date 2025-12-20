import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from '../attachment-video-unit-form/attachment-video-unit-form.component';
import { AttachmentVideoUnitService } from '../services/attachment-video-unit.service';
import { EditAttachmentVideoUnitComponent } from './edit-attachment-video-unit.component';
import { AttachmentVideoUnit } from '../../../shared/entities/lecture-unit/attachmentVideoUnit.model';
import { Attachment, AttachmentType } from '../../../shared/entities/attachment.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';

import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AlertService } from 'app/shared/service/alert.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { objectToJsonBlob } from 'app/shared/util/blob-util';

describe('EditAttachmentVideoUnitComponent', () => {
    let fixture: ComponentFixture<EditAttachmentVideoUnitComponent>;
    let attachmentVideoUnitService: AttachmentVideoUnitService;
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
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
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
        const attachmentVideoUnitForBlob = { ...attachmentVideoUnit, attachment: undefined };
        baseFormData.append('attachment', objectToJsonBlob(attachment));
        baseFormData.append('attachmentVideoUnit', objectToJsonBlob(attachmentVideoUnitForBlob));

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

        expect(attachmentVideoUnitFormComponent.formData()).toBeDefined();

        expect(attachmentVideoUnitFormComponent.formData()?.formProperties.name).toEqual(attachmentVideoUnit.name);
        expect(attachmentVideoUnitFormComponent.formData()?.formProperties.releaseDate).toEqual(attachmentVideoUnit.releaseDate);
        expect(attachmentVideoUnitFormComponent.formData()?.formProperties.updateNotificationText).toBeUndefined();
        expect(attachmentVideoUnitFormComponent.formData()?.formProperties.version).toBe(1);
        expect(attachmentVideoUnitFormComponent.formData()?.formProperties.description).toEqual(attachmentVideoUnit.description);
        expect(attachmentVideoUnitFormComponent.formData()?.formProperties.videoSource).toEqual(attachmentVideoUnit.videoSource);
        expect(attachmentVideoUnitFormComponent.formData()?.fileProperties.fileName).toEqual(attachment.link);
        expect(attachmentVideoUnitFormComponent.formData()?.fileProperties.file).toBeUndefined();
    });

    it('should update attachment video unit with file change without notification', async () => {
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

        expect(updateAttachmentVideoUnitSpy).toHaveBeenCalledWith(1, 1, expect.any(FormData), undefined);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should update attachment video unit with file change with notification', async () => {
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

        expect(updateAttachmentVideoUnitSpy).toHaveBeenCalledWith(1, 1, expect.any(FormData), notification);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should update attachment video unit without file change without notification', async () => {
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
        const attachmentVideoUnitForBlob = { ...attachmentVideoUnit, attachment: undefined };
        formData.append('attachment', objectToJsonBlob(attachment));
        formData.append('attachmentVideoUnit', objectToJsonBlob(attachmentVideoUnitForBlob));

        updateAttachmentVideoUnitSpy.mockReturnValue(of({ body: attachmentVideoUnit, status: 200 }));
        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
        fixture.detectChanges();

        expect(updateAttachmentVideoUnitSpy).toHaveBeenCalledWith(1, 1, expect.any(FormData), undefined);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });
});
