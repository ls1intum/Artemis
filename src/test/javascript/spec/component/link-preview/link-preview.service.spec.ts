import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LinkPreviewService } from 'app/shared/link-preview/services/link-preview.service';

describe('LinkPreviewService', () => {
    let service: LinkPreviewService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [LinkPreviewService],
        });
        service = TestBed.inject(LinkPreviewService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('fetchLink should return link preview from HTTP response', fakeAsync(() => {
        const mockUrl = 'https://example.com';
        const mockPreview = {
            title: 'Example Website',
            description: 'This is an example website.',
            image: 'https://example.com/image.png',
            url: 'https://example.com',
        };

        service.fetchLink(mockUrl).subscribe((preview) => {
            expect(preview).toEqual(mockPreview);
        });

        const req = httpMock.expectOne('api/link-preview');
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toBe(mockUrl);

        req.flush(mockPreview);
        tick();
    }));

    it('fetchLink should return cached link preview for the same URL', fakeAsync(() => {
        const mockUrl = 'https://example.com';
        const mockPreview = {
            title: 'Example Website',
            description: 'This is an example website.',
            image: 'https://example.com/image.png',
            url: 'https://example.com',
        };

        // First request
        service.fetchLink(mockUrl).subscribe((preview) => {
            expect(preview).toEqual(mockPreview);
        });

        const req = httpMock.expectOne('api/link-preview');
        req.flush(mockPreview);
        tick();

        // Second request with the same URL
        service.fetchLink(mockUrl).subscribe((preview) => {
            expect(preview).toEqual(mockPreview);
        });

        // No HTTP request should be made since the preview is cached
        httpMock.expectNone('api/link-preview');
        tick();
    }));
});
