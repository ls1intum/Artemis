import { Component, OnDestroy } from '@angular/core';
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
import { Subscription } from 'rxjs';

const TITLE_MAX_LENGTH = 200;

@Component({
    selector: 'jhi-post-create-edit-modal',
    templateUrl: './post-create-edit-modal.component.html',
})
export class PostCreateEditModalComponent extends PostingsCreateEditModalDirective<Post> {
    exercises?: Exercise[];
    lectures?: Lecture[];
    tags: string[];
    eCourseContext = CourseWideContext;
    course: Course;
    pageType: PageType;
    ePageType = PageType;
    isAtLeastTutorInCourse: boolean;
    initialContext: Lecture | Exercise | CourseWideContext | string;

    constructor(
        protected metisService: MetisService,
        protected modalService: NgbModal,
        protected formBuilder: FormBuilder,
        protected exerciseService: ExerciseService,
        protected lectureService: LectureService,
    ) {
        super(metisService, modalService, formBuilder);
        this.tags = this.posting?.tags ?? [];
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        this.course = this.metisService.getCourse();
        this.lectures = this.course.lectures;
        this.exercises = this.course.exercises;
    }

    /**
     * resets the post title and post content
     */
    resetFormGroup(): void {
        this.pageType = this.metisService.getPageType();
        this.initialContext = this.setInitialContext();
        this.tags = this.posting.tags ?? [];
        this.formGroup = this.formBuilder.group({
            title: [this.posting.title, [Validators.required, Validators.maxLength(TITLE_MAX_LENGTH)]],
            content: [this.posting.content, [Validators.required, Validators.maxLength(this.maxContentLength)]],
            context: [this.initialContext, [Validators.required]],
        });
    }

    /**
     * invokes the metis service after setting the title and current date as creation date of the new answer post,
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
    createPosting(): void {
        this.posting.title = this.formGroup.get('title')?.value;
        if (this.lectures?.includes(this.formGroup.get('context')?.value)) {
            this.posting.lecture = this.formGroup.get('context')?.value;
        } else if (this.exercises?.includes(this.formGroup.get('context')?.value)) {
            this.posting.exercise = this.formGroup.get('context')?.value;
        } else {
            this.posting.courseWideContext = this.formGroup.get('context')?.value;
        }
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

    setInitialContext(): string | Lecture | Exercise | CourseWideContext {
        if (this.posting.exercise) {
            return this.posting.exercise;
        } else if (this.posting.lecture) {
            return this.posting.lecture;
        } else if (this.posting.courseWideContext) {
            return this.posting.courseWideContext;
        } else {
            return '';
        }
    }
}
