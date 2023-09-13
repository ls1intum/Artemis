import { AfterContentChecked, ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnInit, Output, ViewChild } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingDirective } from 'app/shared/metis/posting.directive';
import { MetisService } from 'app/shared/metis/metis.service';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ContextInformation, CourseWideContext, PageType } from '../metis.util';
import { faBullhorn, faCheckSquare } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { PostFooterComponent } from 'app/shared/metis/posting-footer/post-footer/post-footer.component';

@Component({
    selector: 'jhi-post',
    templateUrl: './post.component.html',
    styleUrls: ['./post.component.scss', './../metis.component.scss'],
})
export class PostComponent extends PostingDirective<Post> implements OnInit, OnChanges, AfterContentChecked {
    @Input() lastReadDate?: dayjs.Dayjs;
    @Input() readOnlyMode: boolean;
    @Input() previewMode: boolean;
    // if the post is previewed in the create/edit modal,
    // we need to pass the ref in order to close it when navigating to the previewed post via post title
    @Input() modalRef?: NgbModalRef;
    @Input() showAnswers: boolean;
    @Output() openThread = new EventEmitter<void>();
    @ViewChild('postFooter') postFooterComponent: PostFooterComponent;

    displayInlineInput = false;

    pageType: PageType;
    contextInformation: ContextInformation;
    readonly CourseWideContext = CourseWideContext;
    readonly PageType = PageType;

    // Icons
    faBullhorn = faBullhorn;
    faCheckSquare = faCheckSquare;

    constructor(
        public metisService: MetisService,
        protected changeDetector: ChangeDetectorRef,
    ) {
        super();
    }

    /**
     * on initialization: evaluates post context and page type
     */
    ngOnInit() {
        super.ngOnInit();
        this.pageType = this.metisService.getPageType();
        this.contextInformation = this.metisService.getContextInformation(this.posting);
    }

    /**
     * on changed: re-evaluates context information
     */
    ngOnChanges() {
        this.contextInformation = this.metisService.getContextInformation(this.posting);
    }

    /**
     * this lifecycle hook is required to avoid causing "Expression has changed after it was checked"-error when
     * dismissing the edit-create-modal -> we do not want to store changes in the create-edit-modal that are not saved
     */
    ngAfterContentChecked() {
        this.changeDetector.detectChanges();
    }

    /**
     * ensures that only when clicking on a post title without having cmd key pressed,
     * the modal is dismissed (closed and cleared)
     */
    onNavigateToPostById($event: MouseEvent) {
        if (!$event.metaKey) {
            this.modalRef?.dismiss();
        }
    }

    /**
     * ensures that only when clicking on context without having control key pressed,
     * the modal is dismissed (closed and cleared)
     */
    onNavigateToContext($event: MouseEvent) {
        if (!$event.metaKey) {
            this.modalRef?.dismiss();
        }
    }

    /**
     * Open create answer modal
     */
    openCreateAnswerPostModal() {
        this.postFooterComponent.openCreateAnswerPostModal();
    }
}
