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
    private readonly loading = signal<boolean>(false);

    readonly fullscreenMetadata = this.metadata.asReadonly();
    readonly currentPage = this.page.asReadonly();
    readonly iframeLoading = this.loading.asReadonly();

    open(pdfUrl: string, currentPage: number, uploadDate?: Dayjs, version?: number): void {
        this.metadata.set({
            isOpen: true,
            pdfUrl,
            uploadDate,
            version,
        });
        this.page.set(currentPage);
        this.loading.set(true);
    }

    close(): void {
        this.metadata.update((m) => ({ ...m, isOpen: false }));
        this.loading.set(false);
    }

    updateCurrentPage(pageNumber: number): void {
        this.page.set(pageNumber);
    }

    setIframeLoading(isLoading: boolean): void {
        this.loading.set(isLoading);
    }
}
