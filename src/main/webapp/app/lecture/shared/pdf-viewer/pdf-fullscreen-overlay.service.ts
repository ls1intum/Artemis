import { Injectable, signal } from '@angular/core';
import type { Dayjs } from 'dayjs/esm';

export interface PdfFullscreenMetadata {
    isOpen: boolean;
    pdfUrl?: string;
    uploadDate?: Dayjs;
    version?: number;
}

@Injectable({ providedIn: 'root' })
export class PdfFullscreenOverlayService {
    private readonly metadata = signal<PdfFullscreenMetadata>({ isOpen: false });
    private readonly page = signal<number>(1);
    private downloadRequestedCallback?: () => void;

    readonly fullscreenMetadata = this.metadata.asReadonly();
    readonly currentPage = this.page.asReadonly();

    open(pdfUrl: string, currentPage: number, uploadDate?: Dayjs, version?: number, onDownloadRequested?: () => void): void {
        this.metadata.set({
            isOpen: true,
            pdfUrl,
            uploadDate,
            version,
        });
        this.page.set(currentPage);
        this.downloadRequestedCallback = onDownloadRequested;
    }

    close(): void {
        this.metadata.set({ isOpen: false });
        this.downloadRequestedCallback = undefined;
    }

    updateCurrentPage(pageNumber: number): void {
        this.page.set(pageNumber);
    }

    triggerDownloadRequested(): void {
        this.downloadRequestedCallback?.();
    }
}
