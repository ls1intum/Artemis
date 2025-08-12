import { Injectable } from '@angular/core';
import { inject } from '@angular/core';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

const DRAFT_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

@Injectable({ providedIn: 'root' })
export class DraftService {
    private localStorageService = inject(LocalStorageService);

    /**
     * Saves a draft message to local storage.
     * If the content is empty or whitespace-only, the draft is cleared instead.
     *
     * @param key - The unique key for storing the draft
     * @param content - The draft content to save
     */
    saveDraft(key: string, content: string): void {
        const trimmedContent = content.trim();
        if (key && trimmedContent) {
            const draftData: DraftData = {
                content: trimmedContent,
                timestamp: Date.now(),
            };
            this.localStorageService.store<DraftData>(key, draftData);
        } else if (key) {
            this.clearDraft(key);
        }
    }

    /**
     * Loads a draft message from local storage.
     * Returns undefined if the draft doesn't exist, is expired (older than 7 days), or is invalid.
     *
     * @param key - The unique key for retrieving the draft
     * @returns The draft content if valid and not expired, undefined otherwise
     */
    loadDraft(key: string): string | undefined {
        if (!key) return undefined;

        const stored = this.localStorageService.retrieve<DraftData | string>(key);

        if (!stored) return undefined;

        if (typeof stored === 'string') {
            const trimmed = stored.trim();
            return trimmed ? trimmed : undefined;
        }

        if (this.isDraftData(stored)) {
            if (Date.now() - stored.timestamp > DRAFT_EXPIRY_MS) {
                this.clearDraft(key);
                return undefined;
            }
            const trimmed = stored.content?.trim();
            return trimmed ? trimmed : undefined;
        }

        return undefined;
    }

    private isDraftData(value: any): value is DraftData {
        return typeof value === 'object' && value !== null && typeof value.content === 'string' && typeof value.timestamp === 'number';
    }

    clearDraft(key: string): void {
        if (key) {
            this.localStorageService.remove(key);
        }
    }
}

export interface DraftData {
    content: string;
    timestamp: number;
}
