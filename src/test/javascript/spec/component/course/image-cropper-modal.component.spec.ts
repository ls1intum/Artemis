import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ImageCropperModalComponent } from 'app/course/manage/image-cropper-modal.component';

describe('ImageCropperModalComponent', () => {
    let component: ImageCropperModalComponent;
    let fixture: ComponentFixture<ImageCropperModalComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ImageCropperModalComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(ImageCropperModalComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
