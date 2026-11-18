import { Directive, inject } from '@angular/core';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { PostingCreateEditDirective } from 'app/communication/directive/posting-create-edit.directive';
import { DraftService } from './service/draft-message.service';

/** Draft persistence shared by conversation and thread inputs, each scoped by its own draft key. */
@Directive()
export abstract class DraftMessageInputDirective<T extends Posting> extends PostingCreateEditDirective<T> {
    private readonly draftService = inject(DraftService);

    protected abstract getDraftKey(): string;

    protected saveDraft(content: string): void {
        const key = this.getDraftKey();
        if (!key) {
            return;
        }
        this.draftService.saveDraft(key, content);
    }

    protected loadDraft(): void {
        const key = this.getDraftKey();
        if (!key) {
            return;
        }
        const draft = this.draftService.loadDraft(key);
        if (draft && this.posting() && this.formGroup) {
            this.formGroup.get('content')?.setValue(draft);
        }
    }

    protected clearDraft(): void {
        const key = this.getDraftKey();
        if (!key) {
            return;
        }
        this.draftService.clearDraft(key);
    }
}
