import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { PostingCreateEditModalDirective } from 'app/shared/metis/posting-create-edit-modal/posting-create-edit-modal.directive';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { FormBuilder, Validators } from '@angular/forms';
import { Lecture } from 'app/entities/lecture.model';
import { Exercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { Router } from '@angular/router';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { CourseWideContext, PageType, PostContentValidationPattern, PostTitleValidationPattern, PostingEditType } from 'app/shared/metis/metis.util';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';

const TITLE_MAX_LENGTH = 200;
const DEBOUNCE_TIME_BEFORE_SIMILARITY_CHECK = 800;

export interface ContextSelectorOption {
    lecture?: Lecture;
    exercise?: Exercise;
    courseWideContext?: CourseWideContext;
    conversation?: Conversation;
}

@Component({
    selector: 'jhi-post-create-edit-modal',
    templateUrl: './post-create-edit-modal.component.html',
    styleUrls: ['../../metis.component.scss'],
})
export class PostCreateEditModalComponent extends PostingCreateEditModalDirective<Post> implements OnInit, OnChanges {
    @Input() isCourseMessagesPage: boolean;

    modalRef?: NgbModalRef;
    exercises?: Exercise[];
    lectures?: Lecture[];
    tags: string[];
    course: Course;
    pageType: PageType;
    isAtLeastTutorInCourse: boolean;
    isAtLeastInstructorInCourse: boolean;
    currentContextSelectorOption: ContextSelectorOption;
    similarPosts: Post[] = [];

    readonly CourseWideContext = CourseWideContext;
    readonly PageType = PageType;
    readonly EditType = PostingEditType;

    // Icons
    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;

    constructor(protected metisService: MetisService, protected modalService: NgbModal, protected formBuilder: FormBuilder, private router: Router) {
        super(metisService, modalService, formBuilder);
    }

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
        this.formGroup = this.formBuilder.group(!this.isCourseMessagesPage ? this.postValidator() : this.messageValidator());
        this.formGroup.controls['context'].valueChanges.subscribe((context: ContextSelectorOption) => {
            this.currentContextSelectorOption = context;
            // announcements should not show similar posts
            if (this.currentContextSelectorOption.courseWideContext === CourseWideContext.ANNOUNCEMENT) {
                this.similarPosts = [];
            }
        });
        // we only want to search for similar posts (and show the result of the duplication check) if a post is created, not on updates
        if (this.editType === this.EditType.CREATE) {
            this.triggerPostSimilarityCheck();
        }
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
     * invokes the metis service to get similar posts on changes of the formGroup, i.e. title or content
     */
    triggerPostSimilarityCheck(): void {
        this.formGroup
            .get('title')
            ?.valueChanges.pipe(debounceTime(DEBOUNCE_TIME_BEFORE_SIMILARITY_CHECK), distinctUntilChanged())
            .subscribe((title: string) => {
                let tempPost = new Post();
                tempPost = this.setPostProperties(tempPost);
                // determine if title is provided
                if (title.length > 0 && this.currentContextSelectorOption.courseWideContext !== CourseWideContext.ANNOUNCEMENT) {
                    // if title input field is not empty, or context other than announcement invoke metis service to get similar posts
                    this.metisService.getSimilarPosts(tempPost).subscribe((similarPosts: Post[]) => {
                        this.similarPosts = similarPosts;
                    });
                } else {
                    // if title input field is empty, set similar posts to empty array to not show the list
                    this.similarPosts = [];
                }
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
            this.modalTitle = 'artemisApp.metis.createModalTitlePost';
        }
    }

    /**
     * required for distinguishing different select options for the context selector,
     * Angular needs to be able to identify the currently selected option
     */
    compareContextSelectorOptionFn(option1: ContextSelectorOption, option2: ContextSelectorOption): boolean {
        if (option1.exercise && option2.exercise) {
            return option1.exercise.id === option2.exercise.id;
        } else if (option1.lecture && option2.lecture) {
            return option1.lecture.id === option2.lecture.id;
        } else if (option1.courseWideContext && option2.courseWideContext) {
            return option1.courseWideContext === option2.courseWideContext;
        }
        return false;
    }

    private setPostProperties(post: Post): Post {
        post.title = this.formGroup.get('title')?.value;
        post.tags = this.tags;
        post.content = this.formGroup.get('content')?.value;
        const currentContextSelectorOption: ContextSelectorOption = {
            exercise: undefined,
            lecture: undefined,
            courseWideContext: undefined,
            ...this.formGroup.get('context')?.value,
        };
        post = {
            ...post,
            ...currentContextSelectorOption,
        };
        if (currentContextSelectorOption.courseWideContext) {
            post.course = { id: this.course.id, title: this.course.title };
        }
        if (currentContextSelectorOption.conversation) {
            post.conversation = currentContextSelectorOption.conversation;
        }
        return post;
    }

    private resetCurrentContextSelectorOption(): void {
        this.currentContextSelectorOption = {
            lecture: this.posting.lecture,
            exercise: this.posting.exercise,
            courseWideContext: this.posting.courseWideContext,
        };
    }

    private postValidator() {
        return {
            // the pattern ensures that the title and content must include at least one non-whitespace character
            title: [this.posting.title, [Validators.required, Validators.maxLength(TITLE_MAX_LENGTH), PostTitleValidationPattern]],
            content: [this.posting.content, [Validators.required, Validators.maxLength(this.maxContentLength), PostContentValidationPattern]],
            context: [this.currentContextSelectorOption, [Validators.required]],
        };
    }

    private messageValidator() {
        return {
            // the pattern ensures that content must include at least one non-whitespace character
            content: [this.posting.content, [Validators.required, Validators.maxLength(this.maxContentLength), PostContentValidationPattern]],
            context: [this.currentContextSelectorOption, [Validators.required]],
        };
    }
}
