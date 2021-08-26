import { Component, OnChanges, OnInit } from '@angular/core';
import { PostingsCreateEditModalDirective } from 'app/shared/metis/postings-create-edit-modal/postings-create-edit-modal.directive';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CourseWideContext, Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import * as moment from 'moment';
import { FormBuilder, Validators } from '@angular/forms';
import { Lecture } from 'app/entities/lecture.model';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Course } from 'app/entities/course.model';
import { LectureService } from 'app/lecture/lecture.service';
import { PageType } from 'app/shared/metis/metis.util';
import { AnswerPost } from 'app/entities/metis/answer-post.model';

const TITLE_MAX_LENGTH = 200;

interface ContextSelectorOption {
    lecture?: Lecture;
    exercise?: Exercise;
    courseWideContext?: CourseWideContext;
}

@Component({
    selector: 'jhi-post-create-edit-modal',
    templateUrl: './post-create-edit-modal.component.html',
})
export class PostCreateEditModalComponent extends PostingsCreateEditModalDirective<Post> implements OnInit, OnChanges {
    exercises?: Exercise[];
    lectures?: Lecture[];
    tags: string[];
    eCourseContext = CourseWideContext;
    course: Course;
    pageType: PageType;
    ePageType = PageType;
    isAtLeastTutorInCourse: boolean;
    currentContextSelectorOption: ContextSelectorOption;

    constructor(
        protected metisService: MetisService,
        protected modalService: NgbModal,
        protected formBuilder: FormBuilder,
        protected exerciseService: ExerciseService,
        protected lectureService: LectureService,
    ) {
        super(metisService, modalService, formBuilder);
    }

    ngOnInit() {
        super.ngOnInit();
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        this.course = this.metisService.getCourse();
        this.lectures = this.course.lectures;
        this.exercises = this.course.exercises;
        this.resetCurrentContextSelectorOption();
    }

    ngOnChanges() {
        super.ngOnChanges();
        this.resetCurrentContextSelectorOption();
    }

    /**
     * resets the pageType, initialContext, post tags, post title, and post content
     */
    resetFormGroup(): void {
        this.pageType = this.metisService.getPageType();
        this.tags = this.posting?.tags ?? [];
        this.formGroup = this.formBuilder.group({
            title: [this.posting.title, [Validators.required, Validators.maxLength(TITLE_MAX_LENGTH)]],
            content: [this.posting.content, [Validators.required, Validators.maxLength(this.maxContentLength)]],
            context: [this.currentContextSelectorOption, [Validators.required]],
        });
    }

    /**
     * invokes the metis service after setting the title and current date as creation date of the new answer post,
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
    createPosting(): void {
        this.posting.title = this.formGroup.get('title')?.value;
        this.setPostContextPropertyWithFormValue();
        this.posting.tags = this.tags;
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

    /**
     * invokes the metis service after setting the title of the updated post
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
    updatePosting(): void {
        this.posting.title = this.formGroup.get('title')?.value;
        this.posting.tags = this.tags;
        this.setPostContextPropertyWithFormValue();
        this.metisService.createPost(this.posting).subscribe({
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

    compareContextSelectorOptionFn(option1: ContextSelectorOption, option2: ContextSelectorOption) {
        if (option1.exercise && option2.exercise) {
            return option1.exercise.id === option2.exercise.id;
        } else if (option1.lecture && option2.lecture) {
            return option1.lecture.id === option2.lecture.id;
        } else if (option1.courseWideContext && option2.courseWideContext) {
            return option1.courseWideContext === option2.courseWideContext;
        }
        return false;
    }

    private setPostContextPropertyWithFormValue() {
        const currentContextSelectorOption: ContextSelectorOption = {
            exercise: undefined,
            lecture: undefined,
            courseWideContext: undefined,
            ...this.formGroup.get('context')?.value,
        };
        this.posting = {
            ...this.posting,
            ...currentContextSelectorOption,
        };
        if (currentContextSelectorOption.courseWideContext) {
            this.posting.course = { id: this.course.id };
        }
    }

    private resetCurrentContextSelectorOption() {
        this.currentContextSelectorOption = {
            lecture: this.posting.lecture,
            exercise: this.posting.exercise,
            courseWideContext: this.posting.courseWideContext,
        };
    }
}
