import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ImageComponent, ImageLoadingStatus } from './image.component';

describe('ImageComponent', () => {
    let fixture: any;
    let component: ImageComponent;
    let httpMock: HttpTestingController;

    const testLocalImageURL = 'blob:mock';

    beforeAll(() => {
        global.URL.createObjectURL = jest.fn(() => testLocalImageURL);
        global.URL.revokeObjectURL = jest.fn();
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ImageComponent],
            providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
        }).compileComponents();

        fixture = TestBed.createComponent(ImageComponent);
        component = fixture.componentInstance;
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should emit SUCCESS when image loads and bind src attribute', async () => {
        const loadingStatusSpy = jest.fn();
        component.loadingStatus.subscribe(loadingStatusSpy);

        fixture.componentRef.setInput('src', '/test-image.png');
        fixture.detectChanges();
        expect(loadingStatusSpy).toHaveBeenCalledWith(ImageLoadingStatus.LOADING);

        const req = httpMock.expectOne('/test-image.png');
        const blob = new Blob(['fake image'], { type: 'image/png' });
        req.flush(blob);
        await fixture.whenStable();
        fixture.detectChanges();
        expect(loadingStatusSpy).toHaveBeenCalledWith(ImageLoadingStatus.SUCCESS);

        const imageElement: HTMLImageElement = fixture.nativeElement.querySelector('img');
        expect(imageElement.getAttribute('src')).toBe(testLocalImageURL);
    });

    it('should retry once on first error and then emit SUCCESS', async () => {
        const reloadSpy = jest.spyOn<any, any>(component['imageResource'], 'reload');
        const loadingStatusSpy = jest.fn();
        component.loadingStatus.subscribe(loadingStatusSpy);

        fixture.componentRef.setInput('src', '/error-image.png');
        fixture.detectChanges();

        const requestOne = httpMock.expectOne('/error-image.png');
        requestOne.error(new ProgressEvent('Network error'));
        await fixture.whenStable();
        fixture.detectChanges();

        const requestTwo = httpMock.expectOne('/error-image.png');
        const blob = new Blob(['fake image'], { type: 'image/png' });
        requestTwo.flush(blob);
        await fixture.whenStable();
        fixture.detectChanges();

        expect(reloadSpy).toHaveBeenCalledOnce();
        expect(loadingStatusSpy).toHaveBeenCalledWith(ImageLoadingStatus.SUCCESS);

        const imageElement: HTMLImageElement = fixture.nativeElement.querySelector('img');
        expect(imageElement.getAttribute('src')).toBe(testLocalImageURL);
    });

    it('should reload when retryLoadImage is called', async () => {
        const reloadSpy = jest.spyOn<any, any>(component['imageResource'], 'reload');
        const statusSpy = jest.fn();
        component.loadingStatus.subscribe(statusSpy);

        fixture.componentRef.setInput('src', '/reload-image.png');
        fixture.detectChanges();

        httpMock.expectOne('/reload-image.png').flush(new Blob());
        await fixture.whenStable();
        fixture.detectChanges();

        component.retryLoadImage();
        fixture.detectChanges();
        expect(reloadSpy).toHaveBeenCalledOnce();

        httpMock.expectOne('/reload-image.png').flush(new Blob());
        await fixture.whenStable();
        fixture.detectChanges();

        expect(statusSpy).toHaveBeenCalledWith(ImageLoadingStatus.SUCCESS);

        const img: HTMLImageElement = fixture.nativeElement.querySelector('img');
        expect(img.getAttribute('src')).toBe(testLocalImageURL);
    });

    it('should have src and alt attributes unset while no value is available', () => {
        const loadingStatusSpy = jest.fn();
        component.loadingStatus.subscribe(loadingStatusSpy);

        fixture.componentRef.setInput('src', '/delayed-image.png');
        fixture.detectChanges();

        const request = httpMock.expectOne('/delayed-image.png');
        expect(loadingStatusSpy).toHaveBeenCalledWith(ImageLoadingStatus.LOADING);

        const img: HTMLImageElement = fixture.nativeElement.querySelector('img');

        expect(img.hasAttribute('src')).toBeFalse();
        expect(img.getAttribute('src')).toBeNull();

        expect(img.hasAttribute('alt')).toBeFalse();
        expect(img.getAttribute('alt')).toBeNull();

        request.flush(new Blob(['x'], { type: 'image/png' }));
    });
});
