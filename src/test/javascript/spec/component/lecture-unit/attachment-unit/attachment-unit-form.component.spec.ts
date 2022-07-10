import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { AttachmentUnitFormComponent, AttachmentUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-unit-form/attachment-unit-form.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockPipe, MockProviders } from 'ng-mocks';
describe('AttachmentUnitFormComponent', () => {
    let attachmentUnitFormComponentFixture: ComponentFixture<AttachmentUnitFormComponent>;
    let attachmentUnitFormComponent: AttachmentUnitFormComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule],
            declarations: [AttachmentUnitFormComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FormDateTimePickerComponent)],
            providers: [MockProviders(TranslateService)],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                attachmentUnitFormComponentFixture = TestBed.createComponent(AttachmentUnitFormComponent);
                attachmentUnitFormComponent = attachmentUnitFormComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        attachmentUnitFormComponentFixture.detectChanges();
        expect(attachmentUnitFormComponent).not.toBeNull();
    });

    it('should correctly set form values in edit mode', () => {
        const fakeBlob = new Blob([''], { type: 'application/pdf' });
        fakeBlob['name'] = 'Test-File.pdf';

        attachmentUnitFormComponent.isEditMode = true;
        const formData: AttachmentUnitFormData = {
            formProperties: {
                name: 'test',
                description: 'lorem ipsum',
                releaseDate: dayjs().year(2010).month(3).date(5),
                version: 2,
                updateNotificationText: 'lorem ipsum',
            },
            fileProperties: {
                file: fakeBlob,
                fileName: 'lorem ipsum',
            },
        };
        attachmentUnitFormComponentFixture.detectChanges();

        attachmentUnitFormComponent.formData = formData;
        attachmentUnitFormComponent.ngOnChanges();

        expect(attachmentUnitFormComponent.nameControl?.value).toEqual(formData.formProperties.name);
        expect(attachmentUnitFormComponent.releaseDateControl?.value).toEqual(formData.formProperties.releaseDate);
        expect(attachmentUnitFormComponent.descriptionControl?.value).toEqual(formData.formProperties.description);
        expect(attachmentUnitFormComponent.versionControl?.value).toEqual(formData.formProperties.version);
        expect(attachmentUnitFormComponent.updateNotificationTextControl?.value).toEqual(formData.formProperties.updateNotificationText);
        expect(attachmentUnitFormComponent.fileName).toEqual(formData.fileProperties.fileName);
        expect(attachmentUnitFormComponent.file).toEqual(formData.fileProperties.file);
    });
    it('should submit valid form', () => {
        attachmentUnitFormComponentFixture.detectChanges();
        const exampleName = 'test';
        attachmentUnitFormComponent.nameControl!.setValue(exampleName);
        const exampleReleaseDate = dayjs().year(2010).month(3).date(5);
        attachmentUnitFormComponent.releaseDateControl!.setValue(exampleReleaseDate);
        const exampleDescription = 'lorem ipsum';
        attachmentUnitFormComponent.descriptionControl!.setValue(exampleDescription);
        const exampleVersion = 42;
        attachmentUnitFormComponent.versionControl!.setValue(exampleVersion);
        const exampleUpdateNotificationText = 'updated';
        attachmentUnitFormComponent.updateNotificationTextControl!.setValue(exampleUpdateNotificationText);
        const fakeBlob = new Blob([''], { type: 'application/pdf' });
        fakeBlob['name'] = 'Test-File.pdf';
        attachmentUnitFormComponent.file = fakeBlob;
        const exampleFileName = 'lorem Ipsum';
        attachmentUnitFormComponent.fileName = exampleFileName;

        attachmentUnitFormComponentFixture.detectChanges();
        expect(attachmentUnitFormComponent.form.valid).toBeTrue();

        const submitFormSpy = jest.spyOn(attachmentUnitFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(attachmentUnitFormComponent.formSubmitted, 'emit');

        const submitButton = attachmentUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        expect(submitFormSpy).toHaveBeenCalled();
        expect(submitFormEventSpy).toHaveBeenCalledWith({
            formProperties: {
                name: exampleName,
                description: exampleDescription,
                releaseDate: exampleReleaseDate,
                version: exampleVersion,
                updateNotificationText: exampleUpdateNotificationText,
            },
            fileProperties: {
                file: fakeBlob,
                fileName: exampleFileName,
            },
        });

        submitFormSpy.mockRestore();
        submitFormEventSpy.mockRestore();
    });
    it('should not submit a form when name is missing', () => {
        attachmentUnitFormComponentFixture.detectChanges();
        const exampleReleaseDate = dayjs().year(2010).month(3).date(5);
        attachmentUnitFormComponent.releaseDateControl!.setValue(exampleReleaseDate);
        const exampleDescription = 'lorem ipsum';
        attachmentUnitFormComponent.descriptionControl!.setValue(exampleDescription);
        const exampleVersion = 42;
        attachmentUnitFormComponent.versionControl!.setValue(exampleVersion);
        const exampleUpdateNotificationText = 'updated';
        attachmentUnitFormComponent.updateNotificationTextControl!.setValue(exampleUpdateNotificationText);
        const fakeBlob = new Blob([''], { type: 'application/pdf' });
        fakeBlob['name'] = 'Test-File.pdf';
        attachmentUnitFormComponent.file = fakeBlob;
        const exampleFileName = 'lorem Ipsum';
        attachmentUnitFormComponent.fileName = exampleFileName;

        expect(attachmentUnitFormComponent.form.invalid).toBeTrue();
        const submitFormSpy = jest.spyOn(attachmentUnitFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(attachmentUnitFormComponent.formSubmitted, 'emit');

        const submitButton = attachmentUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        expect(submitFormSpy).toHaveBeenCalledTimes(0);
        expect(submitFormEventSpy).toHaveBeenCalledTimes(0);
    });

    it('calls on file change on changed file', () => {
        const fakeBlob = new Blob([''], { type: 'application/pdf' });
        fakeBlob['name'] = 'Test-File.pdf';
        const onFileChangeStub = jest.spyOn(attachmentUnitFormComponent, 'onFileChange');
        attachmentUnitFormComponentFixture.detectChanges();
        const fileInput = attachmentUnitFormComponentFixture.debugElement.nativeElement.querySelector('#fileInput');
        fileInput.dispatchEvent(new Event('change'));
        expect(onFileChangeStub).toHaveBeenCalledOnce();
    });

    it('sets file upload error correctly', () => {
        attachmentUnitFormComponentFixture.detectChanges();
        attachmentUnitFormComponent.setFileUploadError('lorem ipsum');
        expect(attachmentUnitFormComponent.fileUploadErrorMessage).toEqual('lorem ipsum');
    });
});
