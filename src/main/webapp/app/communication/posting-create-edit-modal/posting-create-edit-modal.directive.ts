import { Directive, OnChanges, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { PostingCreateEditDirective } from 'app/communication/directive/posting-create-edit.directive';

@Directive()
export abstract class PostingCreateEditModalDirective<T extends Posting> extends PostingCreateEditDirective<T> implements OnInit, OnChanges {
    @ViewChild('postingEditor') postingEditor: TemplateRef<any>;
    modalTitle: string;

    /**
     * on initialization: sets the content, and the modal title (edit or create)
     */
    ngOnInit(): void {
        super.ngOnInit();
        this.updateModalTitle();
    }

    /**
     * on changes: sets the content, and the modal title (edit or create), resets the form
     */
    ngOnChanges() {
        super.ngOnChanges();
        this.updateModalTitle();
    }

    abstract open(): void;

    abstract updateModalTitle(): void;
}
