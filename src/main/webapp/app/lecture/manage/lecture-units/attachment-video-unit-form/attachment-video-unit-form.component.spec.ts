import { ComponentFixture, TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { TranscriptionStatus } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';

describe('AttachmentVideoUnitFormComponent', () => {
    let attachmentVideoUnitFormComponentFixture: ComponentFixture<AttachmentVideoUnitFormComponent>;
    let attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent;

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
            ],
        }).compileComponents();

        attachmentVideoUnitFormComponentFixture = TestBed.createComponent(AttachmentVideoUnitFormComponent);
        attachmentVideoUnitFormComponent = attachmentVideoUnitFormComponentFixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();
        expect(attachmentVideoUnitFormComponent).not.toBeNull();
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
        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();

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
        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();
        expect(attachmentVideoUnitFormComponent.transcriptionStatus()).toBe(TranscriptionStatus.PENDING);

        const formDataWithoutStatus: AttachmentVideoUnitFormData = {
            formProperties: {},
            fileProperties: {},
        };
        attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', formDataWithoutStatus);
        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();
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
        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();

        expect(attachmentVideoUnitFormComponent.transcriptionStatus()).toBe(TranscriptionStatus.PENDING);
        expect(attachmentVideoUnitFormComponent.showTranscriptionPendingWarning()).toBeTrue();

        attachmentVideoUnitFormComponentFixture.componentRef.setInput('isEditMode', false);
        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();

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

        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();
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
            },
            fileProperties: {
                file: fakeFile,
                fileName: exampleFileName,
            },
            playlistUrl: undefined,
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

    it('should disable submit button for too big file', () => {
        const fakeFile = new File([''], 'Test-File.pdf', {
            type: 'application/pdf',
            lastModified: Date.now(),
        });

        // Set file size to exceed the maximum file size
        Object.defineProperty(fakeFile, 'size', { value: MAX_FILE_SIZE + 1 });

        attachmentVideoUnitFormComponent.onFileChange({
            target: { files: [fakeFile] } as unknown as EventTarget,
        } as Event);
        attachmentVideoUnitFormComponentFixture.detectChanges();

        const submitButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        expect(attachmentVideoUnitFormComponent.isFileTooBig()).toBeTrue();
        expect(submitButton.disabled).toBeTrue();
    });

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
        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();

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

        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();

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
            },
            fileProperties: {
                file: undefined,
                fileName: undefined,
            },
            playlistUrl: undefined,
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

        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();

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
            },
            fileProperties: {
                file: fakeFile,
                fileName: exampleFileName,
            },
            playlistUrl: undefined,
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
        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();
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
        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();

        const transformButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#transformButton');
        transformButton.click();

        return attachmentVideoUnitFormComponentFixture.whenStable().then(() => {
            expect(attachmentVideoUnitFormComponent.videoSourceControl?.value).toEqual(expectedUrl);
        });
    });

    it('should set playlist URL when playlist is available', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();
        const originalUrl = 'https://live.rbg.tum.de/w/test/26';
        attachmentVideoUnitFormComponent.videoSourceControl!.setValue(originalUrl);

        const httpMock = TestBed.inject(HttpClient);
        const spy = jest.spyOn(httpMock, 'get').mockReturnValue(of('https://live.rbg.tum.de/playlist.m3u8'));

        attachmentVideoUnitFormComponent.checkPlaylistAvailability(originalUrl);

        expect(spy).toHaveBeenCalled();
        expect(attachmentVideoUnitFormComponent.playlistUrl()).toContain('playlist.m3u8');
    });

    it('should clear playlist URL when playlist is unavailable', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();
        const originalUrl = 'https://live.rbg.tum.de/w/test/26';
        attachmentVideoUnitFormComponent.videoSourceControl!.setValue(originalUrl);

        const httpMock = TestBed.inject(HttpClient);
        const spy = jest.spyOn(httpMock, 'get').mockReturnValue(throwError(() => new Error('Not found')));

        attachmentVideoUnitFormComponent.checkPlaylistAvailability(originalUrl);

        expect(spy).toHaveBeenCalled();
        expect(attachmentVideoUnitFormComponent.playlistUrl()).toBeUndefined();
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

    it('checkPlaylistAvailability: clears playlist for null response', fakeAsync(() => {
        attachmentVideoUnitFormComponentFixture.detectChanges();
        // Pre-set to ensure reset occurs
        attachmentVideoUnitFormComponent.playlistUrl.set('https://some/playlist.m3u8');

        // Non TUM-Live URL
        const nonTumUrl = 'https://example.com/video/123';

        // Mock the service to return null (no playlist found for non-TUM URLs)
        const http = TestBed.inject(HttpClient);
        jest.spyOn(http, 'get').mockReturnValue(of(null));

        attachmentVideoUnitFormComponent.checkPlaylistAvailability(nonTumUrl);

        // Wait for async operations to complete
        flushMicrotasks();

        expect(attachmentVideoUnitFormComponent.playlistUrl()).toBeUndefined();
    }));

    it('onFileChange: auto-fills name when empty and marks large files', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();

        // Name initially empty -> should be auto-filled without extension
        expect(attachmentVideoUnitFormComponent.nameControl!.value).toBeFalsy();

        const bigFile = new File(['a'.repeat(10)], 'Lecture-01.mp4', { type: 'video/mp4', lastModified: Date.now() });
        Object.defineProperty(bigFile, 'size', { value: MAX_FILE_SIZE + 10 });

        const input = document.createElement('input');
        Object.defineProperty(input, 'files', { value: [bigFile] });

        attachmentVideoUnitFormComponent.onFileChange({ target: input } as any);

        expect(attachmentVideoUnitFormComponent.fileName()).toBe('Lecture-01.mp4');
        expect(attachmentVideoUnitFormComponent.nameControl!.value).toBe('Lecture-01');
        expect(attachmentVideoUnitFormComponent.isFileTooBig()).toBeTrue();
    });

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
        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();

        // Effect should have triggered and set the playlist URL
        expect(attachmentVideoUnitFormComponent.playlistUrl()).toBe(playlistUrl);
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
        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();

        expect(attachmentVideoUnitFormComponent.playlistUrl()).toBeUndefined();

        // Update formData with playlist URL
        const playlistUrl = 'https://live.rbg.tum.de/playlist.m3u8';
        const formDataWithPlaylist: AttachmentVideoUnitFormData = {
            ...formDataWithoutPlaylist,
            playlistUrl: playlistUrl,
        };
        attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', formDataWithPlaylist);
        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();

        // Effect should have updated the playlist URL
        expect(attachmentVideoUnitFormComponent.playlistUrl()).toBe(playlistUrl);
    });
});
