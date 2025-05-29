import { Injectable } from '@angular/core';
import { LocalStorageService } from 'ngx-webstorage';
import { inject } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class DraftService {
    private localStorageService = inject(LocalStorageService);

    saveDraft(key: string, content: string): void {
        if (key && key !== '' && content && content.trim()) {
            this.localStorageService.store(key, content);
        } else if (key && key !== '') {
            this.clearDraft(key);
        }
    }

    loadDraft(key: string): string | undefined {
        if (key && key !== '') {
            const draft = this.localStorageService.retrieve(key);
            if (draft && draft.trim()) {
                return draft;
            } else {
                this.clearDraft(key);
            }
        }
        return undefined;
    }

    clearDraft(key: string): void {
        if (key && key !== '') {
            this.localStorageService.clear(key);
        }
    }
}
