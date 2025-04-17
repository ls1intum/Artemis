import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { CompetencySelectionComponent } from 'app/shared/competency-selection/competency-selection.component';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('AttachmentVideoUnitFormComponent', () => {
    let attachmentVideoUnitFormComponentFixture: ComponentFixture<AttachmentVideoUnitFormComponent>;
    let attachmentVideoUnitFormComponent: AttachmentVideoUnitFormComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule, MockDirective(NgbTooltip), MockModule(OwlDateTimeModule), MockModule(OwlNativeDateTimeModule)],
            declarations: [
                AttachmentVideoUnitFormComponent,
                FormDateTimePickerComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockComponent(CompetencySelectionComponent),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
            schemas: [],
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
        const fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf' });

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
        attachmentVideoUnitFormComponent.ngOnChanges();

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
        const fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf' });
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
        const exampleVersion = 42;
        attachmentVideoUnitFormComponent.versionControl!.setValue(exampleVersion);
        const exampleUpdateNotificationText = 'updated';
        attachmentVideoUnitFormComponent.updateNotificationTextControl!.setValue(exampleUpdateNotificationText);
        const fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf' });
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
        const fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf', lastModified: Date.now() });

        // Set file size to exceed the maximum file size
        Object.defineProperty(fakeFile, 'size', { value: MAX_FILE_SIZE + 1 });

        attachmentVideoUnitFormComponent.onFileChange({ target: { files: [fakeFile] } as unknown as EventTarget } as Event);
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
        attachmentVideoUnitFormComponent.versionControl!.enable();
        const exampleVersion = 42;
        attachmentVideoUnitFormComponent.versionControl!.setValue(exampleVersion);
        const exampleUpdateNotificationText = 'updated';
        attachmentVideoUnitFormComponent.updateNotificationTextControl!.setValue(exampleUpdateNotificationText);
        // Set file and fileName
        const fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf' });
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
            },
            fileProperties: {
                file: fakeFile,
                fileName: exampleFileName,
            },
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
});
