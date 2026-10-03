import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { UnitFormChange } from 'app/lecture/manage/lecture-units/unit-form-change.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import {
    AttachmentVideoUnitFormComponent,
    AttachmentVideoUnitFormData,
    FileProperties,
} from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { MAX_FILE_SIZE } from 'app/foundation/constants/input.constants';
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
import { FeatureToggleHideDirective } from 'app/foundation/feature-toggle/feature-toggle-hide.directive';
import { By } from '@angular/platform-browser';

describe('AttachmentVideoUnitFormComponent', () => {
    let attachmentVideoUnitFormComponentFixture: ComponentFixture<AttachmentVideoUnitFormComponent>;
    let attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                ReactiveFormsModule,
                FormsModule,
                FontAwesomeTestingModule,
                AttachmentVideoUnitFormComponent,
                FormDateTimePickerComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(CompetencySelectionComponent),
                MockDirective(FeatureToggleHideDirective),
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
        expect(attachmentVideoUnitFormComponent.currentFileVersion()).toEqual(formData.formProperties.version);
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
                updateNotificationText: exampleUpdateNotificationText,
                videoSource: exampleVideoUrl,
                urlHelper: null,
            },
            fileProperties: {
                file: fakeFile,
                fileName: exampleFileName,
            },
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
        expect(attachmentVideoUnitFormComponent.isFileTooBig()).toBe(true);
        expect(submitButton.disabled).toBe(true);
    });

    it('should not submit a form when file and videoSource is missing', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();
        const exampleName = 'test';
        const exampleReleaseDate = dayjs().year(2010).month(3).date(5);
        const exampleDescription = 'lorem ipsum';
        attachmentVideoUnitFormComponent.nameControl!.setValue(exampleName);
        attachmentVideoUnitFormComponent.releaseDateControl!.setValue(exampleReleaseDate);
        attachmentVideoUnitFormComponent.descriptionControl!.setValue(exampleDescription);
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
                updateNotificationText: exampleUpdateNotificationText,
                videoSource: exampleVideoUrl,
                urlHelper: null,
            },
            fileProperties: {
                file: undefined,
                fileName: undefined,
            },
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
                updateNotificationText: exampleUpdateNotificationText,
                videoSource: '',
                urlHelper: null,
            },
            fileProperties: {
                file: fakeFile,
                fileName: exampleFileName,
            },
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

        // Type the link like a user, so the field's input event refreshes the page as it does in the browser.
        const urlHelperInput: HTMLInputElement = attachmentVideoUnitFormComponentFixture.nativeElement.querySelector('#urlHelper');
        urlHelperInput.value = validYouTubeUrl;
        urlHelperInput.dispatchEvent(new Event('input'));
        // The transform button is gated by [disabled]="!isTransformable". Under zoneless change
        // detection the form-control status only propagates to that binding after the reactive flush
        // settles, so wait for stability (and re-render) before clicking, otherwise the click is a no-op.
        await attachmentVideoUnitFormComponentFixture.whenStable();
        attachmentVideoUnitFormComponentFixture.detectChanges();
        const transformButton = attachmentVideoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#transformButton');
        transformButton.click();

        await attachmentVideoUnitFormComponentFixture.whenStable();
        expect(attachmentVideoUnitFormComponent.videoSourceControl?.value).toEqual(validYouTubeUrlInEmbeddableFormat);
    });

    it('should correctly transform TUM-Live URL without video only into embeddable format', async () => {
        const tumLiveUrl = 'https://live.rbg.tum.de/w/test/26';
        const expectedUrl = 'https://live.rbg.tum.de/w/test/26?video_only=1';

        attachmentVideoUnitFormComponentFixture.detectChanges();
        // Type the link like a user, so the field's input event refreshes the page as it does in the browser.
        const urlHelperInput: HTMLInputElement = attachmentVideoUnitFormComponentFixture.nativeElement.querySelector('#urlHelper');
        urlHelperInput.value = tumLiveUrl;
        urlHelperInput.dispatchEvent(new Event('input'));
        // The transform button is gated by [disabled]="!isTransformable". Under zoneless change
        // detection the form-control status only propagates to that binding after the reactive flush
        // settles, so wait for stability (and re-render) before clicking, otherwise the click is a no-op.
        await attachmentVideoUnitFormComponentFixture.whenStable();
        attachmentVideoUnitFormComponentFixture.detectChanges();

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

        // Valid YouTube (parsable by our video URL parser)
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

    it('is backwards-compatible with persisted embed URLs (YouTube embed, TUM-Live video_only, Vimeo player)', () => {
        attachmentVideoUnitFormComponentFixture.detectChanges();

        // Persisted YouTube embed URL must remain valid and idempotent through extractEmbeddedUrl.
        const storedYouTubeEmbed = 'https://www.youtube.com/embed/NWNufWyVcT0';
        attachmentVideoUnitFormComponent.videoSourceControl!.setValue(storedYouTubeEmbed);
        expect(attachmentVideoUnitFormComponent.videoSourceControl!.errors).toBeNull();
        expect(attachmentVideoUnitFormComponent.extractEmbeddedUrl(storedYouTubeEmbed)).toBe(storedYouTubeEmbed);

        // Persisted TUM-Live embed URL must remain valid and idempotent.
        const storedTumLiveEmbed = 'https://live.rbg.tum.de/w/inhn0001/6233?video_only=1';
        attachmentVideoUnitFormComponent.videoSourceControl!.setValue(storedTumLiveEmbed);
        expect(attachmentVideoUnitFormComponent.videoSourceControl!.errors).toBeNull();
        expect(attachmentVideoUnitFormComponent.extractEmbeddedUrl(storedTumLiveEmbed)).toBe(storedTumLiveEmbed);

        // Persisted Vimeo player URL (with unlisted hash) must remain valid and fully idempotent — the `h=` parameter is required for unlisted-video playback, so the transform must preserve it.
        const storedVimeoPlayer = 'https://player.vimeo.com/video/228795592?h=27bef101ce';
        attachmentVideoUnitFormComponent.videoSourceControl!.setValue(storedVimeoPlayer);
        expect(attachmentVideoUnitFormComponent.videoSourceControl!.errors).toBeNull();
        expect(attachmentVideoUnitFormComponent.extractEmbeddedUrl(storedVimeoPlayer)).toBe(storedVimeoPlayer);
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
        attachmentVideoUnitFormComponentFixture.detectChanges();

        // Name initially empty -> should be auto-filled without extension
        expect(attachmentVideoUnitFormComponent.nameControl!.value).toBeFalsy();

        const bigFile = new File(['a'.repeat(10)], 'Lecture-01.pdf', { type: 'application/pdf', lastModified: Date.now() });
        Object.defineProperty(bigFile, 'size', { value: MAX_FILE_SIZE + 10 });

        const input = document.createElement('input');
        Object.defineProperty(input, 'files', { value: [bigFile] });

        attachmentVideoUnitFormComponent.onFileChange({ target: input } as any);

        expect(attachmentVideoUnitFormComponent.fileName()).toBe('Lecture-01.pdf');
        expect(attachmentVideoUnitFormComponent.nameControl!.value).toBe('Lecture-01');
        expect(attachmentVideoUnitFormComponent.isFileTooBig()).toBe(true);
    });

    describe('file field', () => {
        const storedLink = 'attachments/attachment-video-units/7/AttachmentUnit_2026-09-23T20-43-00-961_Design_Patterns%C3%9C.pdf';
        const query = (testId: string) => attachmentVideoUnitFormComponentFixture.debugElement.query(By.css(`[data-testid="${testId}"]`));
        const pdf = (name: string) => new File(['content'], name, { type: 'application/pdf' });
        const chooseFile = (file: File) => attachmentVideoUnitFormComponent.onFileChange({ target: { files: [file] } as unknown as EventTarget } as Event);

        function openInEditMode(fileProperties: FileProperties = { fileName: storedLink }) {
            attachmentVideoUnitFormComponentFixture.componentRef.setInput('isEditMode', true);
            attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', {
                formProperties: { name: 'Design Patterns', version: 3 },
                fileProperties,
            } as AttachmentVideoUnitFormData);
            attachmentVideoUnitFormComponentFixture.detectChanges();
        }

        it('should show the current file of an edited unit instead of an empty file picker', () => {
            openInEditMode();

            expect(query('current-file-name').nativeElement.textContent.trim()).toBe('Design PatternsÜ.pdf');
            expect(query('current-file-version').nativeElement.textContent).toContain('3');
            expect(query('open-current-file-button').nativeElement.getAttribute('href')).toBe(`api/core/files/${storedLink}?version=3`);
            expect(query('replace-file-button')).not.toBeNull();
            expect(query('choose-file-button')).toBeNull();
            expect(query('replacement-file')).toBeNull();
            expect(query('attachment-file-input').nativeElement.classList).toContain('hidden');
        });

        it('should replace the current file and allow keeping it again', () => {
            openInEditMode();
            const submitSpy = vi.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');
            const fileInput: HTMLInputElement = query('attachment-file-input').nativeElement;
            const clickSpy = vi.spyOn(fileInput, 'click').mockImplementation(() => {});

            query('replace-file-button').nativeElement.click();
            expect(clickSpy).toHaveBeenCalledOnce();

            const replacement = pdf('Design Patterns v2.pdf');
            chooseFile(replacement);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(attachmentVideoUnitFormComponent.isReplacingFile()).toBe(true);
            expect(query('replacement-file-name').nativeElement.textContent.trim()).toBe('Design Patterns v2.pdf');
            expect(query('current-file-name').nativeElement.textContent.trim()).toBe('Design PatternsÜ.pdf');
            attachmentVideoUnitFormComponent.submitForm();
            expect(submitSpy).toHaveBeenLastCalledWith(expect.objectContaining({ fileProperties: { file: replacement, fileName: 'Design Patterns v2.pdf' } }));

            const focusSpy = vi.spyOn(query('replace-file-button').nativeElement as HTMLButtonElement, 'focus');
            query('keep-current-file-button').nativeElement.click();
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(query('replacement-file')).toBeNull();
            expect(focusSpy).toHaveBeenCalledOnce();
            attachmentVideoUnitFormComponent.submitForm();
            expect(submitSpy).toHaveBeenLastCalledWith(expect.objectContaining({ fileProperties: { file: undefined, fileName: storedLink } }));
        });

        it('should drop the error of a too big replacement when the current file is kept', () => {
            openInEditMode();
            const tooBig = pdf('Huge.pdf');
            Object.defineProperty(tooBig, 'size', { value: MAX_FILE_SIZE + 1 });
            chooseFile(tooBig);
            attachmentVideoUnitFormComponentFixture.detectChanges();
            expect(query('file-too-big-error')).not.toBeNull();
            expect(attachmentVideoUnitFormComponent.isFormValid()).toBe(false);

            attachmentVideoUnitFormComponent.keepCurrentFile();
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(query('file-too-big-error')).toBeNull();
            expect(attachmentVideoUnitFormComponent.isFormValid()).toBe(true);
        });

        it('should take a file dropped onto the file field', () => {
            attachmentVideoUnitFormComponentFixture.detectChanges();
            const dropped = pdf('Observer Pattern.pdf');
            const dragOver = { preventDefault: vi.fn() } as unknown as DragEvent;
            const drop = { preventDefault: vi.fn(), dataTransfer: { files: [dropped] } } as unknown as DragEvent;

            query('file-field').triggerEventHandler('dragover', dragOver);
            query('file-field').triggerEventHandler('drop', drop);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(dragOver.preventDefault).toHaveBeenCalled();
            expect(drop.preventDefault).toHaveBeenCalled();
            expect(attachmentVideoUnitFormComponent.file).toBe(dropped);
            expect(query('chosen-file-name').nativeElement.textContent.trim()).toBe('Observer Pattern.pdf');
            expect(attachmentVideoUnitFormComponent.nameControl?.value).toBe('Observer Pattern');
        });

        it('should not take a file of a type the server does not accept', () => {
            openInEditMode();
            const drop = { preventDefault: vi.fn(), dataTransfer: { files: [new File(['content'], 'setup.exe')] } } as unknown as DragEvent;

            query('file-field').triggerEventHandler('drop', drop);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(query('file-type-error')).not.toBeNull();
            expect(attachmentVideoUnitFormComponent.file).toBeUndefined();
            expect(attachmentVideoUnitFormComponent.fileName()).toBe(storedLink);
            expect(query('replacement-file')).toBeNull();

            // the dialog's "All files" option lets any file through as well, including one without an extension
            chooseFile(new File(['content'], 'README'));
            attachmentVideoUnitFormComponentFixture.detectChanges();
            expect(query('file-type-error')).not.toBeNull();
            expect(attachmentVideoUnitFormComponent.file).toBeUndefined();

            const replacement = pdf('Design Patterns v2.PDF');
            chooseFile(replacement);
            attachmentVideoUnitFormComponentFixture.detectChanges();
            expect(query('file-type-error')).toBeNull();
            expect(attachmentVideoUnitFormComponent.file).toBe(replacement);
            expect(query('replacement-file')).not.toBeNull();
        });

        it('should clear the unsupported type error when the current file is kept', () => {
            openInEditMode();
            chooseFile(new File(['content'], 'setup.exe'));
            attachmentVideoUnitFormComponentFixture.detectChanges();
            expect(query('file-type-error')).not.toBeNull();

            attachmentVideoUnitFormComponent.keepCurrentFile();
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(query('file-type-error')).toBeNull();
        });

        it('should offer a styled file picker when a unit is created', () => {
            attachmentVideoUnitFormComponentFixture.detectChanges();
            const clickSpy = vi.spyOn(query('attachment-file-input').nativeElement as HTMLInputElement, 'click').mockImplementation(() => {});

            expect(query('current-file')).toBeNull();
            query('choose-file-button').nativeElement.click();
            expect(clickSpy).toHaveBeenCalledOnce();

            chooseFile(pdf('Observer Pattern.pdf'));
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(query('chosen-file-name').nativeElement.textContent.trim()).toBe('Observer Pattern.pdf');
            expect(attachmentVideoUnitFormComponent.nameControl?.value).toBe('Observer Pattern');
        });

        it('should offer the file picker when an edited unit has no file yet', () => {
            openInEditMode({});

            expect(query('current-file')).toBeNull();
            expect(query('choose-file-button')).not.toBeNull();
        });

        it('should only demand a file when there is no video either', () => {
            attachmentVideoUnitFormComponentFixture.detectChanges();
            vi.spyOn(query('attachment-file-input').nativeElement as HTMLInputElement, 'click').mockImplementation(() => {});
            attachmentVideoUnitFormComponent.openFilePicker();
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(query('file-required-error')).not.toBeNull();

            attachmentVideoUnitFormComponent.videoSourceControl!.setValue('https://www.youtube.com/embed/8iU8LPEa4o0');
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(query('file-required-error')).toBeNull();
        });

        it('should start empty when the form switches from editing a unit to creating one', () => {
            openInEditMode();
            expect(attachmentVideoUnitFormComponent.nameControl?.value).toBe('Design Patterns');
            vi.spyOn(query('attachment-file-input').nativeElement as HTMLInputElement, 'click').mockImplementation(() => {});
            query('replace-file-button').nativeElement.click();
            chooseFile(pdf('Design Patterns v2.pdf'));
            const submitSpy = vi.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

            attachmentVideoUnitFormComponentFixture.componentRef.setInput('isEditMode', false);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(attachmentVideoUnitFormComponent.nameControl?.value).toBeNull();
            expect(attachmentVideoUnitFormComponent.fileName()).toBeUndefined();
            expect(attachmentVideoUnitFormComponent.isFormValid()).toBe(false);
            expect(query('current-file')).toBeNull();
            expect(query('choose-file-button')).not.toBeNull();
            expect(query('file-required-error')).toBeNull();
            attachmentVideoUnitFormComponent.submitForm();
            expect(submitSpy).toHaveBeenLastCalledWith(expect.objectContaining({ fileProperties: { file: undefined, fileName: undefined } }));
        });

        it('should not keep the file of a previously edited unit', () => {
            openInEditMode();
            const tooBig = pdf('Replacement.pdf');
            Object.defineProperty(tooBig, 'size', { value: MAX_FILE_SIZE + 1 });
            chooseFile(tooBig);
            const submitSpy = vi.spyOn(attachmentVideoUnitFormComponent.formSubmitted, 'emit');

            attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', {
                formProperties: { name: 'Lecture recording', videoSource: 'https://www.youtube.com/embed/8iU8LPEa4o0' },
                fileProperties: {},
            } as AttachmentVideoUnitFormData);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(attachmentVideoUnitFormComponent.fileName()).toBeUndefined();
            expect(attachmentVideoUnitFormComponent.isFileTooBig()).toBe(false);
            expect(query('current-file')).toBeNull();
            expect(query('file-too-big-error')).toBeNull();
            attachmentVideoUnitFormComponent.submitForm();
            expect(submitSpy).toHaveBeenLastCalledWith(expect.objectContaining({ fileProperties: { file: undefined, fileName: undefined } }));
        });

        it('should show too big files as an error', () => {
            attachmentVideoUnitFormComponentFixture.detectChanges();
            const tooBig = pdf('Huge.pdf');
            Object.defineProperty(tooBig, 'size', { value: MAX_FILE_SIZE + 1 });

            chooseFile(tooBig);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(query('file-too-big-error')).not.toBeNull();
        });
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

    describe('when the item saves itself', () => {
        const storedLink = 'attachments/attachment-video-units/7/Slides.pdf';
        const query = (testId: string) => attachmentVideoUnitFormComponentFixture.debugElement.query(By.css(`[data-testid="${testId}"]`));
        const chooseFile = (file: File) => attachmentVideoUnitFormComponent.onFileChange({ target: { files: [file] } as unknown as EventTarget } as Event);
        let changes: UnitFormChange<AttachmentVideoUnitFormData>[];

        beforeEach(() => {
            changes = [];
            attachmentVideoUnitFormComponent.formChanged.subscribe((change) => changes.push(change));
            attachmentVideoUnitFormComponentFixture.componentRef.setInput('isEditMode', true);
            attachmentVideoUnitFormComponentFixture.componentRef.setInput('autosave', true);
            attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', {
                formProperties: { name: 'Slides', version: 1, videoSource: 'https://www.youtube.com/embed/old' },
                fileProperties: { fileName: storedLink },
            } as AttachmentVideoUnitFormData);
            attachmentVideoUnitFormComponentFixture.detectChanges();
        });

        it('should report typed text for saving after a pause and choices for saving at once, but not the data of the item', () => {
            expect(changes).toEqual([]);

            attachmentVideoUnitFormComponent.nameControl!.setValue('Slides week 1');
            attachmentVideoUnitFormComponent.onReleaseDateChange(dayjs('2026-10-01T10:00:00Z'));

            expect(changes.map((change) => change.immediate)).toEqual([false, true]);
            expect(changes[1].data.formProperties.name).toBe('Slides week 1');
            expect(changes[1].valid).toBe(true);
        });

        it('should offer neither Submit nor a notification text, since Enter saves through the page and students are notified on upload', () => {
            expect(attachmentVideoUnitFormComponentFixture.nativeElement.querySelector('#submitButton')).toBeNull();
            expect(attachmentVideoUnitFormComponentFixture.nativeElement.querySelector('#updateNotificationText')).toBeNull();
        });

        it('should upload the first file of a video item as soon as it is chosen, and let a file that was not saved yet be discarded', () => {
            attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', {
                formProperties: { name: 'Lecture video', videoSource: 'https://www.youtube.com/embed/old' },
                fileProperties: {},
            } as AttachmentVideoUnitFormData);
            attachmentVideoUnitFormComponentFixture.detectChanges();
            const uploadSpy = vi.fn();
            const withdrawnSpy = vi.fn();
            attachmentVideoUnitFormComponent.fileUploadRequested.subscribe(uploadSpy);
            attachmentVideoUnitFormComponent.confirmedContentWithdrawn.subscribe(withdrawnSpy);
            expect(query('upload-hint').nativeElement.textContent).toContain('artemisApp.attachmentVideoUnit.createAttachmentVideoUnit.newFileHint');

            chooseFile(new File(['content'], 'Slides.pdf', { type: 'application/pdf' }));
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(uploadSpy).toHaveBeenCalledOnce();
            expect(attachmentVideoUnitFormComponent.hasUnconfirmedContent()).toBe(false);
            // Whether students are notified was decided with the choice of the file.
            expect(attachmentVideoUnitFormComponentFixture.debugElement.query(By.css('tumaet-ui-checkbox')).componentInstance.disabled()).toBe(true);
            query('discard-new-file-button').nativeElement.click();
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(withdrawnSpy).toHaveBeenCalledExactlyOnceWith('file');
            expect(attachmentVideoUnitFormComponent.fileName()).toBeUndefined();
            expect(document.activeElement).toBe(query('choose-file-button').nativeElement);
        });

        it('should count a video URL typed while the confirmed one is saved as not confirmed', () => {
            attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', {
                formProperties: { name: 'Lecture video', videoSource: 'https://www.youtube.com/embed/old' },
                fileProperties: {},
            } as AttachmentVideoUnitFormData);
            attachmentVideoUnitFormComponentFixture.detectChanges();
            attachmentVideoUnitFormComponent.videoSourceControl!.setValue('https://www.youtube.com/embed/first');

            attachmentVideoUnitFormComponent.saveVideoSource();
            expect(attachmentVideoUnitFormComponent.isVideoSourceSaveRequested()).toBe(true);
            expect(attachmentVideoUnitFormComponent.hasUnconfirmedContent()).toBe(false);

            attachmentVideoUnitFormComponent.videoSourceControl!.setValue('https://www.youtube.com/embed/second');
            expect(attachmentVideoUnitFormComponent.hasUnconfirmedContent()).toBe(true);

            attachmentVideoUnitFormComponent.takeOverSavedVideoSource('https://www.youtube.com/embed/first');
            expect(attachmentVideoUnitFormComponent.hasUnconfirmedContent()).toBe(true);
        });

        it('should count going back to the saved video URL while the confirmed one is saved as not confirmed, since the confirmed one replaces it', () => {
            attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', {
                formProperties: { name: 'Lecture video', videoSource: 'https://www.youtube.com/embed/old' },
                fileProperties: {},
            } as AttachmentVideoUnitFormData);
            attachmentVideoUnitFormComponentFixture.detectChanges();
            attachmentVideoUnitFormComponent.videoSourceControl!.setValue('https://www.youtube.com/embed/first');
            attachmentVideoUnitFormComponent.saveVideoSource();

            attachmentVideoUnitFormComponent.videoSourceControl!.setValue('https://www.youtube.com/embed/old');
            expect(attachmentVideoUnitFormComponent.hasUnconfirmedContent()).toBe(true);
            // The choice is offered, so the hint that asks for it is not a dead end.
            attachmentVideoUnitFormComponentFixture.detectChanges();
            expect(query('video-source-confirm')).not.toBeNull();

            attachmentVideoUnitFormComponent.takeOverSavedVideoSource('https://www.youtube.com/embed/first');
            expect(attachmentVideoUnitFormComponent.hasUnconfirmedContent()).toBe(true);
            attachmentVideoUnitFormComponent.videoSourceControl!.setValue('https://www.youtube.com/embed/first');
            expect(attachmentVideoUnitFormComponent.hasUnconfirmedContent()).toBe(false);
        });

        it('should show that the chosen file is uploaded while its request runs', () => {
            chooseFile(new File(['content'], 'Slides v2.pdf', { type: 'application/pdf' }));
            attachmentVideoUnitFormComponentFixture.detectChanges();
            expect(query('file-uploading')).toBeNull();

            attachmentVideoUnitFormComponentFixture.componentRef.setInput('savingConfirmed', 'file');
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(query('file-uploading')).not.toBeNull();
            expect(query('replacement-hint')).toBeNull();
        });

        it('should neither take nor upload a file that is too big or of a type the server does not accept', () => {
            const uploadSpy = vi.fn();
            attachmentVideoUnitFormComponent.fileUploadRequested.subscribe(uploadSpy);
            const tooBig = new File([''], 'Huge.pdf', { type: 'application/pdf' });
            Object.defineProperty(tooBig, 'size', { value: MAX_FILE_SIZE + 1 });

            chooseFile(tooBig);
            expect(attachmentVideoUnitFormComponent.isFileTooBig()).toBe(true);
            chooseFile(new File(['content'], 'Slides.exe'));

            expect(uploadSpy).not.toHaveBeenCalled();
            expect(attachmentVideoUnitFormComponent.file).toBeUndefined();
            expect(attachmentVideoUnitFormComponent.fileName()).toBe(storedLink);
            expect(attachmentVideoUnitFormComponent.isFileTypeUnsupported()).toBe(true);
        });

        it('should upload a file with the name filled in from the file name when the item has none', () => {
            const uploadSpy = vi.fn();
            attachmentVideoUnitFormComponent.fileUploadRequested.subscribe(uploadSpy);
            attachmentVideoUnitFormComponent.nameControl!.setValue('');

            chooseFile(new File(['content'], 'Week 2.pdf', { type: 'application/pdf' }));

            expect(uploadSpy.mock.lastCall![0].formProperties.name).toBe('Week 2');
            expect(changes.at(-1)).toEqual(expect.objectContaining({ valid: true }));
        });

        it('should not offer to take back a file or video URL whose request runs already', () => {
            chooseFile(new File(['content'], 'Slides v2.pdf', { type: 'application/pdf' }));
            attachmentVideoUnitFormComponent.videoSourceControl!.setValue('https://www.youtube.com/embed/new');
            attachmentVideoUnitFormComponentFixture.componentRef.setInput('savingConfirmed', 'file');
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(query('keep-current-file-button').nativeElement.disabled).toBe(true);
            expect(query('discard-video-source-button').nativeElement.disabled).toBe(false);

            attachmentVideoUnitFormComponentFixture.componentRef.setInput('savingConfirmed', 'videoSource');
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(query('keep-current-file-button').nativeElement.disabled).toBe(false);
            expect(query('discard-video-source-button').nativeElement.disabled).toBe(true);
        });

        it('should take the uploaded file as the current one when the user kept the old file while it was uploaded', () => {
            const uploaded = new File(['content'], 'Slides v2.pdf', { type: 'application/pdf' });
            chooseFile(uploaded);
            attachmentVideoUnitFormComponent.keepCurrentFile();

            attachmentVideoUnitFormComponent.takeOverSavedFile('attachments/attachment-video-units/7/Slides_v2.pdf', 2, uploaded);

            expect(attachmentVideoUnitFormComponent.fileName()).toBe('attachments/attachment-video-units/7/Slides_v2.pdf');
            expect(attachmentVideoUnitFormComponent.isReplacingFile()).toBe(false);
            expect(attachmentVideoUnitFormComponent.hasUnconfirmedContent()).toBe(false);
        });

        it('should notice the name turning invalid while the video URL is invalid already', () => {
            attachmentVideoUnitFormComponent.videoSourceControl!.setValue('not a link');
            attachmentVideoUnitFormComponentFixture.detectChanges();
            expect(attachmentVideoUnitFormComponent.areDetailsValid()).toBe(true);

            // The whole form stays invalid, so only the change of the name tells the details apart.
            attachmentVideoUnitFormComponent.nameControl!.setValue('');
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(attachmentVideoUnitFormComponent.areDetailsValid()).toBe(false);
        });

        it('should remove the video URL only from an item that keeps its file', () => {
            attachmentVideoUnitFormComponent.videoSourceControl!.setValue('');
            expect(attachmentVideoUnitFormComponent.canSaveVideoSource()).toBe(true);

            attachmentVideoUnitFormComponentFixture.componentRef.setInput('formData', {
                formProperties: { name: 'Lecture video', videoSource: 'https://www.youtube.com/embed/old' },
                fileProperties: {},
            } as AttachmentVideoUnitFormData);
            attachmentVideoUnitFormComponentFixture.detectChanges();
            attachmentVideoUnitFormComponent.videoSourceControl!.setValue('');

            expect(attachmentVideoUnitFormComponent.canSaveVideoSource()).toBe(false);
        });

        it('should upload a new file as soon as it is chosen, once, and notify students only when asked before', () => {
            const uploadSpy = vi.fn();
            attachmentVideoUnitFormComponent.fileUploadRequested.subscribe(uploadSpy);
            const replacement = new File(['content'], 'Slides v2.pdf', { type: 'application/pdf' });
            // What the upload does is said before the file is chosen, since nothing is confirmed afterwards.
            expect(query('upload-options')).not.toBeNull();
            expect(query('upload-hint').nativeElement.textContent).toContain('artemisApp.attachmentVideoUnit.createAttachmentVideoUnit.replaceFileHint');
            attachmentVideoUnitFormComponent.notifyStudents.set(true);

            chooseFile(replacement);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(changes).toEqual([]);
            expect(uploadSpy).toHaveBeenCalledOnce();
            expect(uploadSpy.mock.lastCall![0].fileProperties).toEqual({ file: replacement, fileName: 'Slides v2.pdf', notifyStudents: true });
            expect(attachmentVideoUnitFormComponent.nextFileVersion()).toBe(2);
            expect(attachmentVideoUnitFormComponent.hasUnconfirmedContent()).toBe(false);
            expect(query('upload-new-version-button')).toBeNull();
        });

        it('should take back a file that was not saved yet when the current one is kept or another one is chosen', () => {
            const withdrawnSpy = vi.fn();
            attachmentVideoUnitFormComponent.confirmedContentWithdrawn.subscribe(withdrawnSpy);
            chooseFile(new File(['content'], 'Slides v2.pdf', { type: 'application/pdf' }));

            chooseFile(new File(['content'], 'Slides v3.pdf', { type: 'application/pdf' }));
            expect(withdrawnSpy).toHaveBeenCalledExactlyOnceWith('file');
            // The file chosen next is uploaded again.
            expect(attachmentVideoUnitFormComponent.isFileUploadRequested()).toBe(true);

            attachmentVideoUnitFormComponentFixture.detectChanges();
            query('keep-current-file-button').nativeElement.click();
            expect(withdrawnSpy).toHaveBeenCalledTimes(2);
            expect(attachmentVideoUnitFormComponent.hasUnconfirmedContent()).toBe(false);
        });

        it('should save the details although the video link helper holds a link it cannot turn into a video URL', () => {
            const uploadSpy = vi.fn();
            attachmentVideoUnitFormComponent.fileUploadRequested.subscribe(uploadSpy);
            attachmentVideoUnitFormComponent.urlHelperControl!.setValue('not a video page');
            attachmentVideoUnitFormComponent.descriptionControl!.setValue('Week 1');
            chooseFile(new File(['content'], 'Slides v2.pdf', { type: 'application/pdf' }));

            expect(changes.at(-1)).toEqual(expect.objectContaining({ valid: true }));
            expect(uploadSpy).toHaveBeenCalledOnce();
        });

        it('should upload a file while the details cannot be saved, since the page sends it with the last valid details, but not confirm a video URL', () => {
            const uploadSpy = vi.fn();
            const videoSpy = vi.fn();
            attachmentVideoUnitFormComponent.fileUploadRequested.subscribe(uploadSpy);
            attachmentVideoUnitFormComponent.videoSourceSaveRequested.subscribe(videoSpy);
            attachmentVideoUnitFormComponent.videoSourceControl!.setValue('https://www.youtube.com/embed/new');
            attachmentVideoUnitFormComponent.descriptionControl!.setValue('x'.repeat(1001));

            chooseFile(new File(['content'], 'Slides v2.pdf', { type: 'application/pdf' }));
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(uploadSpy).toHaveBeenCalledOnce();
            expect(query('save-video-source-button').nativeElement.disabled).toBe(true);
            attachmentVideoUnitFormComponent.saveVideoSource();
            expect(videoSpy).not.toHaveBeenCalled();
        });

        it('should take the saved file over and keep what else was typed', () => {
            const uploaded = new File(['content'], 'Slides v2.pdf', { type: 'application/pdf' });
            attachmentVideoUnitFormComponent.notifyStudents.set(true);
            chooseFile(uploaded);
            attachmentVideoUnitFormComponent.nameControl!.setValue('Slides week 1');

            attachmentVideoUnitFormComponent.takeOverSavedFile('attachments/attachment-video-units/7/Slides_v2.pdf', 2, uploaded);
            attachmentVideoUnitFormComponentFixture.detectChanges();

            expect(attachmentVideoUnitFormComponent.isReplacingFile()).toBe(false);
            expect(attachmentVideoUnitFormComponent.currentFileVersion()).toBe(2);
            expect(attachmentVideoUnitFormComponent.nextFileVersion()).toBe(3);
            expect(attachmentVideoUnitFormComponent.notifyStudents()).toBe(false);
            expect(attachmentVideoUnitFormComponent.nameControl?.value).toBe('Slides week 1');
            expect(query('replacement-file')).toBeNull();
            expect(changes.map((change) => change.data.formProperties.name)).toEqual(['Slides week 1']);
        });

        it('should keep a file chosen while another one was being uploaded', () => {
            const uploaded = new File(['content'], 'Slides v2.pdf', { type: 'application/pdf' });
            const chosenMeanwhile = new File(['content'], 'Slides v3.pdf', { type: 'application/pdf' });
            chooseFile(uploaded);
            chooseFile(chosenMeanwhile);

            attachmentVideoUnitFormComponent.takeOverSavedFile('attachments/attachment-video-units/7/Slides_v2.pdf', 2, uploaded);

            expect(attachmentVideoUnitFormComponent.file).toBe(chosenMeanwhile);
            expect(attachmentVideoUnitFormComponent.currentFileVersion()).toBe(2);
            expect(attachmentVideoUnitFormComponent.nextFileVersion()).toBe(3);
            expect(attachmentVideoUnitFormComponent.isReplacingFile()).toBe(true);
        });

        it('should save a new video URL only when confirmed and let it be discarded', () => {
            const saveSpy = vi.fn();
            attachmentVideoUnitFormComponent.videoSourceSaveRequested.subscribe(saveSpy);
            expect(query('video-source-confirm')).toBeNull();

            attachmentVideoUnitFormComponent.videoSourceControl!.setValue('https://www.youtube.com/embed/new');
            attachmentVideoUnitFormComponentFixture.detectChanges();
            expect(changes).toEqual([]);
            query('save-video-source-button').nativeElement.click();
            attachmentVideoUnitFormComponentFixture.detectChanges();
            query('save-video-source-button').nativeElement.click();
            expect(saveSpy).toHaveBeenCalledExactlyOnceWith(
                expect.objectContaining({ formProperties: expect.objectContaining({ videoSource: 'https://www.youtube.com/embed/new' }) }),
            );

            attachmentVideoUnitFormComponent.takeOverSavedVideoSource('https://www.youtube.com/embed/new');
            attachmentVideoUnitFormComponentFixture.detectChanges();
            expect(query('video-source-confirm')).toBeNull();

            attachmentVideoUnitFormComponent.videoSourceControl!.setValue('https://www.youtube.com/embed/other');
            attachmentVideoUnitFormComponentFixture.detectChanges();
            query('discard-video-source-button').nativeElement.click();
            attachmentVideoUnitFormComponentFixture.detectChanges();
            expect(attachmentVideoUnitFormComponent.videoSourceControl?.value).toBe('https://www.youtube.com/embed/new');
            expect(query('video-source-confirm')).toBeNull();
            // The discard button is gone, so the keyboard focus continues at the video URL.
            expect(document.activeElement?.id).toBe('videoSource');
        });
    });
});
