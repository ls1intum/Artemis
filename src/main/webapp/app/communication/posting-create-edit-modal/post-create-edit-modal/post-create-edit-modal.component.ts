import { Component, OnInit, input, signal } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { PostingCreateEditModalDirective } from 'app/communication/posting-create-edit-modal/posting-create-edit-modal.directive';
import { Post } from 'app/communication/shared/entities/post.model';
import { FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { PageType, PostContentValidationPattern, PostTitleValidationPattern, PostingEditType } from 'app/communication/metis.util';
import { Conversation } from 'app/communication/shared/entities/conversation/conversation.model';
import { getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { deepClone } from 'app/shared/util/deep-clone.util';

const TITLE_MAX_LENGTH = 200;

export interface ContextSelectorOption {
    conversation?: Conversation;
}

type PostCreator = (post: Post) => Observable<Post>;

@Component({
    selector: 'jhi-post-create-edit-modal',
    templateUrl: './post-create-edit-modal.component.html',
    styleUrls: ['../../metis.component.scss'],
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, HelpIconComponent, PostingMarkdownEditorComponent, PostingButtonComponent, ArtemisTranslatePipe],
})
export class PostCreateEditModalComponent extends PostingCreateEditModalDirective<Post> implements OnInit {
    isCommunicationPage = input<boolean>(false);

    exercises?: Exercise[];
    lectures?: Lecture[];
    course: Course;
    pageType: PageType;
    isAtLeastTutorInCourse: boolean;
    isAtLeastInstructorInCourse: boolean;
    currentContextSelectorOption: ContextSelectorOption;
    similarPosts: Post[] = [];
    private contextSubscription?: Subscription;

    readonly PageType = PageType;
    readonly EditType = PostingEditType;
    protected readonly getAsChannel = getAsChannelDTO;

    // Icons
    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;
    createOverride = input<PostCreator | undefined>(undefined);

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

    isDialogVisible = signal(false);

    /**
     * opens the modal to edit or create a post
     */
    open(): void {
        this.isDialogVisible.set(true);
        this.isModalOpen.emit();
    }

    /**
     * closes the modal and resets the form
     */
    close(): void {
        this.resetFormGroup();
        this.isDialogVisible.set(false);
    }

    /**
     * resets the pageType, initialContext, post tags, post title, and post content
     */
    resetFormGroup(): void {
        this.pageType = this.metisService.getPageType();
        this.similarPosts = [];
        const posting = this.posting();
        if (posting) {
            posting.title = posting.title ?? '';
        }
        this.resetCurrentContextSelectorOption();
        this.contextSubscription?.unsubscribe();
        this.formGroup = this.formBuilder.group(this.postValidator());
        this.contextSubscription = this.formGroup.controls['context'].valueChanges.subscribe((context: ContextSelectorOption) => {
            this.currentContextSelectorOption = context;
            this.similarPosts = [];
        });
    }

    /**
     * invokes the metis service after setting the title and current date as creation date of the new answer post,
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
    createPosting(): void {
        this.isLoading = true;
        const posting = this.posting();
        if (!posting) {
            this.isLoading = false;
            return;
        }
        const payload = this.setPostProperties(deepClone(posting));

        const override = this.createOverride();
        const create$ = override ? override(payload) : this.metisService.createPost(payload);

        create$.subscribe({
            next: (post: Post) => {
                this.isLoading = false;
                this.resetFormGroup();
                this.isDialogVisible.set(false);
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
        const posting = this.posting();
        if (!posting) {
            this.isLoading = false;
            return;
        }
        const payload = this.setPostProperties(deepClone(posting));
        this.metisService.updatePost(payload).subscribe({
            next: () => {
                this.isLoading = false;
                this.resetFormGroup();
                this.isDialogVisible.set(false);
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
            this.modalTitle =
                'artemisApp.metis.' + (getAsChannelDTO(this.posting()?.conversation)?.isAnnouncementChannel ? 'createModalTitleAnnouncement' : 'createModalTitlePost');
        }
    }

    private setPostProperties(post: Post): Post {
        post.title = this.formGroup.get('title')?.value;
        post.content = this.formGroup.get('content')?.value;
        const contextValue = this.formGroup.get('context')?.value as ContextSelectorOption | undefined;
        if (contextValue?.conversation) {
            post.conversation = contextValue.conversation;
        }
        return post;
    }

    private resetCurrentContextSelectorOption(): void {
        this.currentContextSelectorOption = { conversation: this.posting()?.conversation };
    }

    private postValidator() {
        const posting = this.posting();
        return {
            // the pattern ensures that the title and content must include at least one non-whitespace character
            title: [posting?.title, [Validators.required, Validators.maxLength(TITLE_MAX_LENGTH), PostTitleValidationPattern]],
            content: [posting?.content, [Validators.required, Validators.maxLength(this.maxContentLength), PostContentValidationPattern]],
            context: [this.currentContextSelectorOption, [Validators.required]],
        };
    }
}
