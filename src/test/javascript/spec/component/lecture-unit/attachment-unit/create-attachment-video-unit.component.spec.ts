import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { CreateAttachmentVideoUnitComponent } from 'app/lecture/manage/lecture-units/create-attachment-video-unit/create-attachment-video-unit.component';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/attachment-video-unit.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentVideoUnit } from 'app/entities/lecture-unit/attachmentVideoUnit.model';
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

describe('CreateAttachmentVideoUnitComponent', () => {
    let createAttachmentVideoUnitComponentFixture: ComponentFixture<CreateAttachmentVideoUnitComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule],
            providers: [
                MockProvider(AttachmentVideoUnitService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: { paramMap: convertToParamMap({ courseId: '1' }) },
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
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
            schemas: [],
        }).compileComponents();

        createAttachmentVideoUnitComponentFixture = TestBed.createComponent(CreateAttachmentVideoUnitComponent);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should upload file, send POST for attachment and post for attachment unit', fakeAsync(() => {
        const router: Router = TestBed.inject(Router);
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);

        const fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf' });

        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: 'test',
                description: 'lorem ipsum',
                releaseDate: dayjs().year(2010).month(3).date(5),
                version: 2,
                updateNotificationText: 'lorem ipsum',
                videoSource: 'https://live.rbg.tum.de',
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
        attachment.releaseDate = attachmentVideoUnitFormData.formProperties.releaseDate;
        attachment.name = attachmentVideoUnitFormData.formProperties.name;
        attachment.link = examplePath;

        const attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.description = attachmentVideoUnitFormData.formProperties.description;
        attachmentVideoUnit.attachment = attachment;
        attachmentVideoUnit.releaseDate = attachmentVideoUnitFormData.formProperties.releaseDate;
        attachmentVideoUnit.name = attachmentVideoUnitFormData.formProperties.name;
        attachmentVideoUnit.videoSource = attachmentVideoUnitFormData.formProperties.videoSource;

        const formData = new FormData();
        formData.append('file', fakeFile, attachmentVideoUnitFormData.fileProperties.fileName);
        formData.append('attachment', objectToJsonBlob(attachment));
        formData.append('attachmentVideoUnit', objectToJsonBlob(attachmentVideoUnit));

        const attachmentVideoUnitResponse: HttpResponse<AttachmentVideoUnit> = new HttpResponse({
            body: attachmentVideoUnit,
            status: 201,
        });
        const createAttachmentVideoUnitStub = jest.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(of(attachmentVideoUnitResponse));

        const navigateSpy = jest.spyOn(router, 'navigate');
        createAttachmentVideoUnitComponentFixture.detectChanges();

        const attachmentVideoUnitFormComponent = createAttachmentVideoUnitComponentFixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;
        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);

        createAttachmentVideoUnitComponentFixture.whenStable().then(() => {
            expect(createAttachmentVideoUnitStub).toHaveBeenCalledWith(formData, 1);
            expect(navigateSpy).toHaveBeenCalledOnce();
        });
    }));
});
