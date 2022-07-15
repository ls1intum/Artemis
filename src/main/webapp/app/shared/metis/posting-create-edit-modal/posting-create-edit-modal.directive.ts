import { Directive, EventEmitter, Input, OnChanges, OnInit, Output, TemplateRef, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Posting } from 'app/entities/metis/posting.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { PostingEditType } from 'app/shared/metis/metis.util';

const MAX_CONTENT_LENGTH = 1000;

@Directive()
export abstract class PostingCreateEditModalDirective<T extends Posting> implements OnInit, OnChanges {
    @Input() posting: T;
    @ViewChild('postingEditor') postingEditor: TemplateRef<any>;
    @Output() onCreate: EventEmitter<T> = new EventEmitter<T>();
    modalTitle: string;
    isLoading = false;
    maxContentLength = MAX_CONTENT_LENGTH;
    content: string;
    formGroup: FormGroup;
    readonly EditType = PostingEditType;

    protected constructor(protected metisService: MetisService, protected modalService: NgbModal, protected formBuilder: FormBuilder) {}

    get editType(): PostingEditType {
        return this.posting.id ? PostingEditType.UPDATE : PostingEditType.CREATE;
    }

    /**
     * on initialization: sets the content, and the modal title (edit or create)
     */
    ngOnInit(): void {
        this.content = this.posting.content ?? '';
        this.updateModalTitle();
    }

    /**
     * on changes: sets the content, and the modal title (edit or create), resets the from
     */
    ngOnChanges(): void {
        this.content = this.posting?.content ?? '';
        this.updateModalTitle();
        this.resetFormGroup();
    }

    /**
     * checks if the form group is valid, changes the clicked button to indicate a loading process,
     * set the input content (updated or new; of post and answer post) delegates to the corresponding method
     */
    confirm(): void {
        if (this.formGroup.valid) {
            this.isLoading = true;
            if (this.editType === PostingEditType.UPDATE) {
                this.updatePosting();
            } else if (this.editType === PostingEditType.CREATE) {
                this.createPosting();
            }
        }
    }

    abstract open(): void;

    abstract updateModalTitle(): void;

    abstract resetFormGroup(): void;

    abstract createPosting(): void;

    abstract updatePosting(): void;
}
