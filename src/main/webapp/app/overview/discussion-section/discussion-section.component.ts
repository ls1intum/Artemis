import { AfterViewInit, Component, ElementRef, OnDestroy, QueryList, ViewChild, ViewChildren, effect, inject, input } from '@angular/core';
import interact from 'interactjs';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { PageType, PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { Subject, combineLatest, map, takeUntil } from 'rxjs';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { HttpResponse } from '@angular/common/http';
import { faArrowLeft, faChevronLeft, faChevronRight, faGripLinesVertical, faLongArrowRight } from '@fortawesome/free-solid-svg-icons';
import { CourseDiscussionDirective } from 'app/shared/metis/course-discussion.directive';
import { FormBuilder } from '@angular/forms';
import { Channel, ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisPlagiarismCasesSharedModule } from 'app/course/plagiarism-cases/shared/plagiarism-cases-shared.module';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { InfiniteScrollModule } from 'ngx-infinite-scroll';

@Component({
    selector: 'jhi-discussion-section',
    templateUrl: './discussion-section.component.html',
    styleUrls: ['./discussion-section.component.scss'],
    imports: [FontAwesomeModule, ArtemisSharedModule, ArtemisPlagiarismCasesSharedModule, InfiniteScrollModule],
    standalone: true,
    providers: [MetisService],
})
export class DiscussionSectionComponent extends CourseDiscussionDirective implements AfterViewInit, OnDestroy {
    private channelService = inject(ChannelService);
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private formBuilder = inject(FormBuilder);

    exercise = input<Exercise>();
    lecture = input<Lecture>();

    @ViewChild(PostCreateEditModalComponent) postCreateEditModal?: PostCreateEditModalComponent;
    @ViewChildren('postingThread') messages: QueryList<any>;
    @ViewChild('itemsContainer') content: ElementRef;

    private ngUnsubscribe = new Subject<void>();
    private previousScrollDistanceFromTop: number;
    private page = 1;
    private readonly PAGE_SIZE = 50;
    private totalNumberOfPosts = 0;
    // as set for the css class '.items-container'
    private messagesContainerHeight = 700;
    currentSortDirection = SortDirection.DESCENDING;

    channel: ChannelDTO;
    noChannelAvailable: boolean;
    collapsed = false;
    currentPostId?: number;
    currentPost?: Post;
    shouldSendMessage: boolean;
    readonly PAGE_TYPE = PageType.PAGE_SECTION;

    // Icons
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;
    faGripLinesVertical = faGripLinesVertical;
    faArrowLeft = faArrowLeft;
    faLongArrowRight = faLongArrowRight;

    constructor() {
        super();
        effect(() => this.loadData(this.exercise(), this.lecture()));
    }

    loadData(exercise?: Exercise, lecture?: Lecture): void {
        this.paramSubscription = combineLatest({
            params: this.activatedRoute.params,
            queryParams: this.activatedRoute.queryParams,
        }).subscribe((routeParams: { params: Params; queryParams: Params }) => {
            this.currentPostId = +routeParams.queryParams.postId;
            this.course = exercise?.course ?? lecture?.course;
            this.metisService.setCourse(this.course);
            this.metisService.setPageType(this.PAGE_TYPE);
            if (routeParams.params.courseId) {
                this.setChannel(routeParams.params.courseId);
            } else if (this.course?.id) {
                this.setChannel(this.course.id);
            }
            this.createEmptyPost();
            this.resetFormGroup();
        });
        this.postsSubscription = this.metisService.posts.subscribe((posts: Post[]) => {
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
     * Set the channel for the discussion section, either for a lecture or an exercise
     * @param courseId
     */
    setChannel(courseId: number): void {
        const getChannel = () => {
            return {
                next: (channel: ChannelDTO) => {
                    this.channel = channel ?? undefined;
                    this.resetFormGroup();
                    this.setFilterAndSort();

                    if (!this.channel?.id) {
                        this.noChannelAvailable = true;
                        this.collapsed = true;
                        return;
                    }

                    this.metisService.getFilteredPosts(this.currentPostContextFilter, true, this.channel);

                    this.createEmptyPost();
                    this.resetFormGroup();
                },
            };
        };

        // Currently, an additional REST call is made to retrieve the channel associated with the lecture/exercise
        // TODO: Add the channel to the response for loading the lecture/exercise
        if (this.lecture()) {
            this.channelService
                .getChannelOfLecture(courseId, this.lecture()!.id!)
                .pipe(map((res: HttpResponse<ChannelDTO>) => res.body))
                .subscribe(getChannel());
        } else if (this.exercise()) {
            this.channelService
                .getChannelOfExercise(courseId, this.exercise()!.id!)
                .pipe(map((res: HttpResponse<ChannelDTO>) => res.body))
                .subscribe(getChannel());
        }
    }

    /**
     * invoke metis service to create an empty default post that is needed on initialization of a modal to create a post,
     * this empty post has either exercise or lecture set as context, depending on if this component holds an exercise or a lecture reference
     */
    createEmptyPost(): void {
        if (this.channel) {
            const conversation = this.channel as Channel;
            this.shouldSendMessage = false;
            this.createdPost = this.metisService.createEmptyPostForContext(conversation);
        } else {
            this.createdPost = this.metisService.createEmptyPostForContext();
        }
    }

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

        this.messages.changes.pipe(takeUntil(this.ngUnsubscribe)).subscribe(() => {
            this.handleScrollOnNewMessage();
        });
    }

    handleScrollOnNewMessage = () => {
        if ((this.posts.length > 0 && this.content?.nativeElement.scrollTop === 0 && this.page === 1) || this.previousScrollDistanceFromTop === this.messagesContainerHeight) {
            this.scrollToBottomOfMessages();
        }
    };

    scrollToBottomOfMessages() {
        if (this.content?.nativeElement) {
            this.content.nativeElement.scrollTop = this.content.nativeElement.scrollHeight;
        }
    }

    fetchNextPage() {
        const morePostsAvailable = this.posts.length < this.totalNumberOfPosts;
        if (morePostsAvailable) {
            this.page += 1;
            this.commandMetisToFetchPosts();
        }
        this.content.nativeElement.scrollTop = this.content.nativeElement.scrollTop + this.PAGE_SIZE;
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
        this.scrollToBottomOfMessages();
        this.currentPostContextFilter = {
            courseId: undefined,
            conversationId: this.channel?.id,
            searchText: this.searchText?.trim(),
            filterToUnresolved: this.formGroup.get('filterToUnresolved')?.value,
            filterToOwn: this.formGroup.get('filterToOwn')?.value,
            filterToAnsweredOrReacted: this.formGroup.get('filterToAnsweredOrReacted')?.value,
            pagingEnabled: true,
            page: 0,
            pageSize: this.PAGE_SIZE,
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
            exerciseId: this.exercise()?.id,
            lectureId: this.lecture()?.id,
            filterToUnresolved: false,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
        });
    }

    toggleSendMessage(): void {
        this.shouldSendMessage = !this.shouldSendMessage;
    }
}
