import { Component, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingsHeaderDirective } from 'app/shared/metis/postings-header/postings-header.directive';
import { MetisService } from 'app/shared/metis/metis.service';
import { PostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { CourseWideContext } from '../../metis.util';

@Component({
    selector: 'jhi-post-header',
    templateUrl: './post-header.component.html',
    styleUrls: ['../../metis.component.scss'],
})
export class PostHeaderComponent extends PostingsHeaderDirective<Post> implements OnInit, OnDestroy {
    @Input() previewMode: boolean;
    @ViewChild(PostCreateEditModalComponent) postCreateEditModal?: PostCreateEditModalComponent;
    isAtLeastInstructorInCourse: boolean;
    readonly CourseWideContext = CourseWideContext;

    constructor(protected metisService: MetisService) {
        super(metisService);
    }

    ngOnInit() {
        super.ngOnInit();
        this.isAtLeastInstructorInCourse = this.metisService.metisUserIsAtLeastInstructorInCourse();
    }

    /**
     * on leaving the page, the modal should be closed
     */
    ngOnDestroy(): void {
        this.postCreateEditModal?.modalRef?.close();
    }

    /**
     * invokes the metis service to delete a post
     */
    deletePosting(): void {
        this.metisService.deletePost(this.posting);
    }
}
