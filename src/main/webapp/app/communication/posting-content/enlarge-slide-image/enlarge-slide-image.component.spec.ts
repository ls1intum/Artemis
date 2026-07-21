import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DynamicDialogConfig } from 'primeng/dynamicdialog';
import { EnlargeSlideImageComponent } from 'app/communication/posting-content/enlarge-slide-image/enlarge-slide-image.component';
import { beforeEach, describe, expect, it } from 'vitest';

describe('EnlargeSlideImageComponent', () => {
    let component: EnlargeSlideImageComponent;
    let fixture: ComponentFixture<EnlargeSlideImageComponent>;

    const slideToReference = '/path/to/image.png';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [EnlargeSlideImageComponent],
            providers: [{ provide: DynamicDialogConfig, useValue: { data: { slideToReference } } }],
        });
        fixture = TestBed.createComponent(EnlargeSlideImageComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should expose the slide reference from the dialog config', () => {
        expect(component.data.slideToReference).toBe(slideToReference);
    });

    it('should render the image with the provided source', () => {
        const image: HTMLImageElement | null = fixture.nativeElement.querySelector('img');
        expect(image).toBeTruthy();
        expect(image!.getAttribute('src')).toBe(slideToReference);
    });
});
