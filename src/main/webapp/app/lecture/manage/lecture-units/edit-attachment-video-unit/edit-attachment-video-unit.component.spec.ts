import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
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
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { objectToJsonBlob } from 'app/shared/util/blob-util';

describe('EditAttachmentVideoUnitComponent', () => {
    let fixture: ComponentFixture<EditAttachmentVideoUnitComponent>;
    let component: EditAttachmentVideoUnitComponent;
    let attachmentVideoUnitService: AttachmentVideoUnitService;
    let lectureTranscriptionService: LectureTranscriptionService;
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
                MockProvider(LectureTranscriptionService),
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
        component = fixture.componentInstance;
        router = TestBed.inject(Router);
        attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        lectureTranscriptionService = TestBed.inject(LectureTranscriptionService);

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

        jest.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(undefined));
        jest.spyOn(lectureTranscriptionService, 'getTranscriptionStatus').mockReturnValue(of(undefined));
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

    it('should fetch transcription data on initialization', () => {
        const transcription = { id: 1, videoUnitId: 1, language: 'en', content: 'test' };
        const getTranscriptionSpy = jest.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(transcription as any));
        const getTranscriptionStatusSpy = jest.spyOn(lectureTranscriptionService, 'getTranscriptionStatus').mockReturnValue(of(undefined));

        fixture.detectChanges();

        expect(getTranscriptionSpy).toHaveBeenCalledWith(attachmentVideoUnit.id);
        expect(getTranscriptionStatusSpy).toHaveBeenCalledWith(attachmentVideoUnit.id);
        expect(component.formData?.transcriptionProperties?.videoTranscription).toBe(JSON.stringify(transcription));
    });

    it('should handle error when fetching transcription data', () => {
        jest.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(undefined));
        jest.spyOn(lectureTranscriptionService, 'getTranscriptionStatus').mockReturnValue(of(undefined));

        fixture.detectChanges();

        expect(component.formData?.transcriptionProperties?.videoTranscription).toBe('');
    });

    it('should handle transcription status when present', () => {
        const transcriptionStatus = { status: 'PENDING', progress: 50 };
        jest.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(undefined));
        jest.spyOn(lectureTranscriptionService, 'getTranscriptionStatus').mockReturnValue(of(transcriptionStatus as any));

        fixture.detectChanges();

        expect(component.formData?.transcriptionStatus).toEqual(transcriptionStatus);
    });

    it('should start transcription when generateTranscript is true and playlistUrl is provided', () => {
        fixture.detectChanges();
        const attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;

        const playlistUrl = 'https://example.com/playlist.m3u8';
        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: attachmentVideoUnit.name,
                description: attachmentVideoUnit.description,
                releaseDate: attachmentVideoUnit.releaseDate,
                videoSource: attachmentVideoUnit.videoSource,
                version: 1,
                generateTranscript: true,
            },
            fileProperties: {},
            playlistUrl: playlistUrl,
        };

        const startTranscriptionSpy = jest.spyOn(attachmentVideoUnitService, 'startTranscription').mockReturnValue(of(undefined) as any);
        updateAttachmentVideoUnitSpy.mockReturnValue(of({ body: attachmentVideoUnit, status: 200 }));
        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
        fixture.detectChanges();

        expect(startTranscriptionSpy).toHaveBeenCalledWith(1, attachmentVideoUnit.id, playlistUrl);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should start transcription with videoSource when generateTranscript is true and no playlistUrl', () => {
        fixture.detectChanges();
        const attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;

        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: attachmentVideoUnit.name,
                description: attachmentVideoUnit.description,
                releaseDate: attachmentVideoUnit.releaseDate,
                videoSource: 'https://example.com/video.mp4',
                version: 1,
                generateTranscript: true,
            },
            fileProperties: {},
        };

        const startTranscriptionSpy = jest.spyOn(attachmentVideoUnitService, 'startTranscription').mockReturnValue(of(undefined) as any);
        updateAttachmentVideoUnitSpy.mockReturnValue(of({ body: attachmentVideoUnit, status: 200 }));
        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
        fixture.detectChanges();

        expect(startTranscriptionSpy).toHaveBeenCalledWith(1, attachmentVideoUnit.id, 'https://example.com/video.mp4');
    });

    it('should not start transcription when generateTranscript is true but no transcriptionUrl available', () => {
        fixture.detectChanges();
        const attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;

        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: attachmentVideoUnit.name,
                description: attachmentVideoUnit.description,
                releaseDate: attachmentVideoUnit.releaseDate,
                videoSource: undefined,
                version: 1,
                generateTranscript: true,
            },
            fileProperties: {},
        };

        const startTranscriptionSpy = jest.spyOn(attachmentVideoUnitService, 'startTranscription');
        updateAttachmentVideoUnitSpy.mockReturnValue(of({ body: attachmentVideoUnit, status: 200 }));
        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
        fixture.detectChanges();

        expect(startTranscriptionSpy).not.toHaveBeenCalled();
    });

    it('should handle error when startTranscription fails', () => {
        fixture.detectChanges();
        const attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;

        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: attachmentVideoUnit.name,
                description: attachmentVideoUnit.description,
                releaseDate: attachmentVideoUnit.releaseDate,
                videoSource: 'https://example.com/video.mp4',
                version: 1,
                generateTranscript: true,
            },
            fileProperties: {},
        };

        jest.spyOn(attachmentVideoUnitService, 'startTranscription').mockReturnValue(throwError(() => ({ status: 500, message: 'Error' })));
        updateAttachmentVideoUnitSpy.mockReturnValue(of({ body: attachmentVideoUnit, status: 200 }));

        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
        fixture.detectChanges();

        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should handle update without transcription generation', () => {
        fixture.detectChanges();
        const attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;

        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: attachmentVideoUnit.name,
                description: attachmentVideoUnit.description,
                releaseDate: attachmentVideoUnit.releaseDate,
                videoSource: attachmentVideoUnit.videoSource,
                version: 1,
                generateTranscript: false,
            },
            fileProperties: {},
        };

        updateAttachmentVideoUnitSpy.mockReturnValue(of({ body: attachmentVideoUnit, status: 200 }));
        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
        fixture.detectChanges();

        expect(updateAttachmentVideoUnitSpy).toHaveBeenCalledWith(1, 1, expect.any(FormData), undefined);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should cancel transcription when cancel button is clicked', () => {
        const transcriptionStatus = { jobId: 'test-job-123', status: 'PENDING' as any };
        jest.spyOn(lectureTranscriptionService, 'getTranscriptionStatus').mockReturnValue(of(transcriptionStatus));
        const cancelSpy = jest.spyOn(lectureTranscriptionService, 'cancelTranscription').mockReturnValue(of(true));

        fixture.detectChanges();

        expect(component.canCancelTranscription()).toBeTrue();

        component.cancelTranscription();

        expect(cancelSpy).toHaveBeenCalledWith('test-job-123');
        expect(component.transcriptionStatus).toBeUndefined();
    });

    it('should not show cancel button when transcription is completed', () => {
        const transcriptionStatus = { jobId: 'test-job-123', status: 'COMPLETED' as any };
        jest.spyOn(lectureTranscriptionService, 'getTranscriptionStatus').mockReturnValue(of(transcriptionStatus));

        fixture.detectChanges();

        expect(component.canCancelTranscription()).toBeFalse();
    });

    it('should cancel ongoing transcription when video URL changes', () => {
        const transcriptionStatus = { jobId: 'test-job-123', status: 'PENDING' as any };
        jest.spyOn(lectureTranscriptionService, 'getTranscriptionStatus').mockReturnValue(of(transcriptionStatus));
        const cancelSpy = jest.spyOn(lectureTranscriptionService, 'cancelTranscription').mockReturnValue(of(true));

        fixture.detectChanges();

        const attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;

        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: attachmentVideoUnit.name,
                description: attachmentVideoUnit.description,
                releaseDate: attachmentVideoUnit.releaseDate,
                videoSource: 'https://different-url.com/video.mp4', // Changed video URL
                version: 1,
            },
            fileProperties: {},
        };

        updateAttachmentVideoUnitSpy.mockReturnValue(of({ body: attachmentVideoUnit, status: 200 }));
        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
        fixture.detectChanges();

        expect(cancelSpy).toHaveBeenCalledWith('test-job-123');
        expect(updateAttachmentVideoUnitSpy).toHaveBeenCalled();
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should not cancel transcription when video URL does not change', () => {
        const transcriptionStatus = { jobId: 'test-job-123', status: 'PENDING' as any };
        jest.spyOn(lectureTranscriptionService, 'getTranscriptionStatus').mockReturnValue(of(transcriptionStatus));
        const cancelSpy = jest.spyOn(lectureTranscriptionService, 'cancelTranscription');

        fixture.detectChanges();

        const attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;

        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: attachmentVideoUnit.name,
                description: attachmentVideoUnit.description,
                releaseDate: attachmentVideoUnit.releaseDate,
                videoSource: attachmentVideoUnit.videoSource, // Same video URL
                version: 1,
            },
            fileProperties: {},
        };

        updateAttachmentVideoUnitSpy.mockReturnValue(of({ body: attachmentVideoUnit, status: 200 }));
        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
        fixture.detectChanges();

        expect(cancelSpy).not.toHaveBeenCalled();
        expect(updateAttachmentVideoUnitSpy).toHaveBeenCalled();
        expect(navigateSpy).toHaveBeenCalledOnce();
    });
});
