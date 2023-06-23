import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LinkPreviewComponent } from 'app/shared/link-preview/components/link-preview/link-preview.component';

describe('LinkPreviewComponent', () => {
    let component: LinkPreviewComponent;
    let fixture: ComponentFixture<LinkPreviewComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [LinkPreviewComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LinkPreviewComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render link preview when linkPreview is provided', () => {
        component.linkPreview = {
            title: 'Test Title',
            description: 'Test Description',
            image: 'test-image.jpg',
            url: 'https://example.com',
            shouldPreviewBeShown: true,
        };
        fixture.detectChanges();

        const previewContainer = fixture.nativeElement.querySelector('.preview-container');
        const previewCard = fixture.nativeElement.querySelector('.preview-card');
        const previewTitle = fixture.nativeElement.querySelector('.preview-title');
        const previewDescription = fixture.nativeElement.querySelector('.preview-description');
        const previewImage = fixture.nativeElement.querySelector('.preview-image img');

        expect(previewContainer).toBeTruthy();
        expect(previewCard).toBeTruthy();
        expect(previewTitle.textContent).toBe('Test Title');
        expect(previewDescription.textContent).toBe('Test Description');
        expect(previewImage.src).toContain('test-image.jpg');
    });

    it('should not render link preview when linkPreview is not provided', () => {
        const previewContainer = fixture.nativeElement.querySelector('.preview-container');

        expect(previewContainer).toBeFalsy();
    });

    it('should render loading spinner when linkPreview is not loaded and showLoadingsProgress is true', () => {
        component.showLoadingsProgress = true;
        fixture.detectChanges();

        const loadingContainer = fixture.nativeElement.querySelector('.loading-container');
        const loadingSpinner = fixture.nativeElement.querySelector('.loading-spinner');

        expect(loadingContainer).toBeTruthy();
        expect(loadingSpinner).toBeTruthy();
    });

    it('should not render loading spinner when linkPreview is loaded', () => {
        component.loaded = true;
        fixture.detectChanges();

        const loadingContainer = fixture.nativeElement.querySelector('.loading-container');

        expect(loadingContainer).toBeFalsy();
    });

    it('should not render loading spinner when showLoadingsProgress is false', () => {
        component.showLoadingsProgress = false;
        fixture.detectChanges();

        const loadingContainer = fixture.nativeElement.querySelector('.loading-container');

        expect(loadingContainer).toBeFalsy();
    });

    it('should not render error message when hasError is false', () => {
        component.hasError = false;
        fixture.detectChanges();

        const errorContainer = fixture.nativeElement.querySelector('.error-container');

        expect(errorContainer).toBeFalsy();
    });
});
