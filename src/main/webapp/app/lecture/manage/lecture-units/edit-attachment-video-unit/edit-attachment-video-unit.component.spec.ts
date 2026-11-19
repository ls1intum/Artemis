import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { MockInstance } from 'vitest';
import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from '../attachment-video-unit-form/attachment-video-unit-form.component';
import { TumLiveUploadService } from 'app/lecture/manage/lecture-units/services/tum-live-upload.service';
import { AttachmentVideoUnitService } from '../services/attachment-video-unit.service';
import { EditAttachmentVideoUnitComponent } from './edit-attachment-video-unit.component';
import { AttachmentUpdateIntent, AttachmentVideoUnit } from '../../../shared/entities/lecture-unit/attachmentVideoUnit.model';
import { Attachment, AttachmentType } from '../../../shared/entities/attachment.model';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';

import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('EditAttachmentVideoUnitComponent', () => {
    let fixture: ComponentFixture<EditAttachmentVideoUnitComponent>;
    let attachmentVideoUnitService: AttachmentVideoUnitService;
    let router: Router;
    let navigateSpy: MockInstance<Router['navigate']>;
    let updateAttachmentVideoUnitSpy: MockInstance<AttachmentVideoUnitService['update']>;
    let attachment: Attachment;
    let attachmentVideoUnit: AttachmentVideoUnit;
    let fakeFile: File;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [],
            providers: [
                MockProvider(AttachmentVideoUnitService),
                MockProvider(AlertService),
                MockProvider(TumLiveUploadService, { getState: () => of({ connected: true }) }),
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
                                    case 'attachmentVideoUnitId':
                                        return 1;
                                }
                                return null;
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
                                        return null;
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

        fakeFile = new File(['content'], 'Test-File.pdf', { type: 'application/pdf' });

        vi.spyOn(attachmentVideoUnitService, 'findById').mockReturnValue(of(attachmentVideoUnit));
        updateAttachmentVideoUnitSpy = vi.spyOn(attachmentVideoUnitService, 'update');
        navigateSpy = vi.spyOn(router, 'navigate');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('updates a video-only unit without sending attachment metadata', () => {
        attachmentVideoUnit.attachment = undefined;
        attachmentVideoUnit.videoSource = 'https://example.com/video';
        vi.spyOn(attachmentVideoUnitService, 'findById').mockReturnValue(of(attachmentVideoUnit));
        updateAttachmentVideoUnitSpy.mockReturnValue(of({ ...attachmentVideoUnit, videoSource: 'https://example.com/updated-video' } as AttachmentVideoUnit));
        fixture.detectChanges();

        fixture.componentInstance.updateAttachmentVideoUnit({
            formProperties: { name: 'Renamed video', videoSource: 'https://example.com/updated-video' },
            fileProperties: {},
        });

        const [, , sentUnit, sentUpdate] = updateAttachmentVideoUnitSpy.mock.calls[0];
        expect(sentUpdate).toMatchObject({ attachment: undefined, file: undefined });
        expect(sentUnit).toMatchObject({
            name: 'Renamed video',
            videoSource: 'https://example.com/updated-video',
            attachmentUpdateIntent: AttachmentUpdateIntent.NO_FILE_CHANGE,
        });
        expect(navigateSpy).toHaveBeenCalled();
    });

    it('includes attachment metadata when adding a file to a video-only unit', () => {
        attachmentVideoUnit.attachment = undefined;
        vi.spyOn(attachmentVideoUnitService, 'findById').mockReturnValue(of(attachmentVideoUnit));
        updateAttachmentVideoUnitSpy.mockReturnValue(of(attachmentVideoUnit));
        fixture.detectChanges();

        fixture.componentInstance.updateAttachmentVideoUnit({
            formProperties: { name: 'Video with slides', videoSource: 'https://example.com/video' },
            fileProperties: { file: fakeFile, fileName: fakeFile.name },
        });

        const [, , sentUnit, sentUpdate] = updateAttachmentVideoUnitSpy.mock.calls[0];
        expect(sentUpdate!.attachment).toBeDefined();
        expect(sentUpdate!.file).toBe(fakeFile);
        expect(sentUnit.attachmentUpdateIntent).toBe(AttachmentUpdateIntent.FILE_UPLOAD);
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

    it('shows the video link of a video uploaded to TUM.Live once it is attached', () => {
        fixture.detectChanges();
        const form: AttachmentVideoUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;
        vi.spyOn(attachmentVideoUnitService, 'findById').mockReturnValue(of({ ...attachmentVideoUnit, videoSource: 'https://tum.live/w/algo/55' } as AttachmentVideoUnit));

        fixture.componentInstance.onTumLiveVideoAttached(attachmentVideoUnit.id!);

        expect(form.videoSourceControl!.value).toBe('https://tum.live/w/algo/55');
        expect(fixture.componentInstance.attachmentVideoUnit()?.videoSource).toBe('https://tum.live/w/algo/55');
    });

    it('should update attachment video unit with file change without notification', () => {
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

        updateAttachmentVideoUnitSpy.mockReturnValue(of(attachmentVideoUnit));
        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
        fixture.detectChanges();

        const [lectureId, unitId, sentUnit, sentUpdate] = updateAttachmentVideoUnitSpy.mock.calls[0];
        expect([lectureId, unitId]).toEqual([1, 1]);
        expect(sentUnit.attachmentUpdateIntent).toBe(AttachmentUpdateIntent.FILE_UPLOAD);
        expect(sentUpdate!.notificationText).toBeUndefined();
        expect(sentUpdate!.file!.name).toBe(fileName);
        expect(sentUpdate!.attachment!.name).toBe(attachmentVideoUnit.name);
        expect(navigateSpy).toHaveBeenCalledTimes(1);
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
                videoSource: attachmentVideoUnit.videoSource,
                version: 1,
                updateNotificationText: notification,
            },
            fileProperties: {
                file: fakeFile,
                fileName,
            },
        };

        updateAttachmentVideoUnitSpy.mockReturnValue(of(attachmentVideoUnit));
        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
        fixture.detectChanges();

        expect(updateAttachmentVideoUnitSpy.mock.calls[0][3]!.notificationText).toBe(notification);
        expect(navigateSpy).toHaveBeenCalledTimes(1);
    });

    it('should update attachment video unit without file change without notification', () => {
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

        updateAttachmentVideoUnitSpy.mockReturnValue(of(attachmentVideoUnit));
        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
        fixture.detectChanges();

        const [, , sentUnit, sentUpdate] = updateAttachmentVideoUnitSpy.mock.calls[0];
        expect(sentUnit.attachmentUpdateIntent).toBe(AttachmentUpdateIntent.NO_FILE_CHANGE);
        expect(sentUpdate).toMatchObject({ notificationText: undefined, file: undefined });
        expect(navigateSpy).toHaveBeenCalledTimes(1);
    });

    it('should not notify students when the notification text is empty', () => {
        fixture.detectChanges();
        updateAttachmentVideoUnitSpy.mockReturnValue(of(attachmentVideoUnit));

        fixture.componentInstance.updateAttachmentVideoUnit({
            formProperties: { name: attachmentVideoUnit.name, updateNotificationText: '' },
            fileProperties: { file: fakeFile, fileName: 'updated file' },
        });

        expect(updateAttachmentVideoUnitSpy.mock.calls[0][3]!.notificationText).toBeUndefined();
    });

    it('stays on the page with the entered video URL when another editor changed the link meanwhile', async () => {
        const warningSpy = vi.spyOn(TestBed.inject(AlertService), 'warning');
        fixture.detectChanges();
        const form: AttachmentVideoUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;
        form.videoSourceControl!.setValue('https://example.com/entered');
        // The server kept the link another editor saved after this page was loaded.
        updateAttachmentVideoUnitSpy.mockReturnValue(of({ ...attachmentVideoUnit, videoSource: 'https://example.com/other' } as AttachmentVideoUnit));

        fixture.componentInstance.updateAttachmentVideoUnit({ formProperties: { name: 'Renamed', videoSource: 'https://example.com/entered' }, fileProperties: {} });

        expect(navigateSpy).not.toHaveBeenCalled();
        expect(warningSpy).toHaveBeenCalledWith('artemisApp.lecture.unitEditor.videoSourceChangedMeanwhile');
        expect(form.videoSourceControl!.value).toBe('https://example.com/entered');
        expect(fixture.componentInstance.attachmentVideoUnit()?.videoSource).toBe('https://example.com/other');

        // Saving again compares with the link the server has now.
        updateAttachmentVideoUnitSpy.mockReturnValue(of({ ...attachmentVideoUnit, videoSource: 'https://example.com/entered' } as AttachmentVideoUnit));
        fixture.componentInstance.updateAttachmentVideoUnit({ formProperties: { name: 'Renamed', videoSource: 'https://example.com/entered' }, fileProperties: {} });

        expect(updateAttachmentVideoUnitSpy.mock.calls[1][2]).toMatchObject({ loadedVideoSource: 'https://example.com/other' });
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should report a failed update and stay on the page', () => {
        const alertService = TestBed.inject(AlertService);
        const errorSpy = vi.spyOn(alertService, 'error');
        fixture.detectChanges();
        updateAttachmentVideoUnitSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));

        fixture.componentInstance.updateAttachmentVideoUnit({ formProperties: { name: attachmentVideoUnit.name }, fileProperties: {} });

        expect(errorSpy).toHaveBeenCalledWith('error.http.400');
        expect(navigateSpy).not.toHaveBeenCalled();
        expect(fixture.componentInstance.isLoading()).toBe(false);
    });

    it('should report a unit that cannot be loaded and send no update without it', () => {
        const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');
        vi.spyOn(attachmentVideoUnitService, 'findById').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
        fixture.detectChanges();

        fixture.componentInstance.updateAttachmentVideoUnit({ formProperties: { name: 'test' }, fileProperties: {} });

        expect(errorSpy).toHaveBeenCalledWith('error.http.404');
        expect(updateAttachmentVideoUnitSpy).not.toHaveBeenCalled();
        expect(fixture.componentInstance.isLoading()).toBe(false);
    });

    it('should treat a zero-byte file as no file change', () => {
        fixture.detectChanges();
        const attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;
        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: attachmentVideoUnit.name,
                description: attachmentVideoUnit.description,
                releaseDate: attachmentVideoUnit.releaseDate,
                version: 1,
            },
            fileProperties: {
                file: new File([], 'empty.pdf', { type: 'application/pdf' }),
                fileName: 'empty.pdf',
            },
        };
        updateAttachmentVideoUnitSpy.mockReturnValue(of(attachmentVideoUnit));

        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);
        fixture.detectChanges();

        const [, , sentUnit, sentUpdate] = updateAttachmentVideoUnitSpy.mock.calls[0];
        expect(sentUnit.attachmentUpdateIntent).toBe(AttachmentUpdateIntent.NO_FILE_CHANGE);
        expect(sentUpdate!.file).toBeUndefined();
    });
});
