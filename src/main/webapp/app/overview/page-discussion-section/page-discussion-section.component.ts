import { AfterViewInit, Component, Input, OnChanges, OnDestroy, OnInit, ViewChild } from '@angular/core';
import interact from 'interactjs';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { DisplayPriority, PageType, VOTE_EMOJI_ID } from 'app/shared/metis/metis.util';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute } from '@angular/router';
import { map, Subscription } from 'rxjs';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { Reaction } from 'app/entities/metis/reaction.model';
import { PostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';

@Component({
    selector: 'jhi-page-discussion-section',
    templateUrl: './page-discussion-section.component.html',
    styleUrls: ['./page-discussion-section.scss'],
    providers: [MetisService],
})
export class PageDiscussionSectionComponent implements OnInit, OnChanges, AfterViewInit, OnDestroy {
    @Input() exercise?: Exercise;
    @Input() lecture?: Lecture;
    @ViewChild(PostCreateEditModalComponent) postCreateEditModal?: PostCreateEditModalComponent;
    course?: Course;
    collapsed = false;
    createdPost: Post;
    posts: Post[];
    isLoading = true;
    readonly pageType = PageType.PAGE_SECTION;

    private postsSubscription: Subscription;
    private paramSubscription: Subscription;

    constructor(private metisService: MetisService, private activatedRoute: ActivatedRoute, private courseCalculationService: CourseScoreCalculationService) {
        this.paramSubscription = this.activatedRoute.params.subscribe((params) => {
            const courseId = parseInt(params['courseId'], 10);
            this.course = this.courseCalculationService.getCourse(courseId);
            if (this.course) {
                this.metisService.setCourse(this.course!);
                this.metisService.setPageType(this.pageType);
            }
        });
        this.postsSubscription = this.metisService.posts.pipe(map((posts: Post[]) => posts.sort(this.sectionSortFn))).subscribe((posts: Post[]) => {
            this.posts = posts;
            this.isLoading = false;
        });
    }

    /**
     * on initialization: fetches the posts for the exercise or lecture the discussion section is placed at,
     * triggers the creation of an empty post
     */
    ngOnInit(): void {
        this.metisService.getFilteredPosts({
            exerciseId: this.exercise?.id,
            lectureId: this.lecture?.id,
        });
        this.createdPost = this.createEmptyPost();
    }

    /**
     * on changes: fetches the posts for the exercise or lecture the discussion section is placed at,
     * triggers the creation of an empty post
     */
    ngOnChanges(): void {
        this.metisService.getFilteredPosts({
            exerciseId: this.exercise?.id,
            lectureId: this.lecture?.id,
        });
        this.createdPost = this.createEmptyPost();
    }

    /**
     * on leaving the page, the modal should be closed, subscriptions unsubscribed
     */
    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
        this.postsSubscription?.unsubscribe();
        this.postCreateEditModal?.modalRef?.close();
    }

    /**
     * sorts posts by following criteria
     * 1. criterion: displayPriority is PINNED -> pinned posts come first
     * 2. criterion: displayPriority is ARCHIVED  -> archived posts come last
     * -- in between pinned and archived posts --
     * 3. criterion: vote-emoji count -> posts with more vote-emoji counts comes first
     * 4. criterion: creationDate -> most recent comes at the end (chronologically from top to bottom)
     * @return Post[] sorted array of posts
     */
    sectionSortFn(postA: Post, postB: Post): number {
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
        const postAVoteEmojiCount = postA.reactions?.filter((reaction: Reaction) => reaction.emojiId === VOTE_EMOJI_ID).length ?? 0;
        const postBVoteEmojiCount = postB.reactions?.filter((reaction: Reaction) => reaction.emojiId === VOTE_EMOJI_ID).length ?? 0;
        if (postAVoteEmojiCount > postBVoteEmojiCount) {
            return -1;
        }
        if (postAVoteEmojiCount < postBVoteEmojiCount) {
            return 1;
        }
        if (Number(postA.creationDate) > Number(postB.creationDate)) {
            return 1;
        }
        if (Number(postA.creationDate) < Number(postB.creationDate)) {
            return -1;
        }
        return 0;
    }

    /**
     * invoke metis service to create an empty default post that is needed on initialization of a modal to create a post
     * @return Post created empty default post with either exercise or lecture set as context
     */
    createEmptyPost(): Post {
        return this.metisService.createEmptyPostForContext(undefined, this.exercise, this.lecture?.id);
    }

    /**
     * defines a function that returns the post id as unique identifier,
     * by this means, Angular determines which post in the collection of posts has to be reloaded/destroyed on changes
     */
    postsTrackByFn = (index: number, post: Post): number => post.id!;

    /**
     * makes discussion section expandable by configuring 'interact'
     */
    ngAfterViewInit(): void {
        interact('.expanded-discussion')
            .resizable({
                edges: { left: '.draggable-left', right: false, bottom: false, top: false },
                modifiers: [
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: 360, height: 0 },
                        max: { width: 600, height: 4000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function (event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                target.style.width = event.rect.width + 'px';
            });
    }
}
