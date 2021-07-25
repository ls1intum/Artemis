import { Component } from '@angular/core';
import { PostingsCreateEditModalDirective } from 'app/shared/metis/postings-create-edit-modal/postings-create-edit-modal.directive';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import * as moment from 'moment';
import { FormBuilder, Validators } from '@angular/forms';

const TITLE_MAX_LENGTH = 200;

@Component({
    selector: 'jhi-post-create-edit-modal',
    templateUrl: './post-create-edit-modal.component.html',
})
export class PostCreateEditModalComponent extends PostingsCreateEditModalDirective<Post> {
    constructor(protected metisService: MetisService, protected modalService: NgbModal, protected formBuilder: FormBuilder) {
        super(metisService, modalService, formBuilder);
    }

    resetFormGroup(): void {
        this.formGroup = this.formBuilder.group({
            title: [this.posting.title, [Validators.required, Validators.maxLength(TITLE_MAX_LENGTH)]],
            content: [this.posting.content, [Validators.required, Validators.maxLength(this.maxContentLength)]],
        });
    }

    createPosting(): void {
        this.posting.title = this.formGroup.get('title')?.value;
        this.posting.creationDate = moment();
        this.metisService.createPost(this.posting).subscribe({
            next: (post: Post) => {
                this.isLoading = false;
                this.modalRef?.close();
                this.onCreate.emit(post);
            },
            error: () => {
                this.isLoading = false;
            },
        });
    }

    updatePosting(): void {
        this.posting.title = this.formGroup.get('title')?.value;
        this.metisService.updatePost(this.posting).subscribe({
            next: () => {
                this.isLoading = false;
                this.modalRef?.close();
            },
            error: () => {
                this.isLoading = false;
            },
        });
    }

    updateModalTitle() {
        if (this.posting.id) {
            this.modalTitle = 'artemisApp.metis.editPosting';
        } else {
            this.modalTitle = 'artemisApp.metis.createModalTitlePost';
        }
    }
}
