import { Component, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PageType, PostFilter, PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
import { Subscription } from 'rxjs';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { CourseWideContext } from 'app/entities/metis/post.model';
import { FormBuilder, FormGroup } from '@angular/forms';

interface ContextFilterOption {
    course?: Course;
    lecture?: Lecture;
    exercise?: Exercise;
    courseWideContext?: CourseWideContext;
}

@Component({
    selector: 'jhi-course-discussion',
    templateUrl: './course-discussion.component.html',
    styleUrls: ['./course-discussion.scss'],
    providers: [MetisService],
})
export class CourseDiscussionComponent implements OnDestroy {
    readonly pageType = PageType.OVERVIEW;

    course?: Course;
    exercises?: Exercise[];
    lectures?: Lecture[];
    currentPostContextFilter: ContextFilterOption;
    postFilter: PostFilter;
    eCourseContext = CourseWideContext;
    eSortBy = PostSortCriterion;
    eSortDirection = SortDirection;
    formGroup: FormGroup;

    private paramSubscription: Subscription;

    constructor(
        private metisService: MetisService,
        private activatedRoute: ActivatedRoute,
        private courseCalculationService: CourseScoreCalculationService,
        private formBuilder: FormBuilder,
    ) {
        this.paramSubscription = this.activatedRoute.parent!.params.subscribe((params) => {
            const courseId = parseInt(params['courseId'], 10);
            this.course = this.courseCalculationService.getCourse(courseId);
            if (this.course) {
                this.initMetisService();
                this.lectures = this.course?.lectures;
                this.exercises = this.course?.exercises;
                this.resetCurrentPostFilter();
                this.resetFormGroup();
                this.setPostOverviewControlsWithFormValue();
                this.metisService.getPostsForFilter(this.postFilter);
            }
        });
    }

    private initMetisService(): void {
        this.metisService.setCourse(this.course!);
        this.metisService.setPageType(this.pageType);
    }

    resetFormGroup(): void {
        this.formGroup = this.formBuilder.group({
            context: [this.currentPostContextFilter],
            sortBy: [{ sortBy: PostSortCriterion.CREATION_DATE.valueOf() }],
            sortDirection: [{ sortDirection: SortDirection.DESC.valueOf() }],
        });
    }

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
    }

    public onSelect(event: any) {
        this.setPostOverviewControlsWithFormValue();
        this.metisService.getPostsForFilter(this.postFilter);
    }

    compareContextFilterOptionFn(option1: ContextFilterOption, option2: ContextFilterOption) {
        if (option1.exercise && option2.exercise) {
            return option1.exercise.id === option2.exercise.id;
        } else if (option1.lecture && option2.lecture) {
            return option1.lecture.id === option2.lecture.id;
        } else if (option1.courseWideContext && option2.courseWideContext) {
            return option1.courseWideContext === option2.courseWideContext;
        } else if (option1.course && option2.course) {
            return option1.course.id === option2.course.id;
        }
        return false;
    }

    comparePostSortByOptionFn(option1: { sortBy: PostSortCriterion }, option2: { sortBy: PostSortCriterion }) {
        return option1.sortBy === option2.sortBy;
    }

    compareSortDirectionOptionFn(option1: { sortDirection: SortDirection }, option2: { sortDirection: SortDirection }) {
        return option1.sortDirection === option2.sortDirection;
    }

    private setPostOverviewControlsWithFormValue() {
        this.postFilter = {
            course: undefined,
            exercise: undefined,
            lecture: undefined,
            courseWideContext: undefined,
            sortBy: undefined,
            sortDirection: undefined,
            ...this.formGroup.get('context')?.value,
            ...this.formGroup.get('sortBy')?.value,
            ...this.formGroup.get('sortDirection')?.value,
        };
    }

    private resetCurrentPostFilter(): void {
        this.currentPostContextFilter = {
            course: this.course,
            lecture: undefined,
            exercise: undefined,
            courseWideContext: undefined,
        };
    }
}
