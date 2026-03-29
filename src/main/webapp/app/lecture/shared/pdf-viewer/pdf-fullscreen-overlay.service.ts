import { Injectable, signal } from '@angular/core';
import type { Dayjs } from 'dayjs/esm';

export interface PdfFullscreenState {
    isOpen: boolean;
    pdfUrl?: string;
    currentPage?: number;
    uploadDate?: Dayjs;
    version?: number;
}

@Injectable({ providedIn: 'root' })
export class PdfFullscreenOverlayService {
    private readonly state = signal<PdfFullscreenState>({ isOpen: false });

    readonly fullscreenState = this.state.asReadonly();

    open(pdfUrl: string, currentPage: number, uploadDate?: Dayjs, version?: number): void {
        this.state.set({
            isOpen: true,
            pdfUrl,
            currentPage,
            uploadDate,
            version,
        });
    }

    close(): void {
        this.state.update((s) => ({ ...s, isOpen: false }));
    }

    updateCurrentPage(page: number): void {
        this.state.update((s) => ({ ...s, currentPage: page }));
    }
}
