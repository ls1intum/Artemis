import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { AttachmentUnitFormComponent, AttachmentUnitFormData } from 'app/lecture/manage/lecture-units/attachment-unit-form/attachment-unit-form.component';
import { CreateAttachmentUnitComponent } from 'app/lecture/manage/lecture-units/create-attachment-unit/create-attachment-unit.component';
import { AttachmentUnitService } from 'app/lecture/manage/lecture-units/attachmentUnit.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { By } from '@angular/platform-browser';
import { objectToJsonBlob } from 'app/shared/util/blob-util';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { ProfileService } from '../../../../../../main/webapp/app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';

describe('CreateAttachmentUnitComponent', () => {
    let createAttachmentUnitComponentFixture: ComponentFixture<CreateAttachmentUnitComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule],
            providers: [
                MockProvider(AttachmentUnitService),
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

        createAttachmentUnitComponentFixture = TestBed.createComponent(CreateAttachmentUnitComponent);
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

        const attachmentUnitFormComponent = createAttachmentUnitComponentFixture.debugElement.query(By.directive(AttachmentUnitFormComponent)).componentInstance;
        attachmentUnitFormComponent.formSubmitted.emit(attachmentUnitFormData);

        createAttachmentUnitComponentFixture.whenStable().then(() => {
            expect(createAttachmentUnitStub).toHaveBeenCalledWith(formData, 1);
            expect(navigateSpy).toHaveBeenCalledOnce();
        });
    }));
});
