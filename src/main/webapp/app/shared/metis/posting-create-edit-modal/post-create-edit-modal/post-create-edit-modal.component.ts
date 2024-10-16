import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { PostingCreateEditModalDirective } from 'app/shared/metis/posting-create-edit-modal/posting-create-edit-modal.directive';
import { Post } from 'app/entities/metis/post.model';
import { Validators } from '@angular/forms';
import { Lecture } from 'app/entities/lecture.model';
import { Exercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { PageType, PostContentValidationPattern, PostTitleValidationPattern, PostingEditType } from 'app/shared/metis/metis.util';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';

const TITLE_MAX_LENGTH = 200;

export interface ContextSelectorOption {
    conversation?: Conversation;
}

@Component({
    selector: 'jhi-post-create-edit-modal',
    templateUrl: './post-create-edit-modal.component.html',
    styleUrls: ['../../metis.component.scss'],
})
export class PostCreateEditModalComponent extends PostingCreateEditModalDirective<Post> implements OnInit, OnChanges {
    @Input() isCommunicationPage: boolean;

    exercises?: Exercise[];
    lectures?: Lecture[];
    tags: string[];
    course: Course;
    pageType: PageType;
    isAtLeastTutorInCourse: boolean;
    isAtLeastInstructorInCourse: boolean;
    currentContextSelectorOption: ContextSelectorOption;
    similarPosts: Post[] = [];

    readonly PageType = PageType;
    readonly EditType = PostingEditType;
    protected readonly getAsChannel = getAsChannelDTO;

    // Icons
    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;

    /**
     * on initialization: reset all input field of the modal, determine the post context;
     * subscribe to the form control changes of the context selector in order to show the Announcement info box on selection;
     * authorize the user by invoking the metis service
     */
    ngOnInit(): void {
        this.resetCurrentContextSelectorOption();
        super.ngOnInit();
        this.course = this.metisService.getCourse();
        this.lectures = this.course.lectures;
        this.exercises = this.course.exercises;
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        this.isAtLeastInstructorInCourse = this.metisService.metisUserIsAtLeastInstructorInCourse();
    }

    /**
     * on initialization: reset all input field of the modal, determine the post context;
     */
    ngOnChanges(): void {
        super.ngOnChanges();
    }

    /**
     * opens the modal to edit or create a post
     */
    open(): void {
        this.modalRef = this.modalService.open(this.postingEditor, {
            size: 'lg',
            backdrop: 'static',
            beforeDismiss: () => {
                // when cancelling the create or update action, we do not want to store the current values
                // but rather reset the formGroup values so when re-opening the modal we do not show the previously unsaved changes
                this.resetFormGroup();
                return true;
            },
        });
    }

    /**
     * resets the pageType, initialContext, post tags, post title, and post content
     */
    resetFormGroup(): void {
        this.pageType = this.metisService.getPageType();
        this.tags = this.posting?.tags ?? [];
        this.similarPosts = [];
        this.posting.title = this.posting.title ?? '';
        this.resetCurrentContextSelectorOption();
        this.formGroup = this.formBuilder.group(this.postValidator());
        this.formGroup.controls['context'].valueChanges.subscribe((context: ContextSelectorOption) => {
            this.currentContextSelectorOption = context;
            this.similarPosts = [];
        });
    }

    /**
     * invokes the metis service after setting the title and current date as creation date of the new answer post,
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
    createPosting(): void {
        this.posting = this.setPostProperties(this.posting);
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

    /**
     * invokes the metis service after setting the title of the updated post
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
    updatePosting(): void {
        this.posting = this.setPostProperties(this.posting);
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

    /**
     * updates the title in accordance with the current use case (edit or create)
     */
    updateModalTitle(): void {
        if (this.editType === this.EditType.UPDATE) {
            this.modalTitle = 'artemisApp.metis.editPosting';
        } else if (this.editType === this.EditType.CREATE) {
            this.modalTitle = 'artemisApp.metis.' + (getAsChannelDTO(this.posting.conversation)?.isAnnouncementChannel ? 'createModalTitleAnnouncement' : 'createModalTitlePost');
        }
    }

    private setPostProperties(post: Post): Post {
        post.title = this.formGroup.get('title')?.value;
        post.tags = this.tags;
        post.content = this.formGroup.get('content')?.value;
        const currentContextSelectorOption: ContextSelectorOption = {
            ...this.formGroup.get('context')?.value,
        };
        post = {
            ...post,
            ...currentContextSelectorOption,
        };
        if (currentContextSelectorOption.conversation) {
            post.conversation = currentContextSelectorOption.conversation;
        }
        return post;
    }

    private resetCurrentContextSelectorOption(): void {
        this.currentContextSelectorOption = { conversation: this.posting.conversation };
    }

    private postValidator() {
        return {
            // the pattern ensures that the title and content must include at least one non-whitespace character
            title: [this.posting.title, [Validators.required, Validators.maxLength(TITLE_MAX_LENGTH), PostTitleValidationPattern]],
            content: [this.posting.content, [Validators.required, Validators.maxLength(this.maxContentLength), PostContentValidationPattern]],
            context: [this.currentContextSelectorOption, [Validators.required]],
        };
    }
}
