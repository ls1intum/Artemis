import { Directive, OnChanges, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { FormBuilder } from '@angular/forms';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Posting } from 'app/entities/metis/posting.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { PostingCreateEditDirective } from 'app/shared/metis/posting-create-edit.directive';

@Directive()
export abstract class PostingCreateEditModalDirective<T extends Posting> extends PostingCreateEditDirective<T> implements OnInit, OnChanges {
    @ViewChild('postingEditor') postingEditor: TemplateRef<any>;
    modalRef?: NgbModalRef;
    modalTitle: string;

    protected constructor(protected metisService: MetisService, protected modalService: NgbModal, protected formBuilder: FormBuilder) {
        super(metisService, modalService, formBuilder);
    }

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
    ngOnChanges(): void {
        super.ngOnChanges();
        this.updateModalTitle();
    }

    abstract open(): void;

    abstract updateModalTitle(): void;
}
