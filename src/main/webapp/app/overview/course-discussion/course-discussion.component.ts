import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { CourseWideContext, DisplayPriority, PageType, PostSortCriterion, SortDirection, VOTE_EMOJI_ID } from 'app/shared/metis/metis.util';
import { combineLatest, map, Subscription } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Reaction } from 'app/entities/metis/reaction.model';
import { ButtonType } from 'app/shared/components/button.component';
import { HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { faFilter, faLongArrowAltDown, faLongArrowAltUp, faPlus, faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';

interface ContextFilterOption {
    courseId?: number;
    lectureId?: number;
    exerciseId?: number;
    courseWideContext?: CourseWideContext;
}

interface ContentFilterOption {
    searchText?: string;
}

@Component({
    selector: 'jhi-course-discussion',
    templateUrl: './course-discussion.component.html',
    styleUrls: ['./course-discussion.component.scss'],
    providers: [MetisService],
})
export class CourseDiscussionComponent implements OnInit, OnDestroy {
    course?: Course;
    exercises?: Exercise[];
    lectures?: Lecture[];
    currentPostContextFilter: ContextFilterOption;
    currentSortCriterion = PostSortCriterion.CREATION_DATE;
    currentSortDirection = SortDirection.DESC;
    currentPostContentFilter: ContentFilterOption;
    searchText?: string;
    filterToUnresolved = false;
    filterToOwn = false;
    filterToAnsweredOrReactedByUser = false;
    formGroup: FormGroup;
    createdPost: Post;
    posts: Post[];
    isLoading = true;
    readonly CourseWideContext = CourseWideContext;
    readonly SortBy = PostSortCriterion;
    readonly SortDirection = SortDirection;
    readonly PageType = PageType;
    readonly ButtonType = ButtonType;
    readonly pageType = PageType.OVERVIEW;

    private postsSubscription: Subscription;
    private paramSubscription: Subscription;

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faFilter = faFilter;
    faSearch = faSearch;
    faLongArrowAltUp = faLongArrowAltUp;
    faLongArrowAltDown = faLongArrowAltDown;

    constructor(
        protected metisService: MetisService,
        private activatedRoute: ActivatedRoute,
        private courseManagementService: CourseManagementService,
        private formBuilder: FormBuilder,
        private router: Router,
    ) {}

    /**
     * on initialization: initializes the metis service, fetches the posts for the course, resets all user inputs and selects the defaults,
     * creates the subscription to posts to stay updated on any changes of posts in this course
     */
    ngOnInit(): void {
        this.paramSubscription = combineLatest(
            this.activatedRoute.parent!.parent!.params,
            this.activatedRoute.parent!.parent!.queryParams,
            (params: Params, queryParams: Params) => ({
                params,
                queryParams,
            }),
        ).subscribe((routeParams: { params: Params; queryParams: Params }) => {
            const { params, queryParams } = routeParams;
            const courseId = params.courseId;
            this.searchText = queryParams.searchText;
            this.courseManagementService.findOneForDashboard(courseId).subscribe((res: HttpResponse<Course>) => {
                if (res.body !== undefined) {
                    this.course = res.body!;
                    if (this.course?.lectures) {
                        this.lectures = this.course.lectures.sort(this.overviewContextSortFn);
                    }
                    if (this.course?.exercises) {
                        this.exercises = this.course.exercises.sort(this.overviewContextSortFn);
                    }
                    this.metisService.setCourse(this.course!);
                    this.metisService.setPageType(this.pageType);
                    this.metisService.getFilteredPosts({ courseId: this.course!.id });
                    this.resetCurrentFilter();
                    this.createEmptyPost();
                    this.resetFormGroup();
                    if (this.searchText) {
                        this.onSearch(true);
                    }
                }
            });
        });
        this.postsSubscription = this.metisService.posts.pipe(map((posts: Post[]) => posts.filter(this.filterFn).sort(this.overviewSortFn))).subscribe((posts: Post[]) => {
            this.posts = posts;
            this.isLoading = false;
        });
    }

    /**
     * by default, the form group fields are set to show all posts in a course by descending creation date
     */
    resetFormGroup(): void {
        this.formGroup = this.formBuilder.group({
            context: [this.currentPostContextFilter],
            sortBy: [PostSortCriterion.CREATION_DATE],
            filterToUnresolved: [this.filterToUnresolved],
            filterToOwn: [this.filterToOwn],
            filterToAnsweredOrReacted: [this.filterToAnsweredOrReactedByUser],
        });
    }

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
        this.postsSubscription?.unsubscribe();
    }

    /**
     * on changing the context via dropdown, the metis service is invoked to deliver the posts for the currently set context,
     * they are sorted on return
     */
    onSelectContext(): void {
        this.setFilterAndSort();
        this.metisService.getFilteredPosts(this.currentPostContextFilter);
    }

    /**
     * on changing the sort options via dropdown, the metis service is invoked to deliver the posts for the currently set context,
     * as the posts themselves will not change, the forceReload flag is set to false, they are sorted on return
     */
    onChangeSortBy(): void {
        this.setFilterAndSort();
        this.metisService.getFilteredPosts(this.currentPostContextFilter, false);
    }

    /**
     * on changing the sort direction via icon, the metis service is invoked to deliver the posts for the currently set context,
     * as the posts themselves will not change, the forceReload flag is set to false, they are sorted on return
     */
    onChangeSortDir(): void {
        // flip sort direction
        this.currentSortDirection = this.currentSortDirection === SortDirection.DESC ? SortDirection.ASC : SortDirection.DESC;
        this.metisService.getFilteredPosts(this.currentPostContextFilter, false);
    }

    /**
     * on changing the filter (to unresolved | own | answered or reacted posts only), the currently loaded posts are filtered accordingly
     */
    onFilterChange(): void {
        this.setFilterAndSort();
        this.metisService.getFilteredPosts(this.currentPostContextFilter, false);
    }

    /**
     * on changing the search text via input, the metis service is invoked to deliver the posts for the currently set context,
     * the sort will be done on the currently visible posts, so the forceReload flag is set to false
     *
     * @param forceUpdate passed to metisService method call; if true, forces a re-fetch even if filter property did not change
     */
    onSearch(forceUpdate = false): void {
        this.currentPostContentFilter.searchText = this.searchText;
        this.router.navigate([], {
            queryParams: {
                searchText: this.searchText,
            },
            queryParamsHandling: 'merge',
        });
        this.metisService.getFilteredPosts(this.currentPostContextFilter, forceUpdate);
    }

    /**
     * required for distinguishing different select options for the context selector,
     * Angular needs to be able to identify the currently selected option
     */
    compareContextFilterOptionFn(option1: ContextFilterOption, option2: ContextFilterOption) {
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
     * filters posts on several post characteristics criteria, their context and a search string in a match-all-manner
     * - filterToUnresolved: post is only kept if none of the given answers is marked as resolving
     * - filterToOwn: post is only kept if the author of the post matches the currently logged in user
     * - filterToAnsweredOrReactedByUser: post is only kept if the author of any given answer the user that put any reaction on that post matches the currently logged in user
     * - currentPostContentFilter: post is only kept if the search string (which is not a #id pattern) is included in either the post title, content or tag (all strings lowercased)
     * @return boolean predicate if the post is kept (true) or filtered out (false)
     */
    filterFn = (post: Post): boolean => {
        let keepPost = true;
        if (this.filterToUnresolved) {
            // announcement should never be regarded as unresolved posts as they do not address any problem to be solved
            keepPost = keepPost && (!this.metisService.isPostResolved(post) || post.courseWideContext === CourseWideContext.ANNOUNCEMENT);
        }
        if (this.filterToOwn) {
            keepPost = keepPost && this.metisService.metisUserIsAuthorOfPosting(post);
        }
        if (this.filterToAnsweredOrReactedByUser) {
            const hasAnsweredOrReacted =
                (post.answers?.some((answer: AnswerPost) => this.metisService.metisUserIsAuthorOfPosting(answer)) ||
                    post.reactions?.some((reaction: Reaction) => reaction.user?.id === this.metisService.getUser().id)) ??
                false;
            keepPost = keepPost && hasAnsweredOrReacted;
        }
        if (this.currentPostContentFilter.searchText && this.currentPostContentFilter.searchText.trim().length > 0) {
            // check if the search text is either contained in the title or in the content
            const lowerCasedSearchString = this.currentPostContentFilter.searchText.toLowerCase();
            // if searchText starts with a # and is followed by a post id, filter for post with id
            if (lowerCasedSearchString.startsWith('#') && !isNaN(+lowerCasedSearchString.substring(1))) {
                return keepPost && post.id === Number(lowerCasedSearchString.substring(1));
            }
            // regular search on content, title, and tags
            const searchStringMatchesAnyPostProperty =
                post.title?.toLowerCase().includes(lowerCasedSearchString) ||
                post.content?.toLowerCase().includes(lowerCasedSearchString) ||
                post.tags?.join().toLowerCase().includes(lowerCasedSearchString);
            return keepPost && (searchStringMatchesAnyPostProperty ?? false);
        }
        return keepPost;
    };

    /**
     * sorts posts by following criteria
     * 1. criterion: displayPriority is PINNED -> pinned posts come first
     * 2. criterion: displayPriority is ARCHIVED  -> archived posts come last
     * -- in between pinned and archived posts --
     * 3. criterion: currently selected criterion in combination with currently selected order
     * @return number indicating the order of two elements
     */
    overviewSortFn = (postA: Post, postB: Post): number => {
        // sort by priority
        if (
            postA.courseWideContext === CourseWideContext.ANNOUNCEMENT &&
            postA.displayPriority === DisplayPriority.PINNED &&
            postB.courseWideContext !== CourseWideContext.ANNOUNCEMENT
        ) {
            return -1;
        }
        if (
            postA.courseWideContext !== CourseWideContext.ANNOUNCEMENT &&
            postB.courseWideContext === CourseWideContext.ANNOUNCEMENT &&
            postB.displayPriority === DisplayPriority.PINNED
        ) {
            return 1;
        }
        if (postA.displayPriority === DisplayPriority.PINNED && postB.displayPriority !== DisplayPriority.PINNED) {
            return -1;
        }
        if (postA.displayPriority !== DisplayPriority.PINNED && postB.displayPriority === DisplayPriority.PINNED) {
            return 1;
        }
        if (postA.displayPriority === DisplayPriority.ARCHIVED && postB.displayPriority !== DisplayPriority.ARCHIVED) {
            return 1;
        }
        if (postA.displayPriority !== DisplayPriority.ARCHIVED && postB.displayPriority === DisplayPriority.ARCHIVED) {
            return -1;
        }
        // sort by votes via voteEmojiCount
        if (this.currentSortCriterion === PostSortCriterion.VOTES) {
            const postAVoteEmojiCount = postA.reactions?.filter((reaction: Reaction) => reaction.emojiId === VOTE_EMOJI_ID).length ?? 0;
            const postBVoteEmojiCount = postB.reactions?.filter((reaction: Reaction) => reaction.emojiId === VOTE_EMOJI_ID).length ?? 0;
            if (postAVoteEmojiCount > postBVoteEmojiCount) {
                return this.currentSortDirection === SortDirection.DESC ? -1 : 1;
            }
            if (postAVoteEmojiCount < postBVoteEmojiCount) {
                return this.currentSortDirection === SortDirection.DESC ? 1 : -1;
            }
        }
        // sort by creation date
        if (this.currentSortCriterion === PostSortCriterion.CREATION_DATE) {
            if (Number(postA.creationDate) > Number(postB.creationDate)) {
                return this.currentSortDirection === SortDirection.DESC ? -1 : 1;
            }
            if (Number(postA.creationDate) < Number(postB.creationDate)) {
                return this.currentSortDirection === SortDirection.DESC ? 1 : -1;
            }
        }
        // sort by answer count
        if (this.currentSortCriterion === PostSortCriterion.ANSWER_COUNT) {
            const postAAnswerCount = postA.answers?.length ?? 0;
            const postBAnswerCount = postB.answers?.length ?? 0;
            if (postAAnswerCount > postBAnswerCount) {
                return this.currentSortDirection === SortDirection.DESC ? -1 : 1;
            }
            if (postAAnswerCount < postBAnswerCount) {
                return this.currentSortDirection === SortDirection.DESC ? 1 : -1;
            }
        }
        return 0;
    };

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
            this.currentPostContextFilter.courseWideContext,
            this.exercises?.find((exercise) => exercise.id === this.currentPostContextFilter.exerciseId),
            this.lectures?.find((lecture) => lecture.id === this.currentPostContextFilter.lectureId),
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
            ...this.formGroup.get('context')?.value,
        };
        this.currentSortCriterion = this.formGroup.get('sortBy')?.value;
        this.filterToUnresolved = this.formGroup.get('filterToUnresolved')?.value;
        this.filterToOwn = this.formGroup.get('filterToOwn')?.value;
        this.filterToAnsweredOrReactedByUser = this.formGroup.get('filterToAnsweredOrReacted')?.value;
    }

    /**
     * sets the current filter for context (default: course) and content (default: undefined)
     */
    private resetCurrentFilter(): void {
        this.currentPostContextFilter = {
            courseId: this.course!.id,
            courseWideContext: undefined,
            exerciseId: undefined,
            lectureId: undefined,
        };
        this.currentPostContentFilter = {
            searchText: undefined,
        };
    }
}
