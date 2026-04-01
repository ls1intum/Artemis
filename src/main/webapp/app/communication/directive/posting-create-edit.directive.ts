import { Directive, OnInit, effect, inject, model, output, untracked } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { MetisService } from 'app/communication/service/metis.service';
import { PostingEditType } from 'app/communication/metis.util';

import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';

// Note: this number should be the same as in Posting.java
const MAX_CONTENT_LENGTH = 5000;

@Directive()
export abstract class PostingCreateEditDirective<T extends Posting> implements OnInit {
    protected metisService = inject(MetisService);
    protected formBuilder = inject(FormBuilder);

    readonly posting = model<T>();
    readonly onCreate = output<T>();
    readonly isModalOpen = output<void>();

    isLoading = false;
    maxContentLength = MAX_CONTENT_LENGTH;
    editorHeight = MarkdownEditorHeight.INLINE;
    content: string;
    formGroup: FormGroup;

    readonly EditType = PostingEditType;

    constructor() {
        effect(() => {
            this.posting();
            untracked(() => this.onPostingChanged());
        });
    }

    get editType(): PostingEditType {
        return this.posting()?.id ? PostingEditType.UPDATE : PostingEditType.CREATE;
    }

    /**
     * on initialization: sets the content, and the modal title (edit or create)
     */
    ngOnInit(): void {
        this.content = this.posting()?.content ?? '';
    }

    /**
     * Called when the posting signal changes. Override in subclasses and call super.
     */
    protected onPostingChanged(): void {
        const posting = this.posting();
        if (!posting) {
            return;
        }
        this.content = posting.content ?? '';
        this.resetFormGroup();
    }

    /**
     * checks if the form group is valid, changes the clicked button to indicate a loading process,
     * set the input content (updated or new; of post and answer post) delegates to the corresponding method
     */
    confirm(): void {
        if (this.isLoading) return;
        if (this.formGroup.valid) {
            this.isLoading = true;
            if (this.editType === PostingEditType.UPDATE) {
                this.updatePosting();
            } else if (this.editType === PostingEditType.CREATE) {
                this.createPosting();
            }
        }
    }

    abstract resetFormGroup(): void;

    abstract createPosting(): void;

    abstract updatePosting(): void;
}
