import { ComponentFixture, TestBed, fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { TranscriptionStatus } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MAX_FILE_SIZE, MAX_VIDEO_FILE_SIZE } from 'app/shared/constants/input.constants';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { HttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('AttachmentVideoUnitFormComponent', () => {
    let attachmentVideoUnitFormComponentFixture: ComponentFixture<AttachmentVideoUnitFormComponent>;
    let attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent;
    let accountService: AccountService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                ReactiveFormsModule,
                FormsModule,
                MockModule(OwlDateTimeModule),
                MockModule(OwlNativeDateTimeModule),
                FontAwesomeTestingModule,
                AttachmentVideoUnitFormComponent,
            ],
            declarations: [FormDateTimePickerComponent, MockPipe(ArtemisTranslatePipe), MockComponent(CompetencySelectionComponent), MockDirective(NgbTooltip)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        }).compileComponents();

        attachmentVideoUnitFormComponentFixture = TestBed.createComponent(AttachmentVideoUnitFormComponent);
        attachmentVideoUnitFormComponent = attachmentVideoUnitFormComponentFixture.componentInstance;
        accountService = TestBed.inject(AccountService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();
        expect(attachmentVideoUnitFormComponent).not.toBeNull();
    });

    it('should show transcription input for admin', () => {
        jest.spyOn(accountService, 'isAdmin').mockReturnValue(true);
        attachmentVideoUnitFormComponentFixture.detectChanges();
        expect(attachmentVideoUnitFormComponent.shouldShowTranscriptionCreation()).toBeTrue();
        const transcriptionInput = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#video-transcription-row');
        expect(transcriptionInput).not.toBeNull();
    });

    it('should not show transcription input for non-admin', () => {
        jest.spyOn(accountService, 'isAdmin').mockReturnValue(false);
        attachmentVideoUnitFormComponentFixture.detectChanges();
        expect(attachmentVideoUnitFormComponent.shouldShowTranscriptionCreation()).toBeFalse();
        const transcriptionInput = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#video-transcription-row');
        expect(transcriptionInput).toBeNull();
    });

    it('should include transcription in form submission when admin', () => {
        jest.spyOn(accountService, 'isAdmin').mockReturnValue(true);
        attachmentVideoUnitFormComponentFixture.detectChanges();

        const exampleName = 'test';
        attachmentVideoUnitFormComponent.nameControl!.setValue(exampleName);
        const exampleVideoUrl = 'https://live.rbg.tum.de/?video_only=1';
        attachmentVideoUnitFormComponent.videoSourceControl!.setValue(exampleVideoUrl);
        const exampleTranscription = '{"language": "en"}';
        attachmentVideoUnitFormComponent.videoTranscriptionControl!.setValue(exampleTranscription);

        attachmentVideoUnitFormComponentFixture.detectChanges();
        expect(attachmentVideoUnitFormComponent.form.valid).toBeTrue();

        const submitFormSpy = jest.spyOn(attachmentVideoUnitFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

        const submitButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        expect(submitFormSpy).toHaveBeenCalledOnce();
        expect(submitFormEventSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                transcriptionProperties: {
                    videoTranscription: exampleTranscription,
                },
            }),
        );

        submitFormSpy.mockRestore();
        submitFormEventSpy.mockRestore();
    });

    it('should correctly set form values in edit mode', () => {
        const fakeFile = new File([''], 'Test-File.pdf', {
            type: 'application/pdf',
        });

        attachmentVideoUnitFormComponentFixture.componentRef.setInput('isEditMode', true);
        const formData: AttachmentVideoUnitFormData = {
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
        attachmentVideoUnitFormComponentFixture.detectChanges();

        attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', formData);
        attachmentVideoUnitFormComponentFixture.detectChanges();

        expect(attachmentVideoUnitFormComponent.nameControl?.value).toEqual(formData.formProperties.name);
        expect(attachmentVideoUnitFormComponent.releaseDateControl?.value).toEqual(formData.formProperties.releaseDate);
        expect(attachmentVideoUnitFormComponent.descriptionControl?.value).toEqual(formData.formProperties.description);
        expect(attachmentVideoUnitFormComponent.versionControl?.value).toEqual(formData.formProperties.version);
        expect(attachmentVideoUnitFormComponent.updateNotificationTextControl?.value).toEqual(formData.formProperties.updateNotificationText);
        expect(attachmentVideoUnitFormComponent.fileName()).toEqual(formData.fileProperties.fileName);
        expect(attachmentVideoUnitFormComponent.file).toEqual(formData.fileProperties.file);
    });

    it('should clear transcription status when switching to a unit without transcription data', () => {
        attachmentVideoUnitFormComponentFixture.componentRef.setInput('isEditMode', true);
        attachmentVideoUnitFormComponentFixture.detectChanges();

        const formDataWithStatus: AttachmentVideoUnitFormData = {
            formProperties: {},
            fileProperties: {},
            transcriptionStatus: TranscriptionStatus.PENDING,
        };
        attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', formDataWithStatus);
        attachmentVideoUnitFormComponentFixture.detectChanges();
        expect(attachmentVideoUnitFormComponent.transcriptionStatus()).toBe(TranscriptionStatus.PENDING);

        const formDataWithoutStatus: AttachmentVideoUnitFormData = {
            formProperties: {},
            fileProperties: {},
        };
        attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', formDataWithoutStatus);
        attachmentVideoUnitFormComponentFixture.detectChanges();
        expect(attachmentVideoUnitFormComponent.transcriptionStatus()).toBeUndefined();
        expect(attachmentVideoUnitFormComponent.showTranscriptionPendingWarning()).toBeFalse();
    });

    it('should clear transcription status when leaving edit mode', () => {
        attachmentVideoUnitFormComponentFixture.componentRef.setInput('isEditMode', true);
        attachmentVideoUnitFormComponentFixture.detectChanges();

        const formDataWithStatus: AttachmentVideoUnitFormData = {
            formProperties: {},
            fileProperties: {},
            transcriptionStatus: TranscriptionStatus.PENDING,
        };
        attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', formDataWithStatus);
        attachmentVideoUnitFormComponentFixture.detectChanges();

        expect(attachmentVideoUnitFormComponent.transcriptionStatus()).toBe(TranscriptionStatus.PENDING);
        expect(attachmentVideoUnitFormComponent.showTranscriptionPendingWarning()).toBeTrue();

        attachmentVideoUnitFormComponentFixture.componentRef.setInput('isEditMode', false);
        attachmentVideoUnitFormComponentFixture.detectChanges();

        expect(attachmentVideoUnitFormComponent.transcriptionStatus()).toBeUndefined();
        expect(attachmentVideoUnitFormComponent.showTranscriptionPendingWarning()).toBeFalse();
    });
    it('should submit valid form', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();
        const exampleName = 'test';
        attachmentVideoUnitFormComponent.nameControl!.setValue(exampleName);
        const exampleReleaseDate = dayjs().year(2010).month(3).date(5);
        attachmentVideoUnitFormComponent.releaseDateControl!.setValue(exampleReleaseDate);
        const exampleDescription = 'lorem ipsum';
        attachmentVideoUnitFormComponent.descriptionControl!.setValue(exampleDescription);
        attachmentVideoUnitFormComponent.versionControl!.enable();
        const exampleVersion = 42;
        attachmentVideoUnitFormComponent.versionControl!.setValue(exampleVersion);
        const exampleUpdateNotificationText = 'updated';
        attachmentVideoUnitFormComponent.updateNotificationTextControl!.setValue(exampleUpdateNotificationText);
        const fakeFile = new File([''], 'Test-File.pdf', {
            type: 'application/pdf',
        });
        attachmentVideoUnitFormComponent.file = fakeFile;
        const exampleFileName = 'lorem Ipsum';
        attachmentVideoUnitFormComponent.fileName.set(exampleFileName);
        const exampleVideoUrl = 'https://live.rbg.tum.de/?video_only=1';
        attachmentVideoUnitFormComponent.videoSourceControl!.setValue(exampleVideoUrl);

        attachmentVideoUnitFormComponentFixture.detectChanges();
        expect(attachmentVideoUnitFormComponent.form.valid).toBeTrue();

        const submitFormSpy = jest.spyOn(attachmentVideoUnitFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

        const submitButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        expect(submitFormSpy).toHaveBeenCalledOnce();
        expect(submitFormEventSpy).toHaveBeenCalledWith({
            formProperties: {
                name: exampleName,
                description: exampleDescription,
                releaseDate: exampleReleaseDate,
                competencyLinks: null,
                version: exampleVersion,
                updateNotificationText: exampleUpdateNotificationText,
                videoSource: exampleVideoUrl,
                urlHelper: null,
                generateTranscript: false,
                videoTranscription: undefined,
            },
            fileProperties: {
                file: fakeFile,
                fileName: exampleFileName,
            },
            videoFileProperties: {
                videoFile: undefined,
                videoFileName: undefined,
            },
            transcriptionProperties: {
                videoTranscription: null,
            },
            playlistUrl: undefined,
            uploadProgressCallback: expect.any(Function),
        });

        submitFormSpy.mockRestore();
        submitFormEventSpy.mockRestore();
    });

    it('should not submit a form when name is missing', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();
        const exampleReleaseDate = dayjs().year(2010).month(3).date(5);
        attachmentVideoUnitFormComponent.releaseDateControl!.setValue(exampleReleaseDate);
        const exampleDescription = 'lorem ipsum';
        attachmentVideoUnitFormComponent.descriptionControl!.setValue(exampleDescription);
        const exampleVersion = 42;
        attachmentVideoUnitFormComponent.versionControl!.setValue(exampleVersion);
        const exampleUpdateNotificationText = 'updated';
        attachmentVideoUnitFormComponent.updateNotificationTextControl!.setValue(exampleUpdateNotificationText);
        const fakeFile = new File([''], 'Test-File.pdf', {
            type: 'application/pdf',
        });
        attachmentVideoUnitFormComponent.file = fakeFile;
        attachmentVideoUnitFormComponent.fileName.set('lorem Ipsum');

        expect(attachmentVideoUnitFormComponent.form.invalid).toBeTrue();
        const submitFormSpy = jest.spyOn(attachmentVideoUnitFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

        const submitButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        expect(submitFormSpy).not.toHaveBeenCalled();
        expect(submitFormEventSpy).not.toHaveBeenCalled();
    });

    it('calls on file change on changed file', () => {
        const fakeBlob = new Blob([''], { type: 'application/pdf' });
        // @ts-ignore
        fakeBlob['name'] = 'Test-File.pdf';
        const onFileChangeStub = jest.spyOn(attachmentVideoUnitFormComponent, 'onFileChange');
        attachmentVideoUnitFormComponentFixture.detectChanges();
        const fileInput = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#fileInput');
        fileInput.dispatchEvent(new Event('change'));
        expect(onFileChangeStub).toHaveBeenCalledOnce();
    });

    it('should disable submit button for too big file', fakeAsync(() => {
        const fakeFile = new File([''], 'Test-File.pdf', {
            type: 'application/pdf',
            lastModified: Date.now(),
        });

        // Set file size to exceed the maximum file size
        Object.defineProperty(fakeFile, 'size', { value: MAX_FILE_SIZE + 1 });

        attachmentVideoUnitFormComponent.onFileChange({
            target: { files: [fakeFile] } as unknown as EventTarget,
        } as Event);

        // Wait for the setTimeout in onFileChange to complete (1000ms for file processing + 1000ms for reset)
        tick(1100);
        attachmentVideoUnitFormComponentFixture.detectChanges();

        const submitButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        expect(attachmentVideoUnitFormComponent.isFileTooBig()).toBeTrue();
        expect(submitButton.disabled).toBeTrue();
    }));

    it('should not submit a form when file and videoSource is missing', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();
        const exampleName = 'test';
        const exampleReleaseDate = dayjs().year(2010).month(3).date(5);
        const exampleDescription = 'lorem ipsum';
        attachmentVideoUnitFormComponent.nameControl!.setValue(exampleName);
        attachmentVideoUnitFormComponent.releaseDateControl!.setValue(exampleReleaseDate);
        attachmentVideoUnitFormComponent.descriptionControl!.setValue(exampleDescription);
        attachmentVideoUnitFormComponent.versionControl!.enable();
        const exampleVersion = 42;
        attachmentVideoUnitFormComponent.versionControl!.setValue(exampleVersion);
        const exampleUpdateNotificationText = 'updated';
        attachmentVideoUnitFormComponent.updateNotificationTextControl!.setValue(exampleUpdateNotificationText);
        // Do not set file and ensure videoSource is empty
        attachmentVideoUnitFormComponent.videoSourceControl!.setValue('');
        attachmentVideoUnitFormComponentFixture.detectChanges();

        expect(attachmentVideoUnitFormComponent.form.valid).toBeTrue();

        const submitFormSpy = jest.spyOn(attachmentVideoUnitFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

        const submitButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        expect(submitFormSpy).not.toHaveBeenCalled();
        expect(submitFormEventSpy).not.toHaveBeenCalled();

        submitFormSpy.mockRestore();
        submitFormEventSpy.mockRestore();
    });

    it('should submit a form when file is missing but videoSource is set', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();
        const exampleName = 'test';
        const exampleReleaseDate = dayjs().year(2010).month(3).date(5);
        const exampleDescription = 'lorem ipsum';
        attachmentVideoUnitFormComponent.nameControl!.setValue(exampleName);
        attachmentVideoUnitFormComponent.releaseDateControl!.setValue(exampleReleaseDate);
        attachmentVideoUnitFormComponent.descriptionControl!.setValue(exampleDescription);
        attachmentVideoUnitFormComponent.versionControl!.enable();
        const exampleVersion = 42;
        attachmentVideoUnitFormComponent.versionControl!.setValue(exampleVersion);
        const exampleUpdateNotificationText = 'updated';
        attachmentVideoUnitFormComponent.updateNotificationTextControl!.setValue(exampleUpdateNotificationText);
        const exampleVideoUrl = 'https://live.rbg.tum.de/?video_only=1';
        attachmentVideoUnitFormComponent.videoSourceControl!.setValue(exampleVideoUrl);
        // Do not set file

        attachmentVideoUnitFormComponentFixture.detectChanges();

        expect(attachmentVideoUnitFormComponent.form.valid).toBeTrue();

        const submitFormSpy = jest.spyOn(attachmentVideoUnitFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

        const submitButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        expect(submitFormSpy).toHaveBeenCalledOnce();
        expect(submitFormEventSpy).toHaveBeenCalledWith({
            formProperties: {
                name: exampleName,
                description: exampleDescription,
                releaseDate: exampleReleaseDate,
                competencyLinks: null,
                version: exampleVersion,
                updateNotificationText: exampleUpdateNotificationText,
                videoSource: exampleVideoUrl,
                urlHelper: null,
                generateTranscript: false,
                videoTranscription: undefined,
            },
            fileProperties: {
                file: undefined,
                fileName: undefined,
            },
            videoFileProperties: {
                videoFile: undefined,
                videoFileName: undefined,
            },
            transcriptionProperties: {
                videoTranscription: null,
            },
            playlistUrl: undefined,
            uploadProgressCallback: expect.any(Function),
        });

        submitFormSpy.mockRestore();
        submitFormEventSpy.mockRestore();
    });

    it('should submit a form when file is set but videoSource is missing', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();
        const exampleName = 'test';
        const exampleReleaseDate = dayjs().year(2010).month(3).date(5);
        const exampleDescription = 'lorem ipsum';
        attachmentVideoUnitFormComponent.nameControl!.setValue(exampleName);
        attachmentVideoUnitFormComponent.releaseDateControl!.setValue(exampleReleaseDate);
        attachmentVideoUnitFormComponent.descriptionControl!.setValue(exampleDescription);
        attachmentVideoUnitFormComponent.versionControl!.enable();
        const exampleVersion = 42;
        attachmentVideoUnitFormComponent.versionControl!.setValue(exampleVersion);
        const exampleUpdateNotificationText = 'updated';
        attachmentVideoUnitFormComponent.updateNotificationTextControl!.setValue(exampleUpdateNotificationText);
        // Set file and fileName
        const fakeFile = new File([''], 'Test-File.pdf', {
            type: 'application/pdf',
        });
        attachmentVideoUnitFormComponent.file = fakeFile;
        const exampleFileName = 'lorem Ipsum';
        attachmentVideoUnitFormComponent.fileName.set(exampleFileName);
        // Ensure videoSource is missing
        attachmentVideoUnitFormComponent.videoSourceControl!.setValue('');

        attachmentVideoUnitFormComponentFixture.detectChanges();

        expect(attachmentVideoUnitFormComponent.form.valid).toBeTrue();

        const submitFormSpy = jest.spyOn(attachmentVideoUnitFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

        const submitButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        expect(submitFormSpy).toHaveBeenCalledOnce();
        expect(submitFormEventSpy).toHaveBeenCalledWith({
            formProperties: {
                name: exampleName,
                description: exampleDescription,
                releaseDate: exampleReleaseDate,
                competencyLinks: null,
                version: exampleVersion,
                updateNotificationText: exampleUpdateNotificationText,
                videoSource: '',
                urlHelper: null,
                generateTranscript: false,
                videoTranscription: undefined,
            },
            fileProperties: {
                file: fakeFile,
                fileName: exampleFileName,
            },
            videoFileProperties: {
                videoFile: undefined,
                videoFileName: undefined,
            },
            transcriptionProperties: {
                videoTranscription: null,
            },
            playlistUrl: undefined,
            uploadProgressCallback: expect.any(Function),
        });

        submitFormSpy.mockRestore();
        submitFormEventSpy.mockRestore();
    });

    it('should correctly transform YouTube URL into embeddable format', () => {
        const validYouTubeUrl = 'https://www.youtube.com/watch?v=8iU8LPEa4o0';
        const validYouTubeUrlInEmbeddableFormat = 'https://www.youtube.com/embed/8iU8LPEa4o0';

        jest.spyOn(attachmentVideoUnitFormComponent, 'extractEmbeddedUrl').mockReturnValue(validYouTubeUrlInEmbeddableFormat);
        jest.spyOn(attachmentVideoUnitFormComponent, 'videoSourceUrlValidator').mockReturnValue(undefined);
        jest.spyOn(attachmentVideoUnitFormComponent, 'videoSourceTransformUrlValidator').mockReturnValue(undefined);

        attachmentVideoUnitFormComponentFixture.detectChanges();

        attachmentVideoUnitFormComponent.urlHelperControl!.setValue(validYouTubeUrl);
        attachmentVideoUnitFormComponentFixture.detectChanges();
        const transformButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#transformButton');
        transformButton.click();

        return attachmentVideoUnitFormComponentFixture.whenStable().then(() => {
            expect(attachmentVideoUnitFormComponent.videoSourceControl?.value).toEqual(validYouTubeUrlInEmbeddableFormat);
        });
    });

    it('should correctly transform TUM-Live URL without video only into embeddable format', () => {
        const tumLiveUrl = 'https://live.rbg.tum.de/w/test/26';
        const expectedUrl = 'https://live.rbg.tum.de/w/test/26?video_only=1';

        attachmentVideoUnitFormComponentFixture.detectChanges();
        attachmentVideoUnitFormComponent.urlHelperControl!.setValue(tumLiveUrl);
        attachmentVideoUnitFormComponentFixture.detectChanges();

        const transformButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#transformButton');
        transformButton.click();

        return attachmentVideoUnitFormComponentFixture.whenStable().then(() => {
            expect(attachmentVideoUnitFormComponent.videoSourceControl?.value).toEqual(expectedUrl);
        });
    });

    it('should enable generateTranscript checkbox when playlist is available', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();
        const originalUrl = 'https://live.rbg.tum.de/w/test/26';
        attachmentVideoUnitFormComponent.videoSourceControl!.setValue(originalUrl);

        const httpMock = TestBed.inject(HttpClient);
        const spy = jest.spyOn(httpMock, 'get').mockReturnValue(of('https://live.rbg.tum.de/playlist.m3u8'));

        attachmentVideoUnitFormComponent.checkPlaylistAvailability(originalUrl);

        expect(spy).toHaveBeenCalled();
        expect(attachmentVideoUnitFormComponent.canGenerateTranscript()).toBeTrue();
        expect(attachmentVideoUnitFormComponent.playlistUrl()).toContain('playlist.m3u8');
    });

    it('should disable generateTranscript checkbox when playlist is unavailable', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();
        const originalUrl = 'https://live.rbg.tum.de/w/test/26';
        attachmentVideoUnitFormComponent.videoSourceControl!.setValue(originalUrl);

        const httpMock = TestBed.inject(HttpClient);
        const spy = jest.spyOn(httpMock, 'get').mockReturnValue(throwError(() => new Error('Not found')));

        attachmentVideoUnitFormComponent.checkPlaylistAvailability(originalUrl);

        expect(spy).toHaveBeenCalled();
        expect(attachmentVideoUnitFormComponent.canGenerateTranscript()).toBeFalse();
        expect(attachmentVideoUnitFormComponent.playlistUrl()).toBeUndefined();
        expect(attachmentVideoUnitFormComponent.form.get('generateTranscript')?.value).toBeFalse();
    });
    it('should show transcript checkbox only when playlistUrl is set', () => {
        // Initially hidden
        expect(attachmentVideoUnitFormComponent.shouldShowTranscriptCheckbox()).toBeFalse();

        // Simulate playlist found
        attachmentVideoUnitFormComponent.playlistUrl.set('https://live.rbg.tum.de/playlist.m3u8');
        expect(attachmentVideoUnitFormComponent.shouldShowTranscriptCheckbox()).toBeTrue();

        // Simulate playlist removed
        attachmentVideoUnitFormComponent.playlistUrl.set(undefined);
        expect(attachmentVideoUnitFormComponent.shouldShowTranscriptCheckbox()).toBeFalse();
    });

    it('should update checkbox visibility after successful playlist fetch', () => {
        const originalUrl = 'https://live.rbg.tum.de/w/test/26';
        const http = TestBed.inject(HttpClient);
        jest.spyOn(http, 'get').mockReturnValue(of('https://live.rbg.tum.de/playlist.m3u8'));

        attachmentVideoUnitFormComponent.checkPlaylistAvailability(originalUrl);

        expect(attachmentVideoUnitFormComponent.playlistUrl()).toContain('playlist.m3u8');
        expect(attachmentVideoUnitFormComponent.shouldShowTranscriptCheckbox()).toBeTrue();
    });

    it('should hide checkbox and reset generateTranscript after failed playlist fetch', () => {
        const originalUrl = 'https://live.rbg.tum.de/w/test/26';
        attachmentVideoUnitFormComponent.form.get('generateTranscript')!.setValue(true);

        const http = TestBed.inject(HttpClient);
        jest.spyOn(http, 'get').mockReturnValue(throwError(() => new Error('Not found')));

        attachmentVideoUnitFormComponent.checkPlaylistAvailability(originalUrl);

        expect(attachmentVideoUnitFormComponent.playlistUrl()).toBeUndefined();
        expect(attachmentVideoUnitFormComponent.shouldShowTranscriptCheckbox()).toBeFalse();
        expect(attachmentVideoUnitFormComponent.form.get('generateTranscript')!.value).toBeFalse();
    });
    it('videoSourceUrlValidator: rejects TUM-Live without video_only=1, accepts others', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();

        // TUM-Live without ?video_only=1 -> invalid
        attachmentVideoUnitFormComponent.videoSourceControl!.setValue('https://live.rbg.tum.de/w/test/26');
        expect(attachmentVideoUnitFormComponent.videoSourceControl!.errors).toEqual({ invalidVideoUrl: true });

        // TUM-Live with ?video_only=1 -> valid
        attachmentVideoUnitFormComponent.videoSourceControl!.setValue('https://live.rbg.tum.de/w/test/26?video_only=1');
        expect(attachmentVideoUnitFormComponent.videoSourceControl!.errors).toBeNull();

        // Non TUM-Live arbitrary valid URL -> valid
        attachmentVideoUnitFormComponent.videoSourceControl!.setValue('https://example.com/video');
        expect(attachmentVideoUnitFormComponent.videoSourceControl!.errors).toBeNull();
    });

    it('videoSourceTransformUrlValidator: accepts TUM-Live and known providers, rejects garbage', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();

        // Valid TUM-Live
        attachmentVideoUnitFormComponent.urlHelperControl!.setValue('https://live.rbg.tum.de/w/test/26');
        expect(attachmentVideoUnitFormComponent.urlHelperControl!.errors).toBeNull();

        // Valid YouTube (parsable by js-video-url-parser)
        attachmentVideoUnitFormComponent.urlHelperControl!.setValue('https://www.youtube.com/watch?v=dQw4w9WgXcQ');
        expect(attachmentVideoUnitFormComponent.urlHelperControl!.errors).toBeNull();

        // Invalid / unparsable
        attachmentVideoUnitFormComponent.urlHelperControl!.setValue('not-a-url');
        expect(attachmentVideoUnitFormComponent.urlHelperControl!.errors).toEqual({ invalidVideoUrl: true });
    });

    it('extractEmbeddedUrl: adds video_only=1 for TUM-Live and transforms YouTube to embed', () => {
        const tumUrl = 'https://live.rbg.tum.de/w/test/26';
        const transformedTum = attachmentVideoUnitFormComponent.extractEmbeddedUrl(tumUrl);
        expect(transformedTum).toBe('https://live.rbg.tum.de/w/test/26?video_only=1');

        const ytWatch = 'https://www.youtube.com/watch?v=8iU8LPEa4o0';
        const ytEmbed = attachmentVideoUnitFormComponent.extractEmbeddedUrl(ytWatch);
        expect(ytEmbed).toBe('https://www.youtube.com/embed/8iU8LPEa4o0');
    });

    it('setEmbeddedVideoUrl: uses urlHelper, sets videoSource, and checks playlist with ORIGINAL URL', () => {
        const original = 'https://live.rbg.tum.de/w/test/26';
        const embedded = 'https://live.rbg.tum.de/w/test/26?video_only=1';

        // spy extract + playlist check
        const extractSpy = jest.spyOn(attachmentVideoUnitFormComponent, 'extractEmbeddedUrl').mockReturnValue(embedded);
        const checkSpy = jest.spyOn(attachmentVideoUnitFormComponent, 'checkPlaylistAvailability');

        attachmentVideoUnitFormComponentFixture.detectChanges();
        attachmentVideoUnitFormComponent.urlHelperControl!.setValue(original);

        const stopPropagation = jest.fn();
        attachmentVideoUnitFormComponent.setEmbeddedVideoUrl({ stopPropagation } as any);

        expect(stopPropagation).toHaveBeenCalled();
        expect(extractSpy).toHaveBeenCalledWith(original);
        expect(attachmentVideoUnitFormComponent.videoSourceControl!.value).toBe(embedded);
        // IMPORTANT: check should be called with the original URL, not the embedded one
        expect(checkSpy).toHaveBeenCalledWith(original);

        extractSpy.mockRestore();
        checkSpy.mockRestore();
    });

    it('checkTumLivePlaylist: non-TUM hosts disable transcript, clear playlist, and reset checkbox', fakeAsync(() => {
        attachmentVideoUnitFormComponentFixture.detectChanges();
        // Pre-set to ensure reset occurs
        attachmentVideoUnitFormComponent.canGenerateTranscript.set(true);
        attachmentVideoUnitFormComponent.playlistUrl.set('https://some/playlist.m3u8');
        attachmentVideoUnitFormComponent.form.get('generateTranscript')!.setValue(true);

        // Non TUM-Live URL
        const nonTumUrl = 'https://example.com/video/123';

        // Mock the service to return null (no playlist found for non-TUM URLs)
        const http = TestBed.inject(HttpClient);
        jest.spyOn(http, 'get').mockReturnValue(of(null));

        attachmentVideoUnitFormComponent.checkPlaylistAvailability(nonTumUrl);

        // Wait for async operations to complete
        flushMicrotasks();

        expect(attachmentVideoUnitFormComponent.canGenerateTranscript()).toBeFalse();
        expect(attachmentVideoUnitFormComponent.playlistUrl()).toBeUndefined();
        expect(attachmentVideoUnitFormComponent.form.get('generateTranscript')!.value).toBeFalse();
    }));

    it('onFileChange: auto-fills name when empty and marks large files', fakeAsync(() => {
        attachmentVideoUnitFormComponentFixture.detectChanges();

        // Name initially empty -> should be auto-filled without extension
        expect(attachmentVideoUnitFormComponent.nameControl!.value).toBeFalsy();

        const bigFile = new File(['a'.repeat(10)], 'Lecture-01.mp4', { type: 'video/mp4', lastModified: Date.now() });
        // Video files use MAX_VIDEO_FILE_SIZE (200MB), not MAX_FILE_SIZE (20MB)
        Object.defineProperty(bigFile, 'size', { value: MAX_VIDEO_FILE_SIZE + 10 });

        const input = document.createElement('input');
        Object.defineProperty(input, 'files', { value: [bigFile] });

        attachmentVideoUnitFormComponent.onFileChange({ target: input } as any);

        // Wait for the setTimeout in onFileChange to complete (1000ms for file processing + 1000ms for reset)
        tick(1100);

        expect(attachmentVideoUnitFormComponent.fileName()).toBe('Lecture-01.mp4');
        expect(attachmentVideoUnitFormComponent.nameControl!.value).toBe('Lecture-01');
        expect(attachmentVideoUnitFormComponent.isFileTooBig()).toBeTrue();
    }));

    it('isTransformable reflects urlHelper validity', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();

        // Empty -> false
        attachmentVideoUnitFormComponent.urlHelperControl!.setValue('');
        expect(attachmentVideoUnitFormComponent.isTransformable).toBeFalse();

        // Invalid -> false
        attachmentVideoUnitFormComponent.urlHelperControl!.setValue('not-a-url');
        expect(attachmentVideoUnitFormComponent.isTransformable).toBeFalse();

        // Valid -> true
        attachmentVideoUnitFormComponent.urlHelperControl!.setValue('https://www.youtube.com/watch?v=dQw4w9WgXcQ');
        expect(attachmentVideoUnitFormComponent.isTransformable).toBeTrue();
    });
    it('submitForm includes playlistUrl when present', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();

        attachmentVideoUnitFormComponent.nameControl!.setValue('Unit B');
        attachmentVideoUnitFormComponent.versionControl!.enable();
        attachmentVideoUnitFormComponent.versionControl!.setValue(1);
        attachmentVideoUnitFormComponent.videoSourceControl!.setValue('https://www.youtube.com/embed/8iU8LPEa4o0');

        // set a playlist URL to ensure it’s propagated
        const playlist = 'https://live.rbg.tum.de/playlist.m3u8';
        attachmentVideoUnitFormComponent.playlistUrl.set(playlist);

        const emitSpy = jest.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

        attachmentVideoUnitFormComponent.submitForm();

        expect(emitSpy).toHaveBeenCalledOnce();
        const payload = emitSpy.mock.calls[0][0] as AttachmentVideoUnitFormData;
        expect(payload.playlistUrl).toBe(playlist);

        emitSpy.mockRestore();
    });

    it('should set playlist URL from formData in edit mode via effect', () => {
        attachmentVideoUnitFormComponentFixture.componentRef.setInput('isEditMode', true);
        attachmentVideoUnitFormComponentFixture.detectChanges();

        const playlistUrl = 'https://live.rbg.tum.de/playlist.m3u8';
        const formDataWithPlaylist: AttachmentVideoUnitFormData = {
            formProperties: {
                name: 'test',
            },
            fileProperties: {},
            playlistUrl: playlistUrl,
        };

        attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', formDataWithPlaylist);
        attachmentVideoUnitFormComponentFixture.detectChanges();

        // Effect should have triggered and set the playlist URL
        expect(attachmentVideoUnitFormComponent.playlistUrl()).toBe(playlistUrl);
        expect(attachmentVideoUnitFormComponent.canGenerateTranscript()).toBeTrue();
    });

    it('should update playlist URL when formData changes in edit mode', () => {
        attachmentVideoUnitFormComponentFixture.componentRef.setInput('isEditMode', true);
        attachmentVideoUnitFormComponentFixture.detectChanges();

        // Initial formData without playlist
        const formDataWithoutPlaylist: AttachmentVideoUnitFormData = {
            formProperties: {
                name: 'test',
            },
            fileProperties: {},
        };
        attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', formDataWithoutPlaylist);
        attachmentVideoUnitFormComponentFixture.detectChanges();

        expect(attachmentVideoUnitFormComponent.playlistUrl()).toBeUndefined();

        // Update formData with playlist URL
        const playlistUrl = 'https://live.rbg.tum.de/playlist.m3u8';
        const formDataWithPlaylist: AttachmentVideoUnitFormData = {
            ...formDataWithoutPlaylist,
            playlistUrl: playlistUrl,
        };
        attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', formDataWithPlaylist);
        attachmentVideoUnitFormComponentFixture.detectChanges();

        // Effect should have updated the playlist URL
        expect(attachmentVideoUnitFormComponent.playlistUrl()).toBe(playlistUrl);
        expect(attachmentVideoUnitFormComponent.canGenerateTranscript()).toBeTrue();
    });

    describe('Video Upload Feature Flag', () => {
        it('should invalidate form when video upload is disabled and video file is selected', () => {
            // Mock ProfileService to return false for video upload
            const profileService = TestBed.inject(ProfileService);
            jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

            // Create a new component instance to pick up the mock
            attachmentVideoUnitFormComponentFixture = TestBed.createComponent(AttachmentVideoUnitFormComponent);
            attachmentVideoUnitFormComponent = attachmentVideoUnitFormComponentFixture.componentInstance;
            attachmentVideoUnitFormComponentFixture.detectChanges();

            // Set a video file in the dedicated video file field
            const videoFile = new File(['test'], 'test-video.mp4', { type: 'video/mp4' });
            attachmentVideoUnitFormComponent.videoFile = videoFile;
            attachmentVideoUnitFormComponent.videoFileName.set('test-video.mp4');
            // Mark as user-touched to simulate actual file selection (not pre-populated edit mode data)
            attachmentVideoUnitFormComponent.videoFileInputTouched = true;
            attachmentVideoUnitFormComponent.form.patchValue({ name: 'Test Video Unit' });

            // Form should be invalid because video upload is disabled
            expect(attachmentVideoUnitFormComponent.isFormValid()).toBeFalse();
        });

        it('should allow PDF upload when video upload is disabled', () => {
            // Mock ProfileService to return false for video upload
            const profileService = TestBed.inject(ProfileService);
            jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

            // Create a new component instance
            attachmentVideoUnitFormComponentFixture = TestBed.createComponent(AttachmentVideoUnitFormComponent);
            attachmentVideoUnitFormComponent = attachmentVideoUnitFormComponentFixture.componentInstance;
            attachmentVideoUnitFormComponentFixture.detectChanges();

            // Set a PDF file
            const pdfFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
            attachmentVideoUnitFormComponent.file = pdfFile;
            attachmentVideoUnitFormComponent.fileName.set('test.pdf');
            attachmentVideoUnitFormComponent.form.patchValue({ name: 'Test Unit' });

            // Form should be valid (assuming other validations pass)
            expect(attachmentVideoUnitFormComponent.isFormValid()).toBeTrue();
        });

        it('should allow video upload when feature is enabled', () => {
            // Mock ProfileService to return true for video upload
            const profileService = TestBed.inject(ProfileService);
            jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

            // Create a new component instance
            attachmentVideoUnitFormComponentFixture = TestBed.createComponent(AttachmentVideoUnitFormComponent);
            attachmentVideoUnitFormComponent = attachmentVideoUnitFormComponentFixture.componentInstance;
            attachmentVideoUnitFormComponentFixture.detectChanges();

            // Set a video file in the dedicated video file field
            const videoFile = new File(['test'], 'test-video.mp4', { type: 'video/mp4' });
            attachmentVideoUnitFormComponent.videoFile = videoFile;
            attachmentVideoUnitFormComponent.videoFileName.set('test-video.mp4');
            // Mark as user-touched to simulate actual file selection
            attachmentVideoUnitFormComponent.videoFileInputTouched = true;
            attachmentVideoUnitFormComponent.form.patchValue({ name: 'Test Video Unit' });

            // Form should be valid
            expect(attachmentVideoUnitFormComponent.isFormValid()).toBeTrue();
        });

        it('should allow editing pre-populated video data when upload feature is disabled', () => {
            // Mock ProfileService to return false for video upload
            const profileService = TestBed.inject(ProfileService);
            jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

            // Create a new component instance
            attachmentVideoUnitFormComponentFixture = TestBed.createComponent(AttachmentVideoUnitFormComponent);
            attachmentVideoUnitFormComponent = attachmentVideoUnitFormComponentFixture.componentInstance;
            attachmentVideoUnitFormComponentFixture.componentRef.setInput('isEditMode', true);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            // Set up form data as if loaded from server (pre-populated, NOT user-touched)
            const formData: AttachmentVideoUnitFormData = {
                formProperties: { name: 'Existing Video Unit' },
                fileProperties: {},
                videoFileProperties: { videoFileName: 'existing-video.mp4', videoFile: undefined },
            };
            attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', formData);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            // videoFileInputTouched should remain false (set via setFormValues, not user interaction)
            expect(attachmentVideoUnitFormComponent.videoFileInputTouched).toBeFalse();

            // Form should be valid because the video file was pre-populated, not user-selected
            expect(attachmentVideoUnitFormComponent.isFormValid()).toBeTrue();
        });
    });

    describe('Video File Upload', () => {
        it('should call onVideoFileChange when video file input changes', () => {
            attachmentVideoUnitFormComponentFixture.detectChanges();
            const onVideoFileChangeSpy = jest.spyOn(attachmentVideoUnitFormComponent, 'onVideoFileChange');

            const videoFileInput = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#videoFileInput');
            if (videoFileInput) {
                videoFileInput.dispatchEvent(new Event('change'));
                expect(onVideoFileChangeSpy).toHaveBeenCalledOnce();
            }
        });

        it('should set video file properties when video file is selected', () => {
            attachmentVideoUnitFormComponentFixture.detectChanges();

            const videoFile = new File(['video content'], 'test-video.mp4', { type: 'video/mp4' });
            const input = document.createElement('input');
            Object.defineProperty(input, 'files', { value: [videoFile] });

            attachmentVideoUnitFormComponent.onVideoFileChange({ target: input } as unknown as Event);

            expect(attachmentVideoUnitFormComponent.videoFile).toBe(videoFile);
            expect(attachmentVideoUnitFormComponent.videoFileName()).toBe('test-video.mp4');
            expect(attachmentVideoUnitFormComponent.videoFileInputTouched).toBeTrue();
        });

        it('should auto-fill name when video file is selected and name is empty', () => {
            attachmentVideoUnitFormComponentFixture.detectChanges();
            expect(attachmentVideoUnitFormComponent.nameControl!.value).toBeFalsy();

            const videoFile = new File(['video content'], 'My-Lecture-Video.mp4', { type: 'video/mp4' });
            const input = document.createElement('input');
            Object.defineProperty(input, 'files', { value: [videoFile] });

            attachmentVideoUnitFormComponent.onVideoFileChange({ target: input } as unknown as Event);

            // Name should be auto-filled without extension
            expect(attachmentVideoUnitFormComponent.nameControl!.value).toBe('My-Lecture-Video');
        });

        it('should not overwrite name when video file is selected and name already exists', () => {
            attachmentVideoUnitFormComponentFixture.detectChanges();
            attachmentVideoUnitFormComponent.form.patchValue({ name: 'Existing Name' });

            const videoFile = new File(['video content'], 'New-Video.mp4', { type: 'video/mp4' });
            const input = document.createElement('input');
            Object.defineProperty(input, 'files', { value: [videoFile] });

            attachmentVideoUnitFormComponent.onVideoFileChange({ target: input } as unknown as Event);

            // Name should not be overwritten
            expect(attachmentVideoUnitFormComponent.nameControl!.value).toBe('Existing Name');
        });

        it('should mark video file as too big when exceeding max size', () => {
            attachmentVideoUnitFormComponentFixture.detectChanges();

            const largeVideoFile = new File([''], 'large-video.mp4', { type: 'video/mp4' });
            Object.defineProperty(largeVideoFile, 'size', { value: MAX_VIDEO_FILE_SIZE + 1 });
            const input = document.createElement('input');
            Object.defineProperty(input, 'files', { value: [largeVideoFile] });

            attachmentVideoUnitFormComponent.onVideoFileChange({ target: input } as unknown as Event);

            expect(attachmentVideoUnitFormComponent.isVideoFileTooBig()).toBeTrue();
        });

        it('should not mark video file as too big when within max size', () => {
            attachmentVideoUnitFormComponentFixture.detectChanges();

            const normalVideoFile = new File(['video content'], 'normal-video.mp4', { type: 'video/mp4' });
            Object.defineProperty(normalVideoFile, 'size', { value: MAX_VIDEO_FILE_SIZE - 1 });
            const input = document.createElement('input');
            Object.defineProperty(input, 'files', { value: [normalVideoFile] });

            attachmentVideoUnitFormComponent.onVideoFileChange({ target: input } as unknown as Event);

            expect(attachmentVideoUnitFormComponent.isVideoFileTooBig()).toBeFalse();
        });

        it('should not process video file when no files are selected', () => {
            attachmentVideoUnitFormComponentFixture.detectChanges();

            const input = document.createElement('input');
            Object.defineProperty(input, 'files', { value: [] });

            attachmentVideoUnitFormComponent.onVideoFileChange({ target: input } as unknown as Event);

            expect(attachmentVideoUnitFormComponent.videoFile).toBeUndefined();
            expect(attachmentVideoUnitFormComponent.videoFileName()).toBeUndefined();
        });

        it('should include videoFileProperties in form submission', () => {
            const profileService = TestBed.inject(ProfileService);
            jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

            attachmentVideoUnitFormComponentFixture = TestBed.createComponent(AttachmentVideoUnitFormComponent);
            attachmentVideoUnitFormComponent = attachmentVideoUnitFormComponentFixture.componentInstance;
            attachmentVideoUnitFormComponentFixture.detectChanges();

            // Set up valid form data
            attachmentVideoUnitFormComponent.form.patchValue({ name: 'Test Unit' });

            // Set video file
            const videoFile = new File(['video content'], 'test-video.mp4', { type: 'video/mp4' });
            attachmentVideoUnitFormComponent.videoFile = videoFile;
            attachmentVideoUnitFormComponent.videoFileName.set('test-video.mp4');
            attachmentVideoUnitFormComponent.videoFileInputTouched = true;

            const submitFormEventSpy = jest.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

            attachmentVideoUnitFormComponent.submitForm();

            expect(submitFormEventSpy).toHaveBeenCalledOnce();
            const emittedData = submitFormEventSpy.mock.calls[0][0] as AttachmentVideoUnitFormData;
            expect(emittedData.videoFileProperties).toBeDefined();
            expect(emittedData.videoFileProperties!.videoFile).toBe(videoFile);
            expect(emittedData.videoFileProperties!.videoFileName).toBe('test-video.mp4');
        });

        it('should invalidate form when video file is too big', () => {
            const profileService = TestBed.inject(ProfileService);
            jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

            attachmentVideoUnitFormComponentFixture = TestBed.createComponent(AttachmentVideoUnitFormComponent);
            attachmentVideoUnitFormComponent = attachmentVideoUnitFormComponentFixture.componentInstance;
            attachmentVideoUnitFormComponentFixture.detectChanges();

            // Set up valid form data
            attachmentVideoUnitFormComponent.form.patchValue({ name: 'Test Unit' });

            // Set video file that is too big
            const largeVideoFile = new File([''], 'large-video.mp4', { type: 'video/mp4' });
            Object.defineProperty(largeVideoFile, 'size', { value: MAX_VIDEO_FILE_SIZE + 1 });
            attachmentVideoUnitFormComponent.videoFile = largeVideoFile;
            attachmentVideoUnitFormComponent.videoFileName.set('large-video.mp4');
            attachmentVideoUnitFormComponent.isVideoFileTooBig.set(true);
            attachmentVideoUnitFormComponent.videoFileInputTouched = true;

            expect(attachmentVideoUnitFormComponent.isFormValid()).toBeFalse();
        });
    });

    describe('Upload Progress Tracking', () => {
        it('should include uploadProgressCallback in form submission', () => {
            attachmentVideoUnitFormComponentFixture.detectChanges();
            attachmentVideoUnitFormComponent.form.patchValue({ name: 'Test Unit' });

            // Set a PDF file for valid form
            const pdfFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
            attachmentVideoUnitFormComponent.file = pdfFile;
            attachmentVideoUnitFormComponent.fileName.set('test.pdf');

            const submitFormEventSpy = jest.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

            attachmentVideoUnitFormComponent.submitForm();

            expect(submitFormEventSpy).toHaveBeenCalledOnce();
            const emittedData = submitFormEventSpy.mock.calls[0][0] as AttachmentVideoUnitFormData;
            expect(emittedData.uploadProgressCallback).toBeDefined();
            expect(typeof emittedData.uploadProgressCallback).toBe('function');
        });

        it('should update upload progress signals when callback is invoked', () => {
            attachmentVideoUnitFormComponentFixture.detectChanges();
            attachmentVideoUnitFormComponent.form.patchValue({ name: 'Test Unit' });

            const pdfFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
            attachmentVideoUnitFormComponent.file = pdfFile;
            attachmentVideoUnitFormComponent.fileName.set('test.pdf');

            const submitFormEventSpy = jest.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

            attachmentVideoUnitFormComponent.submitForm();

            const emittedData = submitFormEventSpy.mock.calls[0][0] as AttachmentVideoUnitFormData;
            const callback = emittedData.uploadProgressCallback!;

            // Invoke callback with progress
            callback(50, 'Uploading...');

            expect(attachmentVideoUnitFormComponent.isUploading()).toBeTrue();
            expect(attachmentVideoUnitFormComponent.uploadProgress()).toBe(50);
            expect(attachmentVideoUnitFormComponent.uploadStatus()).toBe('Uploading...');
        });

        it('should reset upload state after 100% progress', fakeAsync(() => {
            attachmentVideoUnitFormComponentFixture.detectChanges();
            attachmentVideoUnitFormComponent.form.patchValue({ name: 'Test Unit' });

            const pdfFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
            attachmentVideoUnitFormComponent.file = pdfFile;
            attachmentVideoUnitFormComponent.fileName.set('test.pdf');

            const submitFormEventSpy = jest.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

            attachmentVideoUnitFormComponent.submitForm();

            const emittedData = submitFormEventSpy.mock.calls[0][0] as AttachmentVideoUnitFormData;
            const callback = emittedData.uploadProgressCallback!;

            // Invoke callback with 100% progress
            callback(100, 'Complete');

            expect(attachmentVideoUnitFormComponent.isUploading()).toBeTrue();
            expect(attachmentVideoUnitFormComponent.uploadProgress()).toBe(100);

            // Wait for timeout to reset state
            tick(1000);

            expect(attachmentVideoUnitFormComponent.isUploading()).toBeFalse();
            expect(attachmentVideoUnitFormComponent.uploadProgress()).toBe(0);
            expect(attachmentVideoUnitFormComponent.uploadStatus()).toBe('');
        }));

        it('should clear previous timeout when multiple 100% callbacks are received', fakeAsync(() => {
            attachmentVideoUnitFormComponentFixture.detectChanges();
            attachmentVideoUnitFormComponent.form.patchValue({ name: 'Test Unit' });

            const pdfFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
            attachmentVideoUnitFormComponent.file = pdfFile;
            attachmentVideoUnitFormComponent.fileName.set('test.pdf');

            const submitFormEventSpy = jest.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

            attachmentVideoUnitFormComponent.submitForm();

            const emittedData = submitFormEventSpy.mock.calls[0][0] as AttachmentVideoUnitFormData;
            const callback = emittedData.uploadProgressCallback!;

            // First 100% callback
            callback(100, 'Complete');
            tick(500); // Wait 500ms

            // Second 100% callback (simulating edge case)
            callback(100, 'Complete again');
            tick(500); // Wait another 500ms - first timeout would have fired here if not cleared

            // State should still be uploading (second timeout hasn't completed yet)
            expect(attachmentVideoUnitFormComponent.isUploading()).toBeTrue();

            tick(500); // Complete the second timeout

            expect(attachmentVideoUnitFormComponent.isUploading()).toBeFalse();
        }));
    });

    describe('ngOnDestroy', () => {
        it('should clean up timeout on component destruction', fakeAsync(() => {
            attachmentVideoUnitFormComponentFixture.detectChanges();
            attachmentVideoUnitFormComponent.form.patchValue({ name: 'Test Unit' });

            const pdfFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
            attachmentVideoUnitFormComponent.file = pdfFile;
            attachmentVideoUnitFormComponent.fileName.set('test.pdf');

            const submitFormEventSpy = jest.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

            attachmentVideoUnitFormComponent.submitForm();

            const emittedData = submitFormEventSpy.mock.calls[0][0] as AttachmentVideoUnitFormData;
            const callback = emittedData.uploadProgressCallback!;

            // Invoke callback with 100% progress to schedule timeout
            callback(100, 'Complete');

            // Destroy the component before timeout completes
            attachmentVideoUnitFormComponent.ngOnDestroy();

            // Advance timer - should not throw or cause issues
            tick(1000);

            // Component should be cleaned up without errors
            expect(true).toBeTrue();
        }));
    });

    describe('setFormValues with videoFileProperties', () => {
        it('should set video file properties from form data', () => {
            attachmentVideoUnitFormComponentFixture.componentRef.setInput('isEditMode', true);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            const videoFile = new File(['video'], 'existing-video.mp4', { type: 'video/mp4' });
            const formData: AttachmentVideoUnitFormData = {
                formProperties: { name: 'Test Unit' },
                fileProperties: {},
                videoFileProperties: {
                    videoFile: videoFile,
                    videoFileName: 'existing-video.mp4',
                },
            };

            attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', formData);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(attachmentVideoUnitFormComponent.videoFile).toBe(videoFile);
            expect(attachmentVideoUnitFormComponent.videoFileName()).toBe('existing-video.mp4');
        });

        it('should handle formData without videoFileProperties', () => {
            attachmentVideoUnitFormComponentFixture.componentRef.setInput('isEditMode', true);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            const formData: AttachmentVideoUnitFormData = {
                formProperties: { name: 'Test Unit' },
                fileProperties: {},
            };

            attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', formData);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            // Should not throw and values should remain undefined
            expect(attachmentVideoUnitFormComponent.videoFile).toBeUndefined();
            expect(attachmentVideoUnitFormComponent.videoFileName()).toBeUndefined();
        });
    });
});
