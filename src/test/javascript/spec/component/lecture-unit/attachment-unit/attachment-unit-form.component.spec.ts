import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { AttachmentUnitFormComponent, AttachmentUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-unit-form/attachment-unit-form.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as chai from 'chai';
import dayjs from 'dayjs';
import { MockComponent, MockPipe, MockProviders } from 'ng-mocks';
import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';

chai.use(sinonChai);
const expect = chai.expect;
describe('AttachmentUnitFormComponent', () => {
    const sandbox = sinon.createSandbox();
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

    afterEach(function () {
        sandbox.restore();
    });

    it('should initialize', () => {
        attachmentUnitFormComponentFixture.detectChanges();
        expect(attachmentUnitFormComponent).to.be.ok;
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

        expect(attachmentUnitFormComponent.nameControl?.value).to.equal(formData.formProperties.name);
        expect(attachmentUnitFormComponent.releaseDateControl?.value).to.equal(formData.formProperties.releaseDate);
        expect(attachmentUnitFormComponent.descriptionControl?.value).to.equal(formData.formProperties.description);
        expect(attachmentUnitFormComponent.versionControl?.value).to.equal(formData.formProperties.version);
        expect(attachmentUnitFormComponent.updateNotificationTextControl?.value).to.equal(formData.formProperties.updateNotificationText);
        expect(attachmentUnitFormComponent.fileName).to.equal(formData.fileProperties.fileName);
        expect(attachmentUnitFormComponent.file).to.equal(formData.fileProperties.file);
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
        expect(attachmentUnitFormComponent.form.valid).to.be.true;

        const submitFormSpy = sinon.spy(attachmentUnitFormComponent, 'submitForm');
        const submitFormEventSpy = sinon.spy(attachmentUnitFormComponent.formSubmitted, 'emit');

        const submitButton = attachmentUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        expect(submitFormSpy).to.have.been.called;
        expect(submitFormEventSpy).to.have.been.calledWith({
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

        submitFormSpy.restore();
        submitFormEventSpy.restore();
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

        expect(attachmentUnitFormComponent.form.invalid).to.be.true;
        const submitFormSpy = sinon.spy(attachmentUnitFormComponent, 'submitForm');
        const submitFormEventSpy = sinon.spy(attachmentUnitFormComponent.formSubmitted, 'emit');

        const submitButton = attachmentUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        expect(submitFormSpy).to.not.have.been.called;
        expect(submitFormEventSpy).to.not.have.been.called;
    });

    it('calls on file change on changed file', () => {
        const fakeBlob = new Blob([''], { type: 'application/pdf' });
        fakeBlob['name'] = 'Test-File.pdf';
        const onFileChangeStub = sandbox.stub(attachmentUnitFormComponent, 'onFileChange');
        attachmentUnitFormComponentFixture.detectChanges();
        const fileInput = attachmentUnitFormComponentFixture.debugElement.nativeElement.querySelector('#fileInput');
        fileInput.dispatchEvent(new Event('change'));
        expect(onFileChangeStub).to.have.been.calledOnce;
    });

    it('sets file upload error correctly', () => {
        attachmentUnitFormComponentFixture.detectChanges();
        attachmentUnitFormComponent.setFileUploadError('lorem ipsum');
        expect(attachmentUnitFormComponent.fileUploadErrorMessage).to.equal('lorem ipsum');
    });
});
