import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { EnlargeSlideImageComponent } from 'app/communication/posting-content/enlarge-slide-image/enlarge-slide-image.component';
import { beforeEach, describe, expect, it, vi } from 'vitest';

describe('EnlargeSlideImageComponent', () => {
    let component: EnlargeSlideImageComponent;
    let fixture: ComponentFixture<EnlargeSlideImageComponent>;
    let dialogRef: DynamicDialogRef;

    const slideToReference = '/path/to/image.png';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [EnlargeSlideImageComponent],
            providers: [
                { provide: DynamicDialogConfig, useValue: { data: { slideToReference } } },
                { provide: DynamicDialogRef, useValue: { close: vi.fn() } },
            ],
        });
        fixture = TestBed.createComponent(EnlargeSlideImageComponent);
        component = fixture.componentInstance;
        dialogRef = TestBed.inject(DynamicDialogRef);
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

    it('should render an accessible close button', () => {
        const closeButton: HTMLButtonElement | null = fixture.nativeElement.querySelector('.btn-close');
        expect(closeButton).toBeTruthy();
        expect(closeButton!.getAttribute('aria-label')).toBe('Close');
    });

    it('should close the dialog when the close button is clicked', () => {
        const closeButton: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-close');
        closeButton.click();
        expect(dialogRef.close).toHaveBeenCalledOnce();
    });

    it('should close the dialog when close() is called', () => {
        component.close();
        expect(dialogRef.close).toHaveBeenCalledOnce();
    });

    it('should close the dialog when the Escape key is pressed', () => {
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
        expect(dialogRef.close).toHaveBeenCalledOnce();
    });
});
