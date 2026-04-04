import { Injectable, signal } from '@angular/core';
import type { Dayjs } from 'dayjs/esm';

export interface PdfFullscreenMetadata {
    isOpen: boolean;
    pdfUrl?: string;
    uploadDate?: Dayjs;
    version?: number;
}

/**
 * Shared state service for coordinating fullscreen PDF overlay state across viewer instances.
 */
@Injectable({ providedIn: 'root' })
export class PdfFullscreenOverlayService {
    private readonly metadata = signal<PdfFullscreenMetadata>({ isOpen: false });
    private readonly page = signal<number>(1);
    private downloadRequestedCallback?: () => void;

    readonly fullscreenMetadata = this.metadata.asReadonly();
    readonly currentPage = this.page.asReadonly();

    /**
     * Opens the fullscreen overlay and initializes metadata and current page.
     */
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

    /**
     * Closes the fullscreen overlay and clears temporary callbacks.
     */
    close(): void {
        this.metadata.set({ isOpen: false });
        this.downloadRequestedCallback = undefined;
    }

    /**
     * Synchronizes the currently displayed page between embedded and fullscreen viewers.
     */
    updateCurrentPage(pageNumber: number): void {
        this.page.set(pageNumber);
    }

    /**
     * Forwards download requests triggered from fullscreen controls.
     */
    triggerDownloadRequested(): void {
        this.downloadRequestedCallback?.();
    }
}
