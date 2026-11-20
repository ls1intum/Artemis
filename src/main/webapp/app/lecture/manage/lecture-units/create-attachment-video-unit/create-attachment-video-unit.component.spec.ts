import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/foundation/service/alert.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { Location } from '@angular/common';
import { TumAetUiConfirmationService } from '@tumaet/ui-angular';
import { RecordingNavigation } from '../tum-live-upload/tum-live-creation';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { CreateAttachmentVideoUnitComponent } from 'app/lecture/manage/lecture-units/create-attachment-video-unit/create-attachment-video-unit.component';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('CreateAttachmentVideoUnitComponent', () => {
    let createAttachmentVideoUnitComponentFixture: ComponentFixture<CreateAttachmentVideoUnitComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [],
            providers: [
                MockProvider(AttachmentVideoUnitService),
                MockProvider(AlertService),
                MockProvider(LectureTranscriptionService),
                MockProvider(LectureService),
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
                                        return null;
                                    },
                                }),
                                parent: {
                                    paramMap: of({
                                        get: (key: string) => {
                                            switch (key) {
                                                case 'courseId':
                                                    return 1;
                                            }
                                            return null;
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
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should upload file, send POST for attachment and post for attachment video unit', async () => {
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

        const createAttachmentVideoUnitStub = vi.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(of(attachmentVideoUnit));

        const navigateSpy = vi.spyOn(router, 'navigate');
        createAttachmentVideoUnitComponentFixture.detectChanges();

        const attachmentVideoUnitFormComponent = createAttachmentVideoUnitComponentFixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;
        attachmentVideoUnitFormComponent.formSubmitted.emit(attachmentVideoUnitFormData);

        await createAttachmentVideoUnitComponentFixture.whenStable();
        expect(createAttachmentVideoUnitStub).toHaveBeenCalledOnce();
        const [lectureId, sentUnit, sentAttachment, sentFile] = createAttachmentVideoUnitStub.mock.calls[0];
        expect(lectureId).toBe(1);
        expect(sentUnit.name).toBe(attachmentVideoUnitFormData.formProperties.name);
        expect(sentAttachment!.name).toBe(attachmentVideoUnitFormData.formProperties.name);
        expect(sentFile!.name).toBe('lorem ipsum');
        expect(navigateSpy).toHaveBeenCalledTimes(1);
    });

    it('should create a unit with only a name, so its video can be uploaded to TUM.Live afterwards', async () => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        const createStub = vi.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(of(new AttachmentVideoUnit()));
        const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate');
        createAttachmentVideoUnitComponentFixture.detectChanges();

        const attachmentVideoUnitFormComponent = createAttachmentVideoUnitComponentFixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;
        attachmentVideoUnitFormComponent.formSubmitted.emit({ formProperties: { name: 'Recording' }, fileProperties: {} });

        await createAttachmentVideoUnitComponentFixture.whenStable();
        expect(createStub).toHaveBeenCalledOnce();
        const [lectureId, sentUnit, sentAttachment, sentFile] = createStub.mock.calls[0];
        expect(lectureId).toBe(1);
        expect(sentUnit.name).toBe('Recording');
        expect(sentAttachment).toBeUndefined();
        expect(sentFile).toBeUndefined();
        expect(navigateSpy).toHaveBeenCalledTimes(1);
    });
    it('keeps a normal attachment POST alive and suppresses its late redirect after leaving', () => {
        const fixture = createAttachmentVideoUnitComponentFixture;
        const request = new Subject<AttachmentVideoUnit>();
        vi.spyOn(TestBed.inject(AttachmentVideoUnitService), 'create').mockReturnValue(request);
        const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
        fixture.detectChanges();
        const pdf = new File(['%PDF'], 'slides.pdf', { type: 'application/pdf' });

        fixture.componentInstance.createAttachmentVideoUnit({ formProperties: { name: 'Slides' }, fileProperties: { file: pdf, fileName: pdf.name } });
        fixture.destroy();

        expect(request.observed).toBe(true);
        request.next(new AttachmentVideoUnit());
        request.complete();
        expect(navigate).not.toHaveBeenCalled();
    });

    it('retries a failed redirect with the saved unit and the original file without creating another unit', async () => {
        const fixture = createAttachmentVideoUnitComponentFixture;
        const component = fixture.componentInstance;
        const router = TestBed.inject(Router);
        const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValueOnce(false).mockResolvedValueOnce(true);
        const create = vi.spyOn(TestBed.inject(AttachmentVideoUnitService), 'create').mockReturnValue(of({ id: 42 }));
        fixture.detectChanges();
        const recording = new File(['mp4'], 'recording.mp4', { type: 'video/mp4' });
        const data = { formProperties: { name: 'Recording' }, fileProperties: {}, recording };

        component.createAttachmentVideoUnit(data);
        await fixture.whenStable();
        component.createAttachmentVideoUnit(data);
        component['continueRecording']();

        expect(create).toHaveBeenCalledOnce();
        expect(router.createUrlTree).toHaveBeenCalledWith(['/course-management', 1, 'lectures', 1, 'unit-management', 'attachment-video-units', 42, 'edit']);
        expect(navigate).toHaveBeenCalledTimes(2);
        const info = navigate.mock.calls[1][1]?.info as RecordingNavigation;
        expect(info.target).toEqual({ courseId: 1, lectureId: 1, unitId: 42 });
        expect(info.takeFile()).toBe(recording);
        expect(TestBed.inject(Location).getState()).toMatchObject({ tumLiveRecordingCheckpoint: { courseId: 1, lectureId: 1, unitId: 42 } });
    });

    it('continues to the saved standalone unit after a reload without sending the MP4 again', () => {
        TestBed.inject(Location).replaceState('/create', '', { tumLiveRecordingCheckpoint: { courseId: 1, lectureId: 1, unitId: 42 } });
        const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
        const create = vi.spyOn(TestBed.inject(AttachmentVideoUnitService), 'create');

        createAttachmentVideoUnitComponentFixture.detectChanges();

        expect(create).not.toHaveBeenCalled();
        expect(navigate).toHaveBeenCalledOnce();
        const info = navigate.mock.calls[0][1]?.info as RecordingNavigation;
        expect(info.target.unitId).toBe(42);
        expect(info.takeFile()).toBeUndefined();
    });

    it('keeps the standalone create locked after an empty status check', () => {
        const fixture = createAttachmentVideoUnitComponentFixture;
        const component = fixture.componentInstance;
        const create = vi.spyOn(TestBed.inject(AttachmentVideoUnitService), 'create').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
        const read = vi.spyOn(TestBed.inject(LectureService), 'findWithDetails').mockReturnValue(of({ id: 1, lectureUnits: [] }));
        const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
        fixture.detectChanges();
        const recording = new File(['mp4'], 'recording.mp4', { type: 'video/mp4' });
        const data = { formProperties: { name: 'Recording' }, fileProperties: {}, recording };

        component.createAttachmentVideoUnit(data);
        fixture.detectChanges();
        fixture.nativeElement.querySelector('[data-testid="tum-live-save-recovery"] button').click();
        fixture.detectChanges();
        component.createAttachmentVideoUnit(data);

        expect(read).toHaveBeenCalledExactlyOnceWith(1);
        expect(create).toHaveBeenCalledOnce();
        const form: AttachmentVideoUnitFormComponent = fixture.debugElement.query(By.directive(AttachmentVideoUnitFormComponent)).componentInstance;
        expect(form.recordingSaveLocked()).toBe(true);
        expect(component['recovery']()).toEqual({ courseId: 1, lectureId: 1 });
        expect(navigate).not.toHaveBeenCalled();
    });

    it('asks before leaving a recording create and ignores the late result if confirmed', () => {
        const fixture = createAttachmentVideoUnitComponentFixture;
        const component = fixture.componentInstance;
        const request = new Subject<AttachmentVideoUnit>();
        vi.spyOn(TestBed.inject(AttachmentVideoUnitService), 'create').mockReturnValue(request);
        const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
        const dialogs = fixture.debugElement.injector.get(TumAetUiConfirmationService);
        fixture.detectChanges();
        const recording = new File(['mp4'], 'recording.mp4', { type: 'video/mp4' });
        component.createAttachmentVideoUnit({ formProperties: { name: 'Recording' }, fileProperties: {}, recording });

        const decision = component.confirmRecordingLeave();
        if (typeof decision === 'boolean') {
            throw new Error('Expected a confirmation while saving');
        }
        const result = vi.fn();
        decision.subscribe(result);
        expect(dialogs.request(undefined)?.header).toBe('artemisApp.tumLiveUpload.savingTitle');
        dialogs.request(undefined)!.accept();

        expect(result).toHaveBeenCalledExactlyOnceWith(true);
        expect(request.observed).toBe(false);
        request.next({ id: 42 });
        expect(navigate).not.toHaveBeenCalled();
    });
});
