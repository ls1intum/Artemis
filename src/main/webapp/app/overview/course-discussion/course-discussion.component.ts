import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CourseWideContext, DisplayPriority, PageType, PostSortCriterion, SortDirection, VOTE_EMOJI_ID } from 'app/shared/metis/metis.util';
import { Subscription } from 'rxjs';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Reaction } from 'app/entities/metis/reaction.model';

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
    styleUrls: ['./course-discussion.scss'],
    providers: [MetisService],
})
export class CourseDiscussionComponent implements OnInit, OnDestroy {
    course?: Course;
    exercises?: Exercise[];
    lectures?: Lecture[];
    currentPostContextFilter: ContextFilterOption;
    eCourseContext = CourseWideContext;
    eSortBy = PostSortCriterion;
    eSortDirection = SortDirection;
    currentSortCriterion = PostSortCriterion.CREATION_DATE;
    currentSortDirection = SortDirection.DESC;
    currentPostContentFilter: ContentFilterOption;
    searchText?: string;
    formGroup: FormGroup;
    createdPost: Post;
    posts: Post[];
    readonly pageType = PageType.OVERVIEW;

    private postsSubscription: Subscription;
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
                this.lectures = this.course?.lectures;
                this.exercises = this.course?.exercises;
                this.resetCurrentFilter();
                this.createEmptyPost();
                this.resetFormGroup();
                this.initMetisService();
            }
        });
        this.postsSubscription = this.metisService.posts.subscribe((posts: Post[]) => {
            this.posts = posts;
        });
    }

    ngOnInit(): void {}

    resetFormGroup(): void {
        this.formGroup = this.formBuilder.group({
            context: [this.currentPostContextFilter],
            sortBy: [PostSortCriterion.CREATION_DATE],
            sortDirection: [SortDirection.DESC],
        });
    }

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
        this.postsSubscription?.unsubscribe();
    }

    onSelect() {
        this.setFilterAndSort();
        this.metisService.getFilteredAndSortedPosts(
            this.currentPostContextFilter,
            {
                filter: this.filterFn,
                sort: this.overviewSortFn,
            },
            false, // we do not force reload, as when same context is selected, we do not want to fetch posts again
        );
    }

    onSearch() {
        this.currentPostContentFilter.searchText = this.searchText;
        this.metisService.getFilteredAndSortedPosts(
            this.currentPostContextFilter,
            {
                filter: this.filterFn,
                sort: this.overviewSortFn,
            },
            false, // we do not force reload, as when same context is selected, we do not want to fetch posts again
        );
    }

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

    comparePostSortByOptionFn(option1: PostSortCriterion, option2: PostSortCriterion) {
        return option1 === option2;
    }

    compareSortDirectionOptionFn(option1: SortDirection, option2: SortDirection) {
        return option1 === option2;
    }

    filterFn = (post: Post): boolean => {
        if (this.currentPostContentFilter.searchText && this.currentPostContentFilter.searchText.trim().length > 0) {
            // check if the search text is either contained in the title or in the content
            return (post.title?.includes(this.currentPostContentFilter.searchText) || post.content?.includes(this.currentPostContentFilter.searchText)) ?? false;
        }
        return true;
    };

    overviewSortFn = (postA: Post, postB: Post): number => {
        // TODO: think about how to integrate pinned and archived posts
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
     * creates empty default post that is needed on initialization of a newly opened modal to edit or create a post
     */
    createEmptyPost() {
        const emptyPost: Post = new Post();
        if (this.currentPostContextFilter.courseWideContext) {
            emptyPost.courseWideContext = this.currentPostContextFilter.courseWideContext;
        }
        if (this.currentPostContextFilter.exerciseId) {
            emptyPost.exercise = { id: this.currentPostContextFilter.exerciseId } as Exercise;
        }
        if (this.currentPostContextFilter.lectureId) {
            emptyPost.lecture = { id: this.currentPostContextFilter.lectureId } as Lecture;
        } else {
            emptyPost.courseWideContext = CourseWideContext.TECH_SUPPORT as CourseWideContext;
        }
        this.createdPost = emptyPost;
    }

    private initMetisService(): void {
        this.metisService.setCourse(this.course!);
        this.metisService.setPageType(this.pageType);
        this.metisService.getFilteredAndSortedPosts(
            { courseId: this.course!.id },
            {
                filter: this.filterFn,
                sort: this.overviewSortFn,
            },
        );
    }

    private setFilterAndSort() {
        this.currentPostContextFilter = {
            courseId: undefined,
            courseWideContext: undefined,
            exerciseId: undefined,
            lectureId: undefined,
            ...this.formGroup.get('context')?.value,
        };
        this.currentSortCriterion = this.formGroup.get('sortBy')?.value;
        this.currentSortDirection = this.formGroup.get('sortDirection')?.value;
    }

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

    postsTrackByFn(index: number, post: Post): number {
        return post.id!;
    }
}
