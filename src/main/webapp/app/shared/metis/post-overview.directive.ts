import { Directive, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Params } from '@angular/router';
import { CourseWideContext, PageType, PostContextFilter, PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
import { combineLatest, Subscription } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ButtonType } from 'app/shared/components/button.component';
import { HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';

@Directive()
export abstract class PostOverviewDirective implements OnInit, OnDestroy {
    entitiesPerPageTranslation = 'organizationManagement.userSearch.usersPerPage';
    showAllEntitiesTranslation = 'organizationManagement.userSearch.showAllUsers';

    readonly PageType = PageType;
    readonly ButtonType = ButtonType;

    course?: Course;
    exercises?: Exercise[];
    lectures?: Lecture[];
    currentPostContextFilter: PostContextFilter;
    currentSortCriterion = PostSortCriterion.CREATION_DATE;
    currentSortDirection = SortDirection.DESCENDING;
    searchText?: string;
    conversation?: Conversation;
    formGroup: FormGroup;
    createdPost: Post;
    posts: Post[];
    isLoading = true;
    totalItems = 0;
    pagingEnabled = true;
    itemsPerPage = ITEMS_PER_PAGE;
    page = 1;
    readonly CourseWideContext = CourseWideContext;
    readonly SortBy = PostSortCriterion;
    readonly SortDirection = SortDirection;
    readonly pageType = PageType.OVERVIEW;
    isCourseMessagesPage: boolean;

    private postsSubscription: Subscription;
    private paramSubscription: Subscription;
    private totalItemsSubscription: Subscription;

    // Icons
    faTimes = faTimes;
    faSearch = faSearch;

    constructor(
        protected metisService: MetisService,
        private activatedRoute: ActivatedRoute,
        private courseManagementService: CourseManagementService,
        private formBuilder: FormBuilder,
    ) {}

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
            const courseId = params.courseId;
            this.searchText = queryParams.searchText;
            this.page = 1;
            this.courseManagementService.findOneForDashboard(courseId).subscribe((res: HttpResponse<Course>) => {
                if (res.body !== undefined) {
                    this.course = res.body!;
                    this.fetchLecturesAndExercisesOfCourse();
                    this.metisService.setCourse(this.course!);
                    this.metisService.setPageType(this.pageType);
                    if (!(this.isCourseMessagesPage && !this.conversation)) {
                        this.metisService.getFilteredPosts({
                            courseId: this.course!.id,
                            conversationId: this.conversation?.id,
                            searchText: this.searchText ? this.searchText : undefined,
                            postSortCriterion: this.currentSortCriterion,
                            sortingOrder: this.currentSortDirection,
                            pagingEnabled: this.pagingEnabled,
                            page: this.page - 1,
                            pageSize: this.itemsPerPage,
                        });
                    } else {
                        // if user has no conversations to load on messages page
                        this.isLoading = false;
                    }
                    this.resetCurrentFilter();
                    this.createEmptyPost();
                    this.resetFormGroup();
                }
            });
        });
        this.postsSubscription = this.metisService.posts.subscribe((posts: Post[]) => {
            this.processReceivedPosts(posts);
            this.isLoading = false;
        });
        this.totalItemsSubscription = this.metisService.totalItems.subscribe((totalItems: number) => {
            this.totalItems = totalItems;
        });
    }

    /**
     * by default, the form group fields are set to show all posts in a course by descending creation date
     */
    resetFormGroup(): void {
        this.formGroup = this.formBuilder.group({
            context: [this.currentPostContextFilter],
            sortBy: [PostSortCriterion.CREATION_DATE],
            filterToUnresolved: false,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
        });
    }

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
        this.postsSubscription?.unsubscribe();
        this.totalItemsSubscription?.unsubscribe();
    }

    /**
     * fetches next page of posts on user scroll event
     */
    fetchNextPage() {
        if (!this.isLoading && this.posts.length < this.totalItems) {
            this.isLoading = true;
            this.page += 1;
            this.onSelectPage();
        }
    }

    /**
     *  metis service is invoked to deliver another page of posts, filtered and sorted on the backend
     */
    onSelectPage(): void {
        this.setFilterAndSort();
        this.metisService.getFilteredPosts(this.currentPostContextFilter, false);
    }

    /**
     * on changing any filter, the metis service is invoked to deliver the first page of posts for the
     * currently set context, filtered and sorted on the backend
     */
    onSelectContext(): void {
        this.page = 1;
        this.setFilterAndSort();
        this.metisService.getFilteredPosts(this.currentPostContextFilter);
    }

    /**
     * invoke metis service to create an empty default post that is needed on initialization of a modal to create a post,
     * this empty post has a default course-wide context as well as the course set as context
     **/
    createEmptyPost(): void {
        this.createdPost = this.metisService.createEmptyPostForContext(
            this.currentPostContextFilter.courseWideContext,
            this.exercises?.find((exercise) => exercise.id === this.currentPostContextFilter.exerciseId),
            this.lectures?.find((lecture) => lecture.id === this.currentPostContextFilter.lectureId),
            undefined,
            this.conversation,
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
    private setFilterAndSort(): void {
        this.currentPostContextFilter = {
            courseId: undefined,
            courseWideContext: undefined,
            exerciseId: undefined,
            lectureId: undefined,
            ...this.formGroup?.get('context')?.value,
            searchText: this.searchText ? this.searchText : undefined,
            conversationId: this.conversation?.id,
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
            conversationId: this.conversation?.id,
            courseWideContext: undefined,
            exerciseId: undefined,
            lectureId: undefined,
            searchText: undefined,
            filterToUnresolved: false,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            sortingOrder: SortDirection.DESCENDING,
        };
    }

    abstract fetchLecturesAndExercisesOfCourse(): void;

    abstract processReceivedPosts(posts: Post[]): void;
}
