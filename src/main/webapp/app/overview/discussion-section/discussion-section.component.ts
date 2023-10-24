import { AfterViewInit, Component, ElementRef, Input, OnDestroy, OnInit, QueryList, ViewChild, ViewChildren } from '@angular/core';
import interact from 'interactjs';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { DisplayPriority, PageType, PostSortCriterion, SortDirection, VOTE_EMOJI_ID } from 'app/shared/metis/metis.util';
import { CourseInformationSharingConfiguration } from 'app/entities/course.model';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { Subject, combineLatest, map, takeUntil } from 'rxjs';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { Reaction } from 'app/entities/metis/reaction.model';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { faArrowLeft, faChevronLeft, faChevronRight, faGripLinesVertical, faLongArrowRight } from '@fortawesome/free-solid-svg-icons';
import { CourseDiscussionDirective } from 'app/shared/metis/course-discussion.directive';
import { FormBuilder } from '@angular/forms';
import { Channel, ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';

@Component({
    selector: 'jhi-discussion-section',
    templateUrl: './discussion-section.component.html',
    styleUrls: ['./discussion-section.component.scss'],
    providers: [MetisService],
})
export class DiscussionSectionComponent extends CourseDiscussionDirective implements OnInit, AfterViewInit, OnDestroy {
    @Input() exercise?: Exercise;
    @Input() lecture?: Lecture;
    @Input() isCommunicationPage?: boolean;

    @ViewChild(PostCreateEditModalComponent) postCreateEditModal?: PostCreateEditModalComponent;
    @ViewChildren('postingThread') messages: QueryList<any>;
    @ViewChild('itemsContainer') content: ElementRef;

    private ngUnsubscribe = new Subject<void>();
    private previousScrollDistanceFromTop: number;
    private page = 1;
    private readonly pageSize = 50;
    private totalNumberOfPosts = 0;
    // as set for the css class '.items-container'
    private messagesContainerHeight = 700;
    currentSortDirection = SortDirection.DESCENDING;

    channel: Channel;
    isNotAChannelMember: boolean;
    noChannelAvailable: boolean;
    collapsed = false;
    currentPostId?: number;
    currentPost?: Post;
    shouldSendMessage: boolean;
    readonly pageType = PageType.PAGE_SECTION;

    // Icons
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;
    faGripLinesVertical = faGripLinesVertical;
    faArrowLeft = faArrowLeft;
    faLongArrowRight = faLongArrowRight;

    constructor(
        protected metisService: MetisService,
        private channelService: ChannelService,
        private activatedRoute: ActivatedRoute,
        private courseManagementService: CourseManagementService,
        private router: Router,
        private formBuilder: FormBuilder,
    ) {
        super(metisService);
    }

    /**
     * on initialization: initializes the metis service, fetches the posts for the exercise or lecture the discussion section is placed at,
     * creates the subscription to posts to stay updated on any changes of posts in this course
     */
    ngOnInit(): void {
        this.paramSubscription = combineLatest({
            params: this.activatedRoute.params,
            queryParams: this.activatedRoute.queryParams,
        }).subscribe((routeParams: { params: Params; queryParams: Params }) => {
            this.currentPostId = +routeParams.queryParams.postId;
            this.course = this.exercise?.course ?? this.lecture?.course;
            this.metisService.setCourse(this.course);
            this.metisService.setPageType(this.pageType);
            if (routeParams.params.courseId) {
                this.setChannel(routeParams.params.courseId);
            } else if (this.course?.id) {
                this.setChannel(this.course.id);
            }
            this.createEmptyPost();
            this.resetFormGroup();
        });
        this.postsSubscription = this.metisService.posts.pipe(map((posts: Post[]) => posts.sort(this.sectionSortFn))).subscribe((posts: Post[]) => {
            if (this.content) {
                this.previousScrollDistanceFromTop = this.content.nativeElement.scrollHeight - this.content.nativeElement.scrollTop;
            }
            this.posts = posts.slice().reverse();
            this.isLoading = false;
            if (this.currentPostId && this.posts.length > 0) {
                this.currentPost = this.posts.find((post) => post.id === this.currentPostId);
            }
        });
        this.metisService.totalNumberOfPosts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((totalNumberOfPosts: number) => {
            this.totalNumberOfPosts = totalNumberOfPosts;
        });
    }

    /**
     * on leaving the page, the modal should be closed
     */
    ngOnDestroy(): void {
        super.onDestroy();
        this.postCreateEditModal?.modalRef?.close();
    }

    /**
     * on changing the sort direction via icon, the metis service is invoked to deliver the posts for the currently set context,
     * sorted on the backend
     */
    onChangeSortDir(): void {
        this.currentSortDirection = this.currentSortDirection === SortDirection.DESCENDING ? SortDirection.ASCENDING : SortDirection.DESCENDING;
        this.onSelectContext();
    }

    /**
     * sorts posts by following criteria
     * 1. criterion: displayPriority is PINNED -> pinned posts come first
     * 2. criterion: displayPriority is ARCHIVED  -> archived posts come last
     * -- in between pinned and archived posts --
     * 3. criterion (optional): creationDate - if activated by user through the sort arrow -> most recent comes at the end (chronologically from top to bottom)
     * 4. criterion: if 3'rd criterion was not activated by the user, vote-emoji count -> posts with more vote-emoji counts comes first
     * 5. criterion: most recent posts comes at the end (chronologically from top to bottom)
     * @return Post[] sorted array of posts
     */
    sectionSortFn = (postA: Post, postB: Post): number => {
        // 1st criterion
        if (postA.displayPriority === DisplayPriority.PINNED && postB.displayPriority !== DisplayPriority.PINNED) {
            return -1;
        }
        if (postA.displayPriority !== DisplayPriority.PINNED && postB.displayPriority === DisplayPriority.PINNED) {
            return 1;
        }

        // 2nd criterion
        if (postA.displayPriority === DisplayPriority.ARCHIVED && postB.displayPriority !== DisplayPriority.ARCHIVED) {
            return 1;
        }
        if (postA.displayPriority !== DisplayPriority.ARCHIVED && postB.displayPriority === DisplayPriority.ARCHIVED) {
            return -1;
        }

        // 3rd criterion
        if (this.currentSortDirection) {
            const comparison = this.sortByDate(postA, postB, this.currentSortDirection);
            if (comparison !== 0) {
                return comparison;
            }
        }

        // 4th criterion
        const postAVoteEmojiCount = postA.reactions?.filter((reaction: Reaction) => reaction.emojiId === VOTE_EMOJI_ID).length ?? 0;
        const postBVoteEmojiCount = postB.reactions?.filter((reaction: Reaction) => reaction.emojiId === VOTE_EMOJI_ID).length ?? 0;
        if (postAVoteEmojiCount > postBVoteEmojiCount) {
            return -1;
        }
        if (postAVoteEmojiCount < postBVoteEmojiCount) {
            return 1;
        }

        // 5th criterion
        return this.sortByDate(postA, postB, SortDirection.ASCENDING);
    };

    /**
     * Set the channel for the discussion section, either for a lecture or an exercise
     * @param courseId
     */
    setChannel(courseId: number): void {
        if (this.course?.courseInformationSharingConfiguration === CourseInformationSharingConfiguration.COMMUNICATION_ONLY) {
            this.metisService.getFilteredPosts({
                exerciseIds: this.exercise?.id !== undefined ? [this.exercise.id] : undefined,
                lectureIds: this.lecture?.id !== undefined ? [this.lecture.id] : undefined,
            });
            return;
        }
        const getChannel = () => {
            return {
                next: (channel: Channel) => {
                    this.channel = channel ?? undefined;
                    this.setFilterAndSort();

                    if (!this.channel && this.course?.courseInformationSharingConfiguration === CourseInformationSharingConfiguration.MESSAGING_ONLY) {
                        this.noChannelAvailable = true;
                        this.collapsed = true;
                        return;
                    }

                    if (this.channel?.id) {
                        const channelDTO = new ChannelDTO();
                        channelDTO.isCourseWide = true;
                        this.metisService.getFilteredPosts(this.currentPostContextFilter, true, channelDTO);
                    } else {
                        const contextFilter = {
                            exerciseIds: this.exercise?.id ? [this.exercise.id] : undefined,
                            lectureIds: this.lecture?.id ? [this.lecture.id] : undefined,
                        };
                        this.metisService.getFilteredPosts(contextFilter);
                    }

                    this.createEmptyPost();
                    this.resetFormGroup();
                },
                error: (error: HttpErrorResponse) => {
                    if (error.status === 403 && error.error.message === 'error.noAccessButCouldJoin') {
                        this.isNotAChannelMember = true;
                        this.collapsed = true;
                    }
                },
            };
        };

        // Currently, an additional REST call is made to retrieve the channel associated with the lecture/exercise
        // TODO: Add the channel to the response for loading the lecture/exercise
        if (this.lecture?.id) {
            this.channelService
                .getChannelOfLecture(courseId, this.lecture.id)
                .pipe(map((res: HttpResponse<Channel>) => res.body))
                .subscribe(getChannel());
        } else if (this.exercise?.id) {
            this.channelService
                .getChannelOfExercise(courseId, this.exercise.id)
                .pipe(map((res: HttpResponse<Channel>) => res.body))
                .subscribe(getChannel());
        }
    }

    /**
     * invoke metis service to create an empty default post that is needed on initialization of a modal to create a post,
     * this empty post has either exercise or lecture set as context, depending on if this component holds an exercise or a lecture reference
     */
    createEmptyPost(): void {
        if (this.channel) {
            const conversation = this.channel;
            this.shouldSendMessage = false;
            this.createdPost = this.metisService.createEmptyPostForContext(undefined, undefined, undefined, undefined, conversation);
        } else {
            this.createdPost = this.metisService.createEmptyPostForContext(undefined, this.exercise, this.lecture);
        }
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
                        min: { width: 375, height: 0 },
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

        this.messages.changes.pipe(takeUntil(this.ngUnsubscribe)).subscribe(this.handleScrollOnNewMessage);
    }

    handleScrollOnNewMessage = () => {
        if ((this.posts.length > 0 && this.content.nativeElement.scrollTop === 0 && this.page === 1) || this.previousScrollDistanceFromTop === this.messagesContainerHeight) {
            this.scrollToBottomOfMessages();
        }
    };

    scrollToBottomOfMessages() {
        this.content.nativeElement.scrollTop = this.content.nativeElement.scrollHeight;
    }

    fetchNextPage() {
        const morePostsAvailable = this.posts.length < this.totalNumberOfPosts;
        if (morePostsAvailable) {
            this.page += 1;
            this.commandMetisToFetchPosts();
        }
        this.content.nativeElement.scrollTop = this.content.nativeElement.scrollTop + this.pageSize;
    }

    public commandMetisToFetchPosts(forceUpdate = false) {
        if (this.currentPostContextFilter) {
            this.currentPostContextFilter = { ...this.currentPostContextFilter, page: this.page - 1 };
            this.metisService.getFilteredPosts(this.currentPostContextFilter, forceUpdate, this.channel);
        }
    }

    /**
     * sets the filter options after receiving user input
     */
    setFilterAndSort(): void {
        this.page = 1;
        this.currentPostContextFilter = {
            courseId: undefined,
            exerciseIds: undefined,
            lectureIds: undefined,
            conversationId: this.channel?.id,
            searchText: this.searchText?.trim(),
            filterToUnresolved: this.formGroup.get('filterToUnresolved')?.value,
            filterToOwn: this.formGroup.get('filterToOwn')?.value,
            filterToAnsweredOrReacted: this.formGroup.get('filterToAnsweredOrReacted')?.value,
            pagingEnabled: true,
            page: 0,
            pageSize: this.pageSize,
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            sortingOrder: this.currentSortDirection,
        };
    }

    resetCurrentPost() {
        this.currentPost = undefined;
        this.currentPostId = undefined;
        this.router.navigate([], {
            queryParams: {
                postId: this.currentPostId,
            },
            queryParamsHandling: 'merge',
        });
    }

    /**
     * by default, the form group fields are set to show all posts of the current exercise or lecture
     */
    resetFormGroup(): void {
        this.formGroup = this.formBuilder.group({
            conversationId: this.channel?.id,
            exerciseId: this.exercise?.id,
            lectureId: this.lecture?.id,
            filterToUnresolved: false,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
        });
    }

    /**
     * helper method which returns the order which posts must be listed
     * @param postA     first post to compare
     * @param postB     second post to compare
     * @param sortDirection ascending or descending
     * @return number   the order which posts must be listed
     */
    sortByDate = (postA: Post, postB: Post, sortDirection: SortDirection): number => {
        if (Number(postA.creationDate) > Number(postB.creationDate)) {
            return sortDirection === SortDirection.DESCENDING ? -1 : 1;
        }
        if (Number(postA.creationDate) < Number(postB.creationDate)) {
            return sortDirection === SortDirection.DESCENDING ? 1 : -1;
        }
        return 0;
    };

    toggleSendMessage(): void {
        this.shouldSendMessage = !this.shouldSendMessage;
    }
}
