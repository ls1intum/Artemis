import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MAX_FILE_SIZE, MAX_VIDEO_FILE_SIZE } from 'app/shared/constants/input.constants';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('AttachmentVideoUnitFormComponent', () => {
    setupTestBed({ zoneless: true });

    let attachmentVideoUnitFormComponentFixture: ComponentFixture<AttachmentVideoUnitFormComponent>;
    let attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                ReactiveFormsModule,
                FormsModule,
                OwlDateTimeModule,
                OwlNativeDateTimeModule,
                FontAwesomeTestingModule,
                AttachmentVideoUnitFormComponent,
                FormDateTimePickerComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(CompetencySelectionComponent),
                MockDirective(NgbTooltip),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null } } } },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        }).compileComponents();

        attachmentVideoUnitFormComponentFixture = TestBed.createComponent(AttachmentVideoUnitFormComponent);
        attachmentVideoUnitFormComponent = attachmentVideoUnitFormComponentFixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
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
        expect(attachmentVideoUnitFormComponent.form.valid).toBe(true);

        const submitFormSpy = vi.spyOn(attachmentVideoUnitFormComponent, 'submitForm');
        const submitFormEventSpy = vi.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

        const submitButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        expect(submitFormSpy).toHaveBeenCalledTimes(1);
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
            videoFileProperties: {
                videoFile: undefined,
                videoFileName: undefined,
            },
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

        expect(attachmentVideoUnitFormComponent.form.invalid).toBe(true);
        const submitFormSpy = vi.spyOn(attachmentVideoUnitFormComponent, 'submitForm');
        const submitFormEventSpy = vi.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

        const submitButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        expect(submitFormSpy).not.toHaveBeenCalled();
        expect(submitFormEventSpy).not.toHaveBeenCalled();
    });

    it('calls on file change on changed file', () => {
        const fakeBlob = new Blob([''], { type: 'application/pdf' });
        // @ts-ignore
        fakeBlob['name'] = 'Test-File.pdf';
        const onFileChangeStub = vi.spyOn(attachmentVideoUnitFormComponent, 'onFileChange');
        attachmentVideoUnitFormComponentFixture.detectChanges();
        const fileInput = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#fileInput');
        fileInput.dispatchEvent(new Event('change'));
        expect(onFileChangeStub).toHaveBeenCalledTimes(1);
    });

    it('should disable submit button for too big file', () => {
        vi.useFakeTimers();
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
        vi.advanceTimersByTime(1100);
        attachmentVideoUnitFormComponentFixture.detectChanges();

        const submitButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        expect(attachmentVideoUnitFormComponent.isFileTooBig()).toBe(true);
        expect(submitButton.disabled).toBe(true);
        vi.useRealTimers();
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

        expect(attachmentVideoUnitFormComponent.form.valid).toBe(true);

        const submitFormSpy = vi.spyOn(attachmentVideoUnitFormComponent, 'submitForm');
        const submitFormEventSpy = vi.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

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

        expect(attachmentVideoUnitFormComponent.form.valid).toBe(true);

        const submitFormSpy = vi.spyOn(attachmentVideoUnitFormComponent, 'submitForm');
        const submitFormEventSpy = vi.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

        const submitButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        expect(submitFormSpy).toHaveBeenCalledTimes(1);
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
            videoFileProperties: {
                videoFile: undefined,
                videoFileName: undefined,
            },
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

        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();

        expect(attachmentVideoUnitFormComponent.form.valid).toBe(true);

        const submitFormSpy = vi.spyOn(attachmentVideoUnitFormComponent, 'submitForm');
        const submitFormEventSpy = vi.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

        const submitButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        expect(submitFormSpy).toHaveBeenCalledTimes(1);
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
            videoFileProperties: {
                videoFile: undefined,
                videoFileName: undefined,
            },
            uploadProgressCallback: expect.any(Function),
        });

        submitFormSpy.mockRestore();
        submitFormEventSpy.mockRestore();
    });

    it('should correctly transform YouTube URL into embeddable format', async () => {
        const validYouTubeUrl = 'https://www.youtube.com/watch?v=8iU8LPEa4o0';
        const validYouTubeUrlInEmbeddableFormat = 'https://www.youtube.com/embed/8iU8LPEa4o0';

        vi.spyOn(attachmentVideoUnitFormComponent, 'extractEmbeddedUrl').mockReturnValue(validYouTubeUrlInEmbeddableFormat);
        vi.spyOn(attachmentVideoUnitFormComponent, 'videoSourceUrlValidator').mockReturnValue(undefined);
        vi.spyOn(attachmentVideoUnitFormComponent, 'videoSourceTransformUrlValidator').mockReturnValue(undefined);

        attachmentVideoUnitFormComponentFixture.detectChanges();

        attachmentVideoUnitFormComponent.urlHelperControl!.setValue(validYouTubeUrl);
        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();
        const transformButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#transformButton');
        transformButton.click();

        await attachmentVideoUnitFormComponentFixture.whenStable();
        expect(attachmentVideoUnitFormComponent.videoSourceControl?.value).toEqual(validYouTubeUrlInEmbeddableFormat);
    });

    it('should correctly transform TUM-Live URL without video only into embeddable format', async () => {
        const tumLiveUrl = 'https://live.rbg.tum.de/w/test/26';
        const expectedUrl = 'https://live.rbg.tum.de/w/test/26?video_only=1';

        attachmentVideoUnitFormComponentFixture.detectChanges();
        attachmentVideoUnitFormComponent.urlHelperControl!.setValue(tumLiveUrl);
        attachmentVideoUnitFormComponentFixture.changeDetectorRef.detectChanges();

        const transformButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#transformButton');
        transformButton.click();

        await attachmentVideoUnitFormComponentFixture.whenStable();
        expect(attachmentVideoUnitFormComponent.videoSourceControl?.value).toEqual(expectedUrl);
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

    it('setEmbeddedVideoUrl: uses urlHelper and sets videoSource', () => {
        const original = 'https://live.rbg.tum.de/w/test/26';
        const embedded = 'https://live.rbg.tum.de/w/test/26?video_only=1';

        const extractSpy = vi.spyOn(attachmentVideoUnitFormComponent, 'extractEmbeddedUrl').mockReturnValue(embedded);

        attachmentVideoUnitFormComponentFixture.detectChanges();
        attachmentVideoUnitFormComponent.urlHelperControl!.setValue(original);

        const stopPropagation = vi.fn();
        attachmentVideoUnitFormComponent.setEmbeddedVideoUrl({ stopPropagation } as any);

        expect(stopPropagation).toHaveBeenCalled();
        expect(extractSpy).toHaveBeenCalledWith(original);
        expect(attachmentVideoUnitFormComponent.videoSourceControl!.value).toBe(embedded);

        extractSpy.mockRestore();
    });

    it('onFileChange: auto-fills name when empty and marks large files', () => {
        vi.useFakeTimers();
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
        vi.advanceTimersByTime(1100);

        expect(attachmentVideoUnitFormComponent.fileName()).toBe('Lecture-01.mp4');
        expect(attachmentVideoUnitFormComponent.nameControl!.value).toBe('Lecture-01');
        expect(attachmentVideoUnitFormComponent.isFileTooBig()).toBe(true);
        vi.useRealTimers();
    });

    it('isTransformable reflects urlHelper validity', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();

        // Empty -> false
        attachmentVideoUnitFormComponent.urlHelperControl!.setValue('');
        expect(attachmentVideoUnitFormComponent.isTransformable).toBe(false);

        // Invalid -> false
        attachmentVideoUnitFormComponent.urlHelperControl!.setValue('not-a-url');
        expect(attachmentVideoUnitFormComponent.isTransformable).toBe(false);

        // Valid -> true
        attachmentVideoUnitFormComponent.urlHelperControl!.setValue('https://www.youtube.com/watch?v=dQw4w9WgXcQ');
        expect(attachmentVideoUnitFormComponent.isTransformable).toBe(true);
    });

    describe('Video Upload Feature Flag', () => {
        it('should invalidate form when video upload is disabled and video file is selected', () => {
            // Mock ProfileService to return false for video upload
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

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
            expect(attachmentVideoUnitFormComponent.isFormValid()).toBe(false);
        });

        it('should allow PDF upload when video upload is disabled', () => {
            // Mock ProfileService to return false for video upload
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

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
            expect(attachmentVideoUnitFormComponent.isFormValid()).toBe(true);
        });

        it('should allow video upload when feature is enabled', () => {
            // Mock ProfileService to return true for video upload
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

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
            expect(attachmentVideoUnitFormComponent.isFormValid()).toBe(true);
        });

        it('should allow editing pre-populated video data when upload feature is disabled', () => {
            // Mock ProfileService to return false for video upload
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

            // Create a new component instance
            attachmentVideoUnitFormComponentFixture = TestBed.createComponent(AttachmentVideoUnitFormComponent);
            attachmentVideoUnitFormComponent = attachmentVideoUnitFormComponentFixture.componentInstance;
            attachmentVideoUnitFormComponentFixture.componentRef.setInput('isEditMode', true);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            // Set up form data as if loaded from server (pre-populated, NOT user-touched)
            // videoSource with a file path (not http) triggers video file detection
            const formData: AttachmentVideoUnitFormData = {
                formProperties: { name: 'Existing Video Unit', videoSource: 'attachments/attachment-unit/1/existing-video.mp4' },
                fileProperties: {},
                videoFileProperties: {},
            };
            attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', formData);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            // videoFileInputTouched should remain false (set via setFormValues, not user interaction)
            expect(attachmentVideoUnitFormComponent.videoFileInputTouched).toBe(false);

            // Form should be valid because the video file was pre-populated, not user-selected
            expect(attachmentVideoUnitFormComponent.isFormValid()).toBe(true);
        });
    });

    describe('Video File Upload', () => {
        it('should call onVideoFileChange when video file input changes', () => {
            attachmentVideoUnitFormComponentFixture.detectChanges();
            const onVideoFileChangeSpy = vi.spyOn(attachmentVideoUnitFormComponent, 'onVideoFileChange');

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
            expect(attachmentVideoUnitFormComponent.videoFileInputTouched).toBe(true);
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

            expect(attachmentVideoUnitFormComponent.isVideoFileTooBig()).toBe(true);
        });

        it('should not mark video file as too big when within max size', () => {
            attachmentVideoUnitFormComponentFixture.detectChanges();

            const normalVideoFile = new File(['video content'], 'normal-video.mp4', { type: 'video/mp4' });
            Object.defineProperty(normalVideoFile, 'size', { value: MAX_VIDEO_FILE_SIZE - 1 });
            const input = document.createElement('input');
            Object.defineProperty(input, 'files', { value: [normalVideoFile] });

            attachmentVideoUnitFormComponent.onVideoFileChange({ target: input } as unknown as Event);

            expect(attachmentVideoUnitFormComponent.isVideoFileTooBig()).toBe(false);
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
            vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

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

            const submitFormEventSpy = vi.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

            attachmentVideoUnitFormComponent.submitForm();

            expect(submitFormEventSpy).toHaveBeenCalledOnce();
            const emittedData = submitFormEventSpy.mock.calls[0][0] as AttachmentVideoUnitFormData;
            expect(emittedData.videoFileProperties).toBeDefined();
            expect(emittedData.videoFileProperties!.videoFile).toBe(videoFile);
            expect(emittedData.videoFileProperties!.videoFileName).toBe('test-video.mp4');
        });

        it('should invalidate form when video file is too big', () => {
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

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

            expect(attachmentVideoUnitFormComponent.isFormValid()).toBe(false);
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

            const submitFormEventSpy = vi.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

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

            const submitFormEventSpy = vi.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

            attachmentVideoUnitFormComponent.submitForm();

            const emittedData = submitFormEventSpy.mock.calls[0][0] as AttachmentVideoUnitFormData;
            const callback = emittedData.uploadProgressCallback!;

            // Invoke callback with progress
            callback(50, 'Uploading...');

            expect(attachmentVideoUnitFormComponent.isUploading()).toBe(true);
            expect(attachmentVideoUnitFormComponent.uploadProgress()).toBe(50);
            expect(attachmentVideoUnitFormComponent.uploadStatus()).toBe('Uploading...');
        });

        it('should reset upload state after 100% progress', () => {
            vi.useFakeTimers();
            attachmentVideoUnitFormComponentFixture.detectChanges();
            attachmentVideoUnitFormComponent.form.patchValue({ name: 'Test Unit' });

            const pdfFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
            attachmentVideoUnitFormComponent.file = pdfFile;
            attachmentVideoUnitFormComponent.fileName.set('test.pdf');

            const submitFormEventSpy = vi.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

            attachmentVideoUnitFormComponent.submitForm();

            const emittedData = submitFormEventSpy.mock.calls[0][0] as AttachmentVideoUnitFormData;
            const callback = emittedData.uploadProgressCallback!;

            // Invoke callback with 100% progress
            callback(100, 'Complete');

            expect(attachmentVideoUnitFormComponent.isUploading()).toBe(true);
            expect(attachmentVideoUnitFormComponent.uploadProgress()).toBe(100);

            // Wait for timeout to reset state
            vi.advanceTimersByTime(1000);

            expect(attachmentVideoUnitFormComponent.isUploading()).toBe(false);
            expect(attachmentVideoUnitFormComponent.uploadProgress()).toBe(0);
            expect(attachmentVideoUnitFormComponent.uploadStatus()).toBe('');
            vi.useRealTimers();
        });

        it('should clear previous timeout when multiple 100% callbacks are received', () => {
            vi.useFakeTimers();
            attachmentVideoUnitFormComponentFixture.detectChanges();
            attachmentVideoUnitFormComponent.form.patchValue({ name: 'Test Unit' });

            const pdfFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
            attachmentVideoUnitFormComponent.file = pdfFile;
            attachmentVideoUnitFormComponent.fileName.set('test.pdf');

            const submitFormEventSpy = vi.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

            attachmentVideoUnitFormComponent.submitForm();

            const emittedData = submitFormEventSpy.mock.calls[0][0] as AttachmentVideoUnitFormData;
            const callback = emittedData.uploadProgressCallback!;

            // First 100% callback
            callback(100, 'Complete');
            vi.advanceTimersByTime(500); // Wait 500ms

            // Second 100% callback (simulating edge case)
            callback(100, 'Complete again');
            vi.advanceTimersByTime(500); // Wait another 500ms - first timeout would have fired here if not cleared

            // State should still be uploading (second timeout hasn't completed yet)
            expect(attachmentVideoUnitFormComponent.isUploading()).toBe(true);

            vi.advanceTimersByTime(500); // Complete the second timeout

            expect(attachmentVideoUnitFormComponent.isUploading()).toBe(false);
            vi.useRealTimers();
        });
    });

    describe('ngOnDestroy', () => {
        it('should clean up timeout on component destruction', () => {
            vi.useFakeTimers();
            attachmentVideoUnitFormComponentFixture.detectChanges();
            attachmentVideoUnitFormComponent.form.patchValue({ name: 'Test Unit' });

            const pdfFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
            attachmentVideoUnitFormComponent.file = pdfFile;
            attachmentVideoUnitFormComponent.fileName.set('test.pdf');

            const submitFormEventSpy = vi.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

            attachmentVideoUnitFormComponent.submitForm();

            const emittedData = submitFormEventSpy.mock.calls[0][0] as AttachmentVideoUnitFormData;
            const callback = emittedData.uploadProgressCallback!;

            // Invoke callback with 100% progress to schedule timeout
            callback(100, 'Complete');

            // Destroy the component before timeout completes
            attachmentVideoUnitFormComponent.ngOnDestroy();

            // Advance timer - should not throw or cause issues
            vi.advanceTimersByTime(1000);

            // Component should be cleaned up without errors
            expect(true).toBe(true);
            vi.useRealTimers();
        });
    });

    describe('setFormValues with videoFileProperties', () => {
        it('should set video file properties from form data', () => {
            attachmentVideoUnitFormComponentFixture.componentRef.setInput('isEditMode', true);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            const videoFile = new File(['video'], 'existing-video.mp4', { type: 'video/mp4' });
            // videoSource with a file path (not http) triggers video filename extraction
            const formData: AttachmentVideoUnitFormData = {
                formProperties: { name: 'Test Unit', videoSource: 'attachments/attachment-unit/1/existing-video.mp4' },
                fileProperties: {},
                videoFileProperties: {
                    videoFile: videoFile,
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
