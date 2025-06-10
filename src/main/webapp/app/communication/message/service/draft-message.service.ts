import { Injectable } from '@angular/core';
import { LocalStorageService } from 'ngx-webstorage';
import { inject } from '@angular/core';

const DRAFT_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

@Injectable({ providedIn: 'root' })
export class DraftService {
    private localStorageService = inject(LocalStorageService);

    saveDraft(key: string, content: string): void {
        const trimmedContent = content.trim();
        if (key && key !== '' && content && content.trim()) {
            const draftData: DraftData = {
                content: trimmedContent,
                timestamp: Date.now(),
            };
            this.localStorageService.store(key, JSON.stringify(draftData));
        } else if (key && key !== '') {
            this.clearDraft(key);
        }
    }

    loadDraft(key: string): string | undefined {
        if (!key || key === '') {
            return undefined;
        }

        const raw = this.localStorageService.retrieve(key);

        if (raw) {
            try {
                const draftData: DraftData = JSON.parse(raw);
                if (
                    typeof draftData === 'object' &&
                    draftData !== null &&
                    'content' in draftData &&
                    'timestamp' in draftData &&
                    typeof draftData.content === 'string' &&
                    typeof draftData.timestamp === 'number'
                ) {
                    // Check expiry
                    if (Date.now() - draftData.timestamp > DRAFT_EXPIRY_MS) {
                        this.clearDraft(key);
                        return undefined;
                    }
                    if (draftData.content.trim()) {
                        return draftData.content;
                    }
                }
            } catch {
                // fallback for old drafts (plain string)
                if (typeof raw === 'string' && raw.trim()) {
                    return raw;
                }
            }
        }
    }

    clearDraft(key: string): void {
        if (key && key !== '') {
            this.localStorageService.clear(key);
        }
    }
}

interface DraftData {
    content: string;
    timestamp: number;
}
