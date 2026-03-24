import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LinkPreviewService } from 'app/communication/link-preview/services/link-preview.service';

describe('LinkPreviewService', () => {
    setupTestBed({ zoneless: true });

    let service: LinkPreviewService;
    let httpMock: HttpTestingController;

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
        httpMock.verify();
    });

    beforeEach(() => {
        vi.useFakeTimers();
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), LinkPreviewService],
        });
        service = TestBed.inject(LinkPreviewService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('fetchLink should return link preview from HTTP response', () => {
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

        const req = httpMock.expectOne('api/communication/link-preview?url=https%253A%252F%252Fexample.com');
        expect(req.request.method).toBe('GET');
        expect(req.request.body).toBeNull();

        req.flush(mockPreview);
        vi.advanceTimersByTime(0);
    });

    it('fetchLink should return cached link preview for the same URL', () => {
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

        const req = httpMock.expectOne('api/communication/link-preview?url=https%253A%252F%252Fexample.com');
        req.flush(mockPreview);
        vi.advanceTimersByTime(0);

        // Second request with the same URL
        service.fetchLink(mockUrl).subscribe((preview) => {
            expect(preview).toEqual(mockPreview);
        });

        // No HTTP request should be made since the preview is cached
        httpMock.expectNone('api/communication/link-preview');
        vi.advanceTimersByTime(0);
    });
});
