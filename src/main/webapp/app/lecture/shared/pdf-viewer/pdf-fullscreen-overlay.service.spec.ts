import { describe, expect, it } from 'vitest';
import { PdfFullscreenOverlayService } from './pdf-fullscreen-overlay.service';
import dayjs from 'dayjs/esm';

describe('PdfFullscreenOverlayService', () => {
    it('should initialize with closed state', () => {
        const service = new PdfFullscreenOverlayService();
        const metadata = service.fullscreenMetadata();
        const page = service.currentPage();

        expect(metadata.isOpen).toBe(false);
        expect(metadata.pdfUrl).toBeUndefined();
        expect(metadata.uploadDate).toBeUndefined();
        expect(metadata.version).toBeUndefined();
        expect(page).toBe(1);
    });

    it('should update current page', () => {
        const service = new PdfFullscreenOverlayService();

        service.open('test.pdf', 1, undefined, undefined);
        service.updateCurrentPage(10);
        const metadata = service.fullscreenMetadata();
        const page = service.currentPage();

        expect(page).toBe(10);
        expect(metadata.isOpen).toBe(true);
        expect(metadata.pdfUrl).toBe('test.pdf');
    });

    it('should handle multiple open/close cycles and reset metadata on close', () => {
        const service = new PdfFullscreenOverlayService();
        const uploadDate1 = dayjs();
        const uploadDate2 = dayjs().add(1, 'day');

        // First open with all parameters
        service.open('first.pdf', 3, uploadDate1, 1);
        let metadata = service.fullscreenMetadata();
        let page = service.currentPage();

        expect(metadata.isOpen).toBe(true);
        expect(metadata.pdfUrl).toBe('first.pdf');
        expect(page).toBe(3);
        expect(metadata.uploadDate).toBe(uploadDate1);
        expect(metadata.version).toBe(1);

        // Close and verify metadata is reset
        service.close();
        metadata = service.fullscreenMetadata();
        page = service.currentPage();

        expect(metadata.isOpen).toBe(false);
        expect(metadata.pdfUrl).toBeUndefined();
        expect(page).toBe(3);
        expect(metadata.uploadDate).toBeUndefined();
        expect(metadata.version).toBeUndefined();

        // Second open with different parameters
        service.open('second.pdf', 5, uploadDate2, 2);
        metadata = service.fullscreenMetadata();
        page = service.currentPage();

        expect(metadata.isOpen).toBe(true);
        expect(metadata.pdfUrl).toBe('second.pdf');
        expect(page).toBe(5);
        expect(metadata.uploadDate).toBe(uploadDate2);
        expect(metadata.version).toBe(2);
    });

    it('should provide readonly signals', () => {
        const service = new PdfFullscreenOverlayService();
        const metadata = service.fullscreenMetadata;
        const page = service.currentPage;

        // The signals should be readonly - this is a type-level guarantee
        // We can verify they're signals by checking they're callable
        expect(typeof metadata).toBe('function');
        expect(typeof metadata()).toBe('object');
        expect(typeof page).toBe('function');
        expect(typeof page()).toBe('number');
    });
});
