import { Component, Input, OnChanges, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingHeaderDirective } from 'app/shared/metis/posting-header/posting-header.directive';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { faCheckSquare, faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-post-header',
    templateUrl: './post-header.component.html',
    styleUrls: ['../../metis.component.scss'],
    standalone: true,
    imports: [ProfilePictureComponent, NgClass, FaIconComponent, NgbTooltip, TranslateDirective, ArtemisSharedCommonModule],
})
export class PostHeaderComponent extends PostingHeaderDirective<Post> implements OnInit, OnDestroy, OnChanges {
    @Input()
    readOnlyMode = false;
    @Input() lastReadDate?: dayjs.Dayjs;
    @Input() previewMode: boolean;
    @ViewChild(PostCreateEditModalComponent) postCreateEditModal?: PostCreateEditModalComponent;
    isAtLeastInstructorInCourse: boolean;

    // Icons
    readonly faPencilAlt = faPencilAlt;
    readonly faCheckSquare = faCheckSquare;

    ngOnInit() {
        super.ngOnInit();
    }

    /**
     * on changes: re-evaluates authority roles
     */
    ngOnChanges() {
        this.setUserProperties();
        this.setUserAuthorityIconAndTooltip();
    }

    /**
     * on leaving the page, the modal should be closed
     */
    ngOnDestroy(): void {
        this.postCreateEditModal?.modalRef?.close();
    }

    protected readonly CachingStrategy = CachingStrategy;
}
