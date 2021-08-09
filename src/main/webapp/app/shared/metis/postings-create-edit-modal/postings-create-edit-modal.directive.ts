import { Directive, EventEmitter, Input, OnChanges, OnInit, Output, TemplateRef, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Posting } from 'app/entities/metis/posting.model';
import { MetisService } from 'app/shared/metis/metis.service';

const MAX_CONTENT_LENGTH = 1000;

enum EditType {
    CREATE,
    UPDATE,
}

@Directive()
export abstract class PostingsCreateEditModalDirective<T extends Posting> implements OnInit, OnChanges {
    readonly EditType = EditType;

    @Input() posting: T;
    @ViewChild('postingEditor') postingEditor: TemplateRef<any>;
    @Output() onCreate: EventEmitter<T> = new EventEmitter<T>();
    modalRef?: NgbModalRef;
    modalTitle: string;
    isLoading = false;
    maxContentLength = MAX_CONTENT_LENGTH;
    content: string;
    formGroup: FormGroup;

    protected constructor(protected metisService: MetisService, protected modalService: NgbModal, protected formBuilder: FormBuilder) {}

    get editType(): EditType {
        return this.posting.id ? EditType.UPDATE : EditType.CREATE;
    }

    /**
     * on initialization: sets the content, and the modal title (edit or create) and reset the form group
     */
    ngOnInit(): void {
        this.content = this.posting.content ?? '';
        this.updateModalTitle();
        this.resetFormGroup();
    }

    /**
     * on changes: sets the content, and the modal title (edit or create)
     */
    ngOnChanges(): void {
        this.content = this.posting.content ?? '';
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
            this.posting.content = this.formGroup.get('content')?.value;
            if (this.editType === EditType.UPDATE) {
                this.updatePosting();
            } else if (this.editType === EditType.CREATE) {
                this.createPosting();
            }
        }
    }

    open(): void {
        this.modalRef = this.modalService.open(this.postingEditor, { size: 'lg' });
    }

    abstract updateModalTitle(): void;

    abstract resetFormGroup(): void;

    abstract createPosting(): void;

    abstract updatePosting(): void;
}
