import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Params } from '@angular/router';
import { CourseWideContext, PageType, PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
import { Subscription, combineLatest } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { FormBuilder } from '@angular/forms';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { CourseDiscussionDirective } from 'app/shared/metis/course-discussion.directive';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { CourseStorageService } from 'app/course/manage/course-storage.service';

@Component({
    selector: 'jhi-course-discussion',
    templateUrl: './course-discussion.component.html',
    styleUrls: ['./course-discussion.component.scss'],
    providers: [MetisService],
})
export class CourseDiscussionComponent extends CourseDiscussionDirective implements OnInit, OnDestroy {
    entitiesPerPageTranslation = 'artemisApp.organizationManagement.userSearch.usersPerPage';
    showAllEntitiesTranslation = 'artemisApp.organizationManagement.userSearch.showAllUsers';

    exercises?: Exercise[];
    lectures?: Lecture[];
    currentSortDirection = SortDirection.DESCENDING;
    totalItems = 0;
    pagingEnabled = true;
    itemsPerPage = ITEMS_PER_PAGE;
    page = 1;

    documentationType = DocumentationType.Communications;

    forceReload = true;
    readonly CourseWideContext = CourseWideContext;
    readonly PageType = PageType;
    readonly pageType = PageType.OVERVIEW;

    private totalItemsSubscription: Subscription;

    constructor(
        protected metisService: MetisService,
        private activatedRoute: ActivatedRoute,
        private courseManagementService: CourseManagementService,
        private formBuilder: FormBuilder,
        private courseStorageService: CourseStorageService,
    ) {
        super(metisService);
    }

    /**
     * on initialization: initializes the metis service, fetches the posts for the course, resets all user inputs and selects the defaults,
     * creates the subscription to posts to stay updated on any changes of posts in this course
     */
    ngOnInit(): void {
        this.paramSubscription = combineLatest({
            params: this.activatedRoute.parent!.parent!.params,
            queryParams: this.activatedRoute.parent!.parent!.queryParams,
        }).subscribe((routeParams: { params: Params; queryParams: Params }) => {
            const { params, queryParams } = routeParams;
            const courseId = +params.courseId;
            this.searchText = queryParams.searchText;

            this.course = this.courseStorageService.getCourse(courseId);
            if (this.course) {
                this.onCourseLoad(this.course);
            }

            this.courseStorageService.subscribeToCourseUpdates(courseId).subscribe((course: Course) => {
                this.onCourseLoad(course);
            });
        });
        this.postsSubscription = this.metisService.posts.pipe().subscribe((posts: Post[]) => {
            this.posts = posts;
            this.isLoading = false;
        });
        this.totalItemsSubscription = this.metisService.totalNumberOfPosts.pipe().subscribe((totalItems: number) => {
            this.totalItems = totalItems;
        });
    }

    onCourseLoad(course: Course) {
        this.course = course;
        if (this.course?.lectures) {
            this.lectures = this.course.lectures.sort(this.overviewContextSortFn);
        }
        if (this.course?.exercises) {
            this.exercises = this.course.exercises.sort(this.overviewContextSortFn);
        }
        this.metisService.setCourse(this.course!);
        this.metisService.setPageType(this.pageType);
        this.metisService.getFilteredPosts({
            courseId: this.course!.id,
            searchText: this.searchText ? this.searchText : undefined,
            postSortCriterion: this.currentSortCriterion,
            sortingOrder: this.currentSortDirection,
            pagingEnabled: this.pagingEnabled,
            page: this.page - 1,
            pageSize: this.itemsPerPage,
        });
        this.resetCurrentFilter();
        this.createEmptyPost();
        this.resetFormGroup();
    }

    /**
     * by default, the form group fields are set to show all posts in a course by descending creation date
     */
    resetFormGroup(): void {
        this.formGroup = this.formBuilder.group({
            context: [[]],
            sortBy: [PostSortCriterion.CREATION_DATE],
            filterToUnresolved: false,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
        });
    }

    ngOnDestroy(): void {
        super.onDestroy();
        this.totalItemsSubscription?.unsubscribe();
    }

    /**
     *  metis service is invoked to deliver another page of posts, filtered and sorted on the backend
     */
    private onSelectPage(): void {
        this.setFilterAndSort();
        this.metisService.getFilteredPosts(this.currentPostContextFilter, false);
    }

    /**
     * on changing any filter, the metis service is invoked to deliver the first page of posts for the
     * currently set context, filtered and sorted on the server
     */
    onSelectContext(): void {
        this.page = 1;
        // will scroll to the top of the posts
        this.forceReload = true;

        const lectureIds: number[] = [];
        const exerciseIds: number[] = [];
        const courseWideContexts: CourseWideContext[] = [];

        for (const context of this.formGroup.get('context')?.value || []) {
            if (context.lectureId) {
                lectureIds.push(context.lectureId);
            } else if (context.exerciseId) {
                exerciseIds.push(context.exerciseId);
            } else if (context.courseWideContext) {
                courseWideContexts.push(context.courseWideContext);
            }
        }

        this.currentPostContextFilter.lectureIds = lectureIds.length ? lectureIds : undefined;
        this.currentPostContextFilter.exerciseIds = exerciseIds.length ? exerciseIds : undefined;
        this.currentPostContextFilter.courseWideContexts = courseWideContexts.length ? courseWideContexts : undefined;

        super.onSelectContext();
    }

    /**
     * on changing the sort direction via icon, the metis service is invoked to deliver the posts for the currently set context,
     * sorted on the backend
     */
    onChangeSortDir(): void {
        // flip sort direction
        this.currentSortDirection = this.currentSortDirection === SortDirection.DESCENDING ? SortDirection.ASCENDING : SortDirection.DESCENDING;
        this.onSelectContext();
    }

    /**
     * required for distinguishing different select options for the context selector,
     * Angular needs to be able to identify the currently selected option
     */
    compareContextFilterOptionFn(option1: any, option2: any) {
        if (option1.exerciseId && option2.exerciseId) {
            return option1.exerciseId === option2.exerciseId;
        } else if (option1.lectureId && option2.lectureId) {
            return option1.lectureId === option2.lectureId;
        } else if (option1.courseWideContext && option2.courseWideContext) {
            return option1.courseWideContext === option2.courseWideContext;
        } else if (option1.courseId && option2.courseId) {
            return option1.courseId === option2.courseId;
        }
        return false;
    }

    /**
     * required for distinguishing different select options for the sort selector (sortBy, and sortDirection),
     * Angular needs to be able to identify the currently selected option
     */
    comparePostSortOptionFn(option1: PostSortCriterion | SortDirection, option2: PostSortCriterion | SortDirection) {
        return option1 === option2;
    }

    /**
     * sort context (lecture, exercise) by title
     **/
    private overviewContextSortFn = (contextA: Lecture | Exercise, contextB: Lecture | Exercise): number => {
        const titleA = contextA.title!.toUpperCase(); // ignore capitalization
        const titleB = contextB.title!.toUpperCase(); // ignore capitalization
        if (titleA < titleB) {
            return -1;
        }
        if (titleA > titleB) {
            return 1;
        }
        return 0;
    };

    /**
     * invoke metis service to create an empty default post that is needed on initialization of a modal to create a post,
     * this empty post has a default course-wide context as well as the course set as context
     **/
    createEmptyPost(): void {
        this.createdPost = this.metisService.createEmptyPostForContext(
            this.currentPostContextFilter.courseWideContexts?.[0],
            this.exercises?.find((exercise) => exercise.id === this.currentPostContextFilter.exerciseIds?.[0]),
            this.lectures?.find((lecture) => lecture.id === this.currentPostContextFilter.lectureIds?.[0]),
        );
    }
    /**
     * defines a function that returns the post id as unique identifier,
     * by this means, Angular determines which post in the collection of posts has to be reloaded/destroyed on changes
     */
    postsTrackByFn = (index: number, post: Post): number => post.id!;

    /**
     * sets the filter and sort options after receiving user input
     */
    setFilterAndSort(): void {
        this.currentPostContextFilter = {
            ...this.currentPostContextFilter,
            courseId: this.course?.id,
            searchText: this.searchText,
            pagingEnabled: this.pagingEnabled,
            page: this.page - 1,
            pageSize: this.itemsPerPage,
            filterToUnresolved: this.formGroup.get('filterToUnresolved')?.value,
            filterToOwn: this.formGroup.get('filterToOwn')?.value,
            filterToAnsweredOrReacted: this.formGroup.get('filterToAnsweredOrReacted')?.value,
            postSortCriterion: this.formGroup.get('sortBy')?.value,
            sortingOrder: this.currentSortDirection,
        };
    }

    /**
     * sets the current filter for context (default: course) and content (default: undefined)
     */
    private resetCurrentFilter(): void {
        this.currentPostContextFilter = {
            courseId: this.course!.id,
            courseWideContexts: undefined,
            exerciseIds: undefined,
            lectureIds: undefined,
            searchText: undefined,
            filterToUnresolved: false,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            sortingOrder: SortDirection.DESCENDING,
        };
    }

    /**
     * fetches next page of posts when user scrolls to the end of posts
     */
    fetchNextPage() {
        if (!this.isLoading && this.posts.length < this.totalItems) {
            this.isLoading = true;
            this.page += 1;
            this.onSelectPage();
        }
    }
}
