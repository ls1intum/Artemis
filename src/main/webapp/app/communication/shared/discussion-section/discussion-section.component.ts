import { AfterViewInit, Component, ElementRef, OnDestroy, effect, inject, input, viewChild, viewChildren } from '@angular/core';
import interact from 'interactjs';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { DisplayPriority, PageType, PostSortCriterion, SortDirection } from 'app/communication/metis.util';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { Subject, combineLatest, map, takeUntil } from 'rxjs';
import { MetisService } from 'app/communication/service/metis.service';
import { Post } from 'app/communication/shared/entities/post.model';
import { User } from 'app/core/user/user.model';
import { PostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { HttpResponse } from '@angular/common/http';
import { faArrowLeft, faChevronLeft, faChevronRight, faGripLinesVertical, faLongArrowRight } from '@fortawesome/free-solid-svg-icons';
import { CourseDiscussionDirective } from 'app/communication/directive/course-discussion.directive';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Channel, ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
import { PostingThreadComponent } from 'app/communication/posting-thread/posting-thread.component';
import { MessageInlineInputComponent } from 'app/communication/message/message-inline-input/message-inline-input.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { toObservable } from '@angular/core/rxjs-interop';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-discussion-section',
    templateUrl: './discussion-section.component.html',
    styleUrls: ['./discussion-section.component.scss'],
    imports: [
        FontAwesomeModule,
        InfiniteScrollDirective,
        FormsModule,
        ReactiveFormsModule,
        PostingThreadComponent,
        MessageInlineInputComponent,
        ArtemisTranslatePipe,
        TranslateDirective,
        NgbTooltipModule,
        ButtonComponent,
    ],
    providers: [MetisService],
})
export class DiscussionSectionComponent extends CourseDiscussionDirective implements AfterViewInit, OnDestroy {
    private channelService = inject(ChannelService);
    private courseStorageService = inject(CourseStorageService);
    private accountService = inject(AccountService);
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private formBuilder = inject(FormBuilder);

    exercise = input<Exercise>();
    lecture = input<Lecture>();

    readonly postCreateEditModal = viewChild<PostCreateEditModalComponent>(PostCreateEditModalComponent);
    readonly messages = viewChildren<ElementRef>('postingThread');
    readonly messages$ = toObservable(this.messages);
    readonly content = viewChild<ElementRef>('itemsContainer');

    private ngUnsubscribe = new Subject<void>();
    private previousScrollDistanceFromTop: number;
    private page = 1;
    private readonly PAGE_SIZE = 50;
    private totalNumberOfPosts = 0;
    // as set for the css class '.items-container'
    private messagesContainerHeight = 700;
    private viewChildrenInitialized = false;
    currentSortDirection = SortDirection.DESCENDING;

    channel: ChannelDTO;
    noChannelAvailable: boolean;
    collapsed = false;
    currentPostId?: number;
    currentPost?: Post;
    currentUser?: User;
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
            const courseId = exercise?.course?.id ?? lecture?.course?.id;
            if (courseId) {
                this.course = this.courseStorageService.getCourse(courseId);
            }
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
            if (this.viewChildrenInitialized && this.content()) {
                this.previousScrollDistanceFromTop = this.content()!.nativeElement.scrollHeight - this.content()!.nativeElement.scrollTop;
            }
            this.posts = posts
                .slice()
                .sort((a, b) => {
                    if (a.displayPriority === DisplayPriority.PINNED && b.displayPriority !== DisplayPriority.PINNED) {
                        return 1;
                    }
                    if (a.displayPriority !== DisplayPriority.PINNED && b.displayPriority === DisplayPriority.PINNED) {
                        return -1;
                    }
                    return 0;
                })
                .reverse();
            this.isLoading = false;
            if (this.currentPostId && this.posts.length > 0) {
                this.currentPost = this.posts.find((post) => post.id === this.currentPostId);
            }
        });
        this.accountService.identity().then((user: User) => {
            this.currentUser = user!;
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
        this.postCreateEditModal()?.modalRef?.close();
    }

    /**
     * on changing the sort direction via icon, the metis service is invoked to deliver the posts for the currently set context,
     * sorted on the server
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

        this.messages$.pipe(takeUntil(this.ngUnsubscribe)).subscribe(() => {
            this.handleScrollOnNewMessage();
        });

        this.viewChildrenInitialized = true;
    }

    handleScrollOnNewMessage = () => {
        if ((this.posts.length > 0 && this.content()?.nativeElement.scrollTop === 0 && this.page === 1) || this.previousScrollDistanceFromTop === this.messagesContainerHeight) {
            this.scrollToBottomOfMessages();
        }
    };

    scrollToBottomOfMessages() {
        if (this.viewChildrenInitialized && this.content()?.nativeElement) {
            this.content()!.nativeElement.scrollTop = this.content()!.nativeElement.scrollHeight;
        }
    }

    fetchNextPage() {
        const morePostsAvailable = this.posts.length < this.totalNumberOfPosts;
        if (morePostsAvailable) {
            this.page += 1;
            this.commandMetisToFetchPosts();
        }
        if (this.content()?.nativeElement) {
            this.content()!.nativeElement.scrollTop = this.content()!.nativeElement.scrollTop + this.PAGE_SIZE;
        }
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
            conversationIds: this.channel?.id ? [this.channel?.id] : undefined,
            authorIds: this.formGroup.get('filterToOwn')?.value && this.currentUser?.id ? [this.currentUser.id] : undefined,
            searchText: this.searchText?.trim(),
            filterToUnresolved: this.formGroup.get('filterToUnresolved')?.value,
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
