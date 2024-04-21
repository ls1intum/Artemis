import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ImageCropperModalComponent } from 'app/course/manage/image-cropper-modal.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ImageCropperModule } from 'app/shared/image-cropper/image-cropper.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('ImageCropperModalComponent', () => {
    let component: ImageCropperModalComponent;
    let fixture: ComponentFixture<ImageCropperModalComponent>;
    let ngbActiveModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisSharedCommonModule, ImageCropperModule, ImageCropperModalComponent],
            providers: [NgbActiveModal, MockProvider(TranslateService)],
            declarations: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ImageCropperModalComponent);
                component = fixture.componentInstance;
                ngbActiveModal = TestBed.inject(NgbActiveModal);
                fixture.detectChanges();
            });
    });

    it('should call onCancel when cancel button is clicked', () => {
        jest.spyOn(component, 'onCancel');
        const cancelButton = fixture.debugElement.nativeElement.querySelector('#cancel-cropping-button');

        cancelButton.click();
        fixture.detectChanges();

        expect(component.onCancel).toHaveBeenCalled();
    });

    it('should call onSave when save button is clicked', () => {
        jest.spyOn(component, 'onSave');
        const saveButton = fixture.debugElement.nativeElement.querySelector('#save-cropping-button');

        saveButton.click();
        fixture.detectChanges();

        expect(component.onSave).toHaveBeenCalled();
    });

    it('should close the modal when onCancel is called', () => {
        jest.spyOn(ngbActiveModal, 'close');
        component.onCancel();
        expect(ngbActiveModal.close).toHaveBeenCalled();
    });

    it('should close the modal with cropped image when onSave is called', () => {
        jest.spyOn(ngbActiveModal, 'close');
        component.croppedImage = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAU...';
        component.onSave();
        expect(ngbActiveModal.close).toHaveBeenCalledWith(component.croppedImage);
    });
});
