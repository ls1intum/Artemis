import { TestBed, fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
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
        fixture.changeDetectorRef.detectChanges();
        expect(loadingStatusSpy).toHaveBeenCalledWith(ImageLoadingStatus.LOADING);

        const req = httpMock.expectOne('/test-image.png');
        const blob = new Blob(['fake image'], { type: 'image/png' });
        req.flush(blob);
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();
        expect(loadingStatusSpy).toHaveBeenCalledWith(ImageLoadingStatus.SUCCESS);

        const imageElement: HTMLImageElement = fixture.nativeElement.querySelector('img');
        expect(imageElement.getAttribute('src')).toBe(testLocalImageURL);
    });

    it('should emit SUCCESS when retryLoadImage is called after error', fakeAsync(() => {
        const reloadSpy = jest.spyOn<any, any>(component['imageResource'], 'reload').mockImplementation(() => {
            component['rawLocalImageUrl'] = testLocalImageURL;
            component['imageResource'].hasValue = () => true;
            component['imageResource'].value = () => testLocalImageURL as any;
            (component as any).updateLoadingStatusDependingOnIf(true, false, false);
            (component as any).updateLoadingStatusDependingOnIf(false, false, true);
        });
        const loadingStatusSpy = jest.fn();
        component.loadingStatus.subscribe(loadingStatusSpy);

        fixture.componentRef.setInput('src', '/error-image.png');
        fixture.changeDetectorRef.detectChanges();
        tick();

        // First request fails
        const requestOne = httpMock.expectOne('/error-image.png');
        requestOne.error(new ProgressEvent('Network error'));
        // Manually trigger retry (simulates user action or programmatic retry)
        component.retryLoadImage();
        flushMicrotasks();
        tick();
        fixture.changeDetectorRef.detectChanges();

        expect(reloadSpy).toHaveBeenCalledOnce();
        expect(loadingStatusSpy).toHaveBeenCalledWith(ImageLoadingStatus.LOADING);
        expect(loadingStatusSpy).toHaveBeenCalledWith(ImageLoadingStatus.SUCCESS);
    }));

    it('should reload when retryLoadImage is called', async () => {
        const reloadSpy = jest.spyOn<any, any>(component['imageResource'], 'reload');
        const statusSpy = jest.fn();
        component.loadingStatus.subscribe(statusSpy);

        fixture.componentRef.setInput('src', '/reload-image.png');
        fixture.changeDetectorRef.detectChanges();

        httpMock.expectOne('/reload-image.png').flush(new Blob());
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        component.retryLoadImage();
        fixture.changeDetectorRef.detectChanges();
        expect(reloadSpy).toHaveBeenCalledOnce();

        httpMock.expectOne('/reload-image.png').flush(new Blob());
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        expect(statusSpy).toHaveBeenCalledWith(ImageLoadingStatus.SUCCESS);

        const img: HTMLImageElement = fixture.nativeElement.querySelector('img');
        expect(img.getAttribute('src')).toBe(testLocalImageURL);
    });

    it('should have src and alt attributes unset while no value is available', () => {
        const loadingStatusSpy = jest.fn();
        component.loadingStatus.subscribe(loadingStatusSpy);

        fixture.componentRef.setInput('src', '/delayed-image.png');
        fixture.changeDetectorRef.detectChanges();

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
