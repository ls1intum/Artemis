import { Directive, EventEmitter, Input, OnChanges, OnInit, Output, TemplateRef, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Posting } from 'app/entities/metis/posting.model';
import { MetisService } from 'app/shared/metis/metis.service';

const MAX_CONTENT_LENGTH = 1000;

@Directive()
export abstract class PostingsCreateEditModalDirective<T extends Posting> implements OnInit, OnChanges {
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

    ngOnInit() {
        this.content = this.posting.content ?? '';
        this.updateModalTitle();
        this.resetFormGroup();
    }

    ngOnChanges() {
        this.content = this.posting.content ?? '';
        this.updateModalTitle();
    }

    abstract updateModalTitle(): void;

    abstract resetFormGroup(): void;

    confirm() {
        this.isLoading = true;
        this.posting.content = this.content;
        if (this.posting!.id) {
            this.updatePosting();
        } else {
            this.createPosting();
        }
    }

    abstract createPosting(): void;

    abstract updatePosting(): void;

    public open() {
        this.modalRef = this.modalService.open(this.postingEditor, { size: 'lg' });
    }
}
