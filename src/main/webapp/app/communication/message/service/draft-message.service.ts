import { Injectable } from '@angular/core';
import { inject } from '@angular/core';
import { LocalStorageService } from 'app/shared/storage/local-storage.service';

const DRAFT_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

@Injectable({ providedIn: 'root' })
export class DraftService {
    private localStorageService = inject(LocalStorageService);

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

    loadDraft(key: string): string | undefined {
        if (!key) {
            return undefined;
        }

        const raw = this.localStorageService.retrieve<string>(key);

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
        if (key) {
            this.localStorageService.remove(key);
        }
    }
}

interface DraftData {
    content: string;
    timestamp: number;
}
