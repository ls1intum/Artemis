import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import dayjs from 'dayjs';
import { VideoUnitFormComponent, VideoUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/video-unit-form/video-unit-form.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

chai.use(sinonChai);
const expect = chai.expect;
describe('VideoUnitFormComponent', () => {
    const sandbox = sinon.createSandbox();
    const validYouTubeUrl = 'https://www.youtube.com/watch?v=8iU8LPEa4o0';
    const validYouTubeUrlInEmbeddableFormat = 'https://www.youtube.com/embed/8iU8LPEa4o0';
    let videoUnitFormComponentFixture: ComponentFixture<VideoUnitFormComponent>;
    let videoUnitFormComponent: VideoUnitFormComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule],
            declarations: [VideoUnitFormComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FormDateTimePickerComponent), MockComponent(FaIconComponent)],
            providers: [],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                videoUnitFormComponentFixture = TestBed.createComponent(VideoUnitFormComponent);
                videoUnitFormComponent = videoUnitFormComponentFixture.componentInstance;
            });
    });

    afterEach(function () {
        sandbox.restore();
    });

    it('should initialize', () => {
        videoUnitFormComponentFixture.detectChanges();
        expect(videoUnitFormComponent).to.be.ok;
    });

    it('should not submit a form when name is missing', () => {
        sandbox.stub(videoUnitFormComponent, 'urlValidator').returns(null);
        sandbox.stub(videoUnitFormComponent, 'videoUrlValidator').returns(null);
        videoUnitFormComponentFixture.detectChanges();
        const exampleDescription = 'lorem ipsum';
        videoUnitFormComponent.descriptionControl!.setValue(exampleDescription);
        const exampleReleaseDate = dayjs().year(2010).month(3).date(5);
        videoUnitFormComponent.releaseDateControl!.setValue(exampleReleaseDate);
        videoUnitFormComponent.sourceControl!.setValue(validYouTubeUrlInEmbeddableFormat);
        videoUnitFormComponentFixture.detectChanges();
        expect(videoUnitFormComponent.form.invalid).to.be.true;

        const submitFormSpy = sinon.spy(videoUnitFormComponent, 'submitForm');
        const submitFormEventSpy = sinon.spy(videoUnitFormComponent.formSubmitted, 'emit');

        const submitButton = videoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        videoUnitFormComponentFixture.whenStable().then(() => {
            expect(submitFormSpy).to.not.have.been.called;
            expect(submitFormEventSpy).to.not.have.been.called;
        });
    });

    it('should not submit a form when source is missing', () => {
        sandbox.stub(videoUnitFormComponent, 'urlValidator').returns(null);
        sandbox.stub(videoUnitFormComponent, 'videoUrlValidator').returns(null);
        videoUnitFormComponentFixture.detectChanges();
        const exampleName = 'test';
        videoUnitFormComponent.nameControl!.setValue(exampleName);
        const exampleDescription = 'lorem ipsum';
        videoUnitFormComponent.descriptionControl!.setValue(exampleDescription);
        const exampleReleaseDate = dayjs().year(2010).month(3).date(5);
        videoUnitFormComponent.releaseDateControl!.setValue(exampleReleaseDate);
        videoUnitFormComponentFixture.detectChanges();
        expect(videoUnitFormComponent.form.invalid).to.be.true;

        const submitFormSpy = sinon.spy(videoUnitFormComponent, 'submitForm');
        const submitFormEventSpy = sinon.spy(videoUnitFormComponent.formSubmitted, 'emit');

        const submitButton = videoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        videoUnitFormComponentFixture.whenStable().then(() => {
            expect(submitFormSpy).to.not.have.been.called;
            expect(submitFormEventSpy).to.not.have.been.called;
        });
    });

    it('should submit valid form', () => {
        sandbox.stub(videoUnitFormComponent, 'urlValidator').returns(null);
        sandbox.stub(videoUnitFormComponent, 'videoUrlValidator').returns(null);
        videoUnitFormComponentFixture.detectChanges();
        const exampleName = 'test';
        videoUnitFormComponent.nameControl!.setValue(exampleName);
        const exampleDescription = 'lorem ipsum';
        videoUnitFormComponent.descriptionControl!.setValue(exampleDescription);
        const exampleReleaseDate = dayjs().year(2010).month(3).date(5);
        videoUnitFormComponent.releaseDateControl!.setValue(exampleReleaseDate);
        videoUnitFormComponent.sourceControl!.setValue(validYouTubeUrlInEmbeddableFormat);
        videoUnitFormComponentFixture.detectChanges();
        expect(videoUnitFormComponent.form.valid).to.be.true;

        const submitFormSpy = sinon.spy(videoUnitFormComponent, 'submitForm');
        const submitFormEventSpy = sinon.spy(videoUnitFormComponent.formSubmitted, 'emit');

        const submitButton = videoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        videoUnitFormComponentFixture.whenStable().then(() => {
            expect(submitFormSpy).to.have.been.called;
            expect(submitFormEventSpy).to.have.been.calledWith({
                name: exampleName,
                description: exampleDescription,
                releaseDate: exampleReleaseDate,
                source: validYouTubeUrlInEmbeddableFormat,
                urlHelper: null,
            });

            submitFormSpy.restore();
            submitFormEventSpy.restore();
        });
    });

    it('should correctly transform YouTube URL into embeddable format', () => {
        sandbox.stub(videoUnitFormComponent, 'extractEmbeddedUrl').returns(validYouTubeUrlInEmbeddableFormat);
        sandbox.stub(videoUnitFormComponent, 'urlValidator').returns(null);
        sandbox.stub(videoUnitFormComponent, 'videoUrlValidator').returns(null);

        videoUnitFormComponentFixture.detectChanges();

        videoUnitFormComponent.urlHelperControl!.setValue(validYouTubeUrl);
        videoUnitFormComponentFixture.detectChanges();
        const transformButton = videoUnitFormComponentFixture.debugElement.nativeElement.querySelector('#transformButton');
        transformButton.click();

        videoUnitFormComponentFixture.whenStable().then(() => {
            expect(videoUnitFormComponent.sourceControl?.value).to.equal(validYouTubeUrlInEmbeddableFormat);
        });
    });

    it('should correctly set form values in edit mode', () => {
        videoUnitFormComponent.isEditMode = true;
        const formData: VideoUnitFormData = {
            name: 'test',
            description: 'lorem ipsum',
            releaseDate: dayjs().year(2010).month(3).date(5),
            source: validYouTubeUrlInEmbeddableFormat,
        };
        videoUnitFormComponentFixture.detectChanges();

        videoUnitFormComponent.formData = formData;
        videoUnitFormComponent.ngOnChanges();

        expect(videoUnitFormComponent.nameControl?.value).to.equal(formData.name);
        expect(videoUnitFormComponent.releaseDateControl?.value).to.equal(formData.releaseDate);
        expect(videoUnitFormComponent.descriptionControl?.value).to.equal(formData.description);
        expect(videoUnitFormComponent.sourceControl?.value).to.equal(formData.source);
    });
});
