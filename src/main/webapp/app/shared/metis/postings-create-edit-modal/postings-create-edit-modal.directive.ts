import { Directive, EventEmitter, Input, OnChanges, OnInit, Output, TemplateRef, ViewChild } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Posting } from 'app/entities/metis/posting.model';
import { PostingsService } from 'app/shared/metis/postings.service';
import moment from 'moment';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';

const MAX_CONTENT_LENGTH = 1000;

@Directive()
export abstract class PostingsCreateEditModalDirective<T extends Posting> implements OnInit, OnChanges {
    @Input() posting: T;
    @Input() courseId: number;
    @Output() onUpdate: EventEmitter<T> = new EventEmitter<T>();
    @Output() onCreate: EventEmitter<T> = new EventEmitter<T>();
    @ViewChild('postingEditor') postingEditor: TemplateRef<any>;
    modalRef?: NgbModalRef;
    modalTitle: string;
    isLoading = false;
    maxContentLength = MAX_CONTENT_LENGTH;
    content: string;

    protected constructor(protected postingService: PostingsService<T>, protected modalService: NgbModal) {}

    ngOnInit() {
        this.content = this.posting.content ?? '';
        this.updateModalTitle();
    }

    ngOnChanges() {
        this.updateModalTitle();
    }

    private updateModalTitle() {
        if (this.posting.id) {
            this.modalTitle = 'artemisApp.metis.editPosting';
        } else {
            if (this.posting instanceof Post) {
                this.modalTitle = 'artemisApp.metis.createModalTitlePost';
            } else if (this.posting instanceof AnswerPost) {
                this.modalTitle = 'artemisApp.metis.createModalTitleAnswer';
            }
        }
    }

    confirm() {
        this.isLoading = true;
        this.posting.content = this.content;
        if (!!this.posting.id) {
            this.updatePosting();
        } else {
            this.createPosting();
        }
    }

    createPosting(): void {
        this.posting.creationDate = moment();
        this.postingService.create(this.courseId, this.posting).subscribe({
            next: (posting) => {
                this.onCreate.emit(posting.body as T);
            },
            error: () => {
                this.isLoading = false;
                this.close();
            },
            complete: () => {
                this.isLoading = false;
                this.close();
            },
        });
    }

    updatePosting(): void {
        this.postingService.update(this.courseId, this.posting).subscribe({
            next: (posting) => {
                this.onUpdate.emit(posting.body as T);
            },
            error: () => {
                this.isLoading = false;
                this.close();
            },
            complete: () => {
                this.isLoading = false;
                this.close();
            },
        });
    }

    close(): void {
        this.modalRef?.close();
        this.content = '';
    }

    public open() {
        this.modalRef = this.modalService.open(this.postingEditor, { size: 'lg' });
    }
}
