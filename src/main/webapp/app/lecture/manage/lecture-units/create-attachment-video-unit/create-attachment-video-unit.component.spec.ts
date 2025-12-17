import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { CreateAttachmentVideoUnitComponent } from 'app/lecture/manage/lecture-units/create-attachment-video-unit/create-attachment-video-unit.component';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { By } from '@angular/platform-browser';
import { objectToJsonBlob } from 'app/shared/util/blob-util';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

type StartTxReturn = ReturnType<AttachmentVideoUnitService['startTranscription']>;

describe('CreateAttachmentVideoUnitComponent', () => {
    let createAttachmentVideoUnitComponentFixture: ComponentFixture<CreateAttachmentVideoUnitComponent>;
    let lectureTranscriptionService: LectureTranscriptionService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule],
            providers: [
                MockProvider(AttachmentVideoUnitService),
                MockProvider(AlertService),
                MockProvider(LectureTranscriptionService),
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
        }).compileComponents();

        createAttachmentVideoUnitComponentFixture = TestBed.createComponent(CreateAttachmentVideoUnitComponent);
        lectureTranscriptionService = TestBed.inject(LectureTranscriptionService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should upload file, send POST for attachment and post for attachment video unit', fakeAsync(() => {
        const router: Router = TestBed.inject(Router);
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);

        const fakeFile = new File([''], 'Test-File.pdf', {
            type: 'application/pdf',
        });

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
            expect(createAttachmentVideoUnitStub).toHaveBeenCalledWith(expect.any(FormData), 1);
            expect(navigateSpy).toHaveBeenCalledOnce();
        });
    }));

    describe('transcription', () => {
        it('should create transcription when form is submitted with transcription properties', fakeAsync(() => {
            const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);

            const fakeFile = new File([''], 'Test-File.pdf', {
                type: 'application/pdf',
            });

            const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
                formProperties: {
                    name: 'test',
                    description: 'lorem ipsum',
                    releaseDate: dayjs().year(2010).month(3).date(5),
                },
                fileProperties: {
                    file: fakeFile,
                    fileName: 'lorem ipsum',
                },
                transcriptionProperties: {
                    videoTranscription: JSON.stringify({
                        language: 'en',
                        lectureUnitId: 1,
                    }),
                },
            };

            const attachmentVideoUnit = new AttachmentVideoUnit();
            attachmentVideoUnit.id = 1;

            const attachmentVideoUnitResponse: HttpResponse<AttachmentVideoUnit> = new HttpResponse({
                body: attachmentVideoUnit,
                status: 201,
            });
            const createAttachmentVideoUnitStub = jest.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(of(attachmentVideoUnitResponse));
            const createTranscriptionStub = jest.spyOn(lectureTranscriptionService, 'createTranscription').mockReturnValue(of(true));

            createAttachmentVideoUnitComponentFixture.detectChanges();

            const attachmentVideoUnitFormComponent = createAttachmentVideoUnitComponentFixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;
            attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);

            createAttachmentVideoUnitComponentFixture.whenStable().then(() => {
                expect(createAttachmentVideoUnitStub).toHaveBeenCalledOnce();
                expect(createTranscriptionStub).toHaveBeenCalledWith(
                    1,
                    attachmentVideoUnit.id,
                    JSON.parse(attachmentVideoUnitFormData.transcriptionProperties?.videoTranscription ?? ''),
                );
            });
        }));

        describe('transcript generation', () => {
            it('should start transcription when generateTranscript is true and playlistUrl is provided', fakeAsync(() => {
                const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
                const createSpy = jest.spyOn(attachmentVideoUnitService, 'create');
                const startTranscriptionSpy = jest.spyOn(attachmentVideoUnitService, 'startTranscription').mockReturnValue(of(undefined) as StartTxReturn);

                const attachmentVideoUnit = new AttachmentVideoUnit();
                attachmentVideoUnit.id = 1;
                attachmentVideoUnit.name = 'test';
                attachmentVideoUnit.videoSource = 'https://example.com/video.mp4';

                createSpy.mockReturnValue(of(new HttpResponse({ body: attachmentVideoUnit, status: 201 })));

                createAttachmentVideoUnitComponentFixture.detectChanges();

                const attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent = createAttachmentVideoUnitComponentFixture.debugElement.query(
                    By.directive(AttachmentVideoUnitFormComponent),
                ).componentInstance;

                const playlistUrl = 'https://example.com/playlist.m3u8';
                const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
                    formProperties: {
                        name: 'test',
                        description: 'test description',
                        releaseDate: dayjs(),
                        videoSource: 'https://example.com/video.mp4',
                        generateTranscript: true,
                    },
                    fileProperties: {},
                    playlistUrl: playlistUrl,
                };

                attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
                createAttachmentVideoUnitComponentFixture.detectChanges();
                tick();

                expect(startTranscriptionSpy).toHaveBeenCalledWith(1, attachmentVideoUnit.id, playlistUrl);
            }));

            it('should start transcription with videoSource when generateTranscript is true and no playlistUrl', fakeAsync(() => {
                const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
                const createSpy = jest.spyOn(attachmentVideoUnitService, 'create');
                const startTranscriptionSpy = jest.spyOn(attachmentVideoUnitService, 'startTranscription').mockReturnValue(of(undefined) as StartTxReturn);

                const attachmentVideoUnit = new AttachmentVideoUnit();
                attachmentVideoUnit.id = 1;
                attachmentVideoUnit.name = 'test';
                attachmentVideoUnit.videoSource = 'https://example.com/video.mp4';

                createSpy.mockReturnValue(of(new HttpResponse({ body: attachmentVideoUnit, status: 201 })));

                createAttachmentVideoUnitComponentFixture.detectChanges();

                const attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent = createAttachmentVideoUnitComponentFixture.debugElement.query(
                    By.directive(AttachmentVideoUnitFormComponent),
                ).componentInstance;

                const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
                    formProperties: {
                        name: 'test',
                        description: 'test description',
                        releaseDate: dayjs(),
                        videoSource: 'https://example.com/video.mp4',
                        generateTranscript: true,
                    },
                    fileProperties: {},
                };

                attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
                createAttachmentVideoUnitComponentFixture.detectChanges();
                tick();

                expect(startTranscriptionSpy).toHaveBeenCalledWith(1, attachmentVideoUnit.id, 'https://example.com/video.mp4');
            }));

            it('should not trigger transcript generation when generateTranscript is false', fakeAsync(() => {
                const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
                const createSpy = jest.spyOn(attachmentVideoUnitService, 'create');
                const startTranscriptionSpy = jest.spyOn(attachmentVideoUnitService, 'startTranscription').mockReturnValue(of(undefined) as StartTxReturn);

                const attachmentVideoUnit = new AttachmentVideoUnit();
                attachmentVideoUnit.id = 1;
                attachmentVideoUnit.name = 'test';
                attachmentVideoUnit.videoSource = 'https://example.com/video.mp4';

                createSpy.mockReturnValue(of(new HttpResponse({ body: attachmentVideoUnit, status: 201 })));

                createAttachmentVideoUnitComponentFixture.detectChanges();

                const attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent = createAttachmentVideoUnitComponentFixture.debugElement.query(
                    By.directive(AttachmentVideoUnitFormComponent),
                ).componentInstance;

                const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
                    formProperties: {
                        name: 'test',
                        description: 'test description',
                        releaseDate: dayjs(),
                        videoSource: 'https://example.com/video.mp4',
                        generateTranscript: false,
                    },
                    fileProperties: {},
                };

                attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
                createAttachmentVideoUnitComponentFixture.detectChanges();
                tick();

                expect(startTranscriptionSpy).not.toHaveBeenCalled();
            }));
        });
    });
});
