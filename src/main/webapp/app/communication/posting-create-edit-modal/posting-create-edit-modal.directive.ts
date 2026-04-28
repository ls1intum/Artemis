import { Directive, OnInit, TemplateRef, viewChild } from '@angular/core';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { PostingCreateEditDirective } from 'app/communication/directive/posting-create-edit.directive';

@Directive()
export abstract class PostingCreateEditModalDirective<T extends Posting> extends PostingCreateEditDirective<T> implements OnInit {
    readonly postingEditor = viewChild<TemplateRef<any>>('postingEditor');
    modalTitle: string;

    /**
     * on initialization: sets the content, and the modal title (edit or create)
     */
    ngOnInit(): void {
        super.ngOnInit();
        this.updateModalTitle();
    }

    protected override onPostingChanged(): void {
        super.onPostingChanged();
        if (this.posting()) {
            this.updateModalTitle();
        }
    }

    abstract open(): void;

    abstract updateModalTitle(): void;
}
