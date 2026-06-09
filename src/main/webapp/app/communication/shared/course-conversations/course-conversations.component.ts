import { BreakpointObserver } from '@angular/cdk/layout';
import { NgClass } from '@angular/common';
import { ChangeDetectorRef, Component, OnDestroy, OnInit, ViewEncapsulation, computed, inject, output, signal, viewChild } from '@angular/core';
import { outputToObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
    faBookmark,
    faBoxArchive,
    faClock,
    faComment,
    faComments,
    faEnvelopeOpenText,
    faFile,
    faFilter,
    faGraduationCap,
    faHeart,
    faList,
    faMessage,
    faPersonChalkboard,
    faPlus,
    faTimes,
} from '@fortawesome/free-solid-svg-icons';
import { DialogService } from 'primeng/dynamicdialog';
import { captureException } from '@sentry/angular';
import { canCreateChannel } from 'app/communication/conversations/conversation-permissions.utils';
import { CourseConversationsCodeOfConductComponent } from 'app/communication/course-conversations-components/code-of-conduct/course-conversations-code-of-conduct.component';
import { CourseWideSearchComponent, CourseWideSearchConfig } from 'app/communication/course-conversations-components/course-wide-search/course-wide-search.component';
import { ChannelsCreateDialogComponent } from 'app/communication/course-conversations-components/dialogs/channels-create-dialog/channels-create-dialog.component';
import {
    ChannelAction,
    ChannelsOverviewDialogComponent,
} from 'app/communication/course-conversations-components/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { GroupChatCreateDialogComponent } from 'app/communication/course-conversations-components/group-chat-create-dialog/group-chat-create-dialog.component';
import { ConversationHeaderComponent } from 'app/communication/course-conversations-components/layout/conversation-header/conversation-header.component';
import { ConversationMessagesComponent } from 'app/communication/course-conversations-components/layout/conversation-messages/conversation-messages.component';
import { ConversationThreadSidebarComponent } from 'app/communication/course-conversations-components/layout/conversation-thread-sidebar/conversation-thread-sidebar.component';
import { OneToOneChatCreateDialogComponent } from 'app/communication/course-conversations-components/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { defaultFirstLayerDialogOptions, defaultSecondLayerDialogOptions } from 'app/communication/course-conversations-components/other/conversation.util';
import { SavedPostsComponent } from 'app/communication/course-conversations-components/saved-posts/saved-posts.component';
import { FaqService } from 'app/communication/faq/faq.service';
import { LinkPreviewService } from 'app/communication/link-preview/services/link-preview.service';
import { LinkifyService } from 'app/communication/link-preview/services/linkify.service';
import { PageType, SortDirection } from 'app/communication/metis.util';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MetisService } from 'app/communication/service/metis.service';
import { ConversationGlobalSearchComponent, ConversationGlobalSearchConfig } from 'app/communication/shared/conversation-global-search/conversation-global-search.component';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { ChannelDTO, ChannelSubType, getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { FaqState } from 'app/communication/shared/entities/faq.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { Posting, PostingType, SavedPostStatus, toSavedPostStatus } from 'app/communication/shared/entities/posting.model';
import { CourseOverviewService } from 'app/course/overview/services/course-overview.service';
import { CourseSidebarService } from 'app/course/overview/services/course-sidebar.service';
import { Course, CourseInformationSharingConfiguration, isCommunicationEnabled, isMessagingEnabled } from 'app/course/shared/entities/course.model';
import { UserPublicInfoDTO } from 'app/account/user/user.model';
import { FeatureActivationComponent } from 'app/shared-ui/feature-activation/feature-activation.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { LoadingIndicatorContainerComponent } from 'app/shared-ui/loading-indicator-container/loading-indicator-container.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { SidebarComponent } from 'app/course/sidebar/sidebar.component';
import { AccordionGroups, ChannelTypeIcons, CollapseState, SidebarCardElement, SidebarData, SidebarItemShowAlways } from 'app/foundation/types/sidebar';
import { Observable, Subject, Subscription, firstValueFrom } from 'rxjs';
import { debounceTime, distinctUntilChanged, filter, take, takeUntil } from 'rxjs/operators';
import { ConversationSelectionState } from 'app/communication/shared/course-conversations/course-conversation-selection.state';
import { getIsMobileSignal } from 'app/foundation/util/global.utils';

const DEFAULT_CHANNEL_GROUPS: AccordionGroups = {
    unreadMessages: { entityData: [] },
    favoriteChannels: { entityData: [] },
    recents: { entityData: [] },
    generalChannels: { entityData: [] },
    exerciseChannels: { entityData: [] },
    lectureChannels: { entityData: [] },
    examChannels: { entityData: [] },
    feedbackDiscussion: { entityData: [] },
    archivedChannels: { entityData: [] },
    savedPosts: { entityData: [] },
};

const CHANNEL_TYPE_ICON: ChannelTypeIcons = {
    unreadMessages: faEnvelopeOpenText,
    generalChannels: faMessage,
    exerciseChannels: faList,
    examChannels: faGraduationCap,
    groupChats: faComments,
    directMessages: faComment,
    favoriteChannels: faHeart,
    lectureChannels: faFile,
    archivedChannels: faBoxArchive,
    feedbackDiscussion: faPersonChalkboard,
    savedPosts: faBookmark,
    recents: faClock,
};

const DEFAULT_COLLAPSE_STATE: CollapseState = {
    unreadMessages: false,
    generalChannels: true,
    exerciseChannels: true,
    examChannels: true,
    groupChats: true,
    directMessages: true,
    favoriteChannels: false,
    lectureChannels: true,
    archivedChannels: true,
    feedbackDiscussion: true,
    savedPosts: true,
    recents: true,
};

const DEFAULT_SHOW_ALWAYS: SidebarItemShowAlways = {
    unreadMessages: false,
    generalChannels: true,
    exerciseChannels: false,
    examChannels: false,
    groupChats: true,
    directMessages: true,
    favoriteChannels: true,
    lectureChannels: false,
    archivedChannels: false,
    feedbackDiscussion: false,
    savedPosts: true,
    recents: true,
};

@Component({
    selector: 'jhi-course-conversations',
    templateUrl: './course-conversations.component.html',
    styleUrls: ['../../../course/overview/course-overview/course-overview.scss', './course-conversations.component.scss'],
    encapsulation: ViewEncapsulation.None,
    providers: [MetisService, LinkifyService, LinkPreviewService],
    imports: [
        LoadingIndicatorContainerComponent,
        FormsModule,
        CourseConversationsCodeOfConductComponent,
        TranslateDirective,
        NgClass,
        SidebarComponent,
        ConversationHeaderComponent,
        ConversationMessagesComponent,
        CourseWideSearchComponent,
        SavedPostsComponent,
        ConversationThreadSidebarComponent,
        ConversationGlobalSearchComponent,
        FeatureActivationComponent,
    ],
})
export class CourseConversationsComponent implements OnInit, OnDestroy {
    readonly isCommunicationEnabled = computed(() => {
        const currentCourse = this.course();
        return currentCourse ? isCommunicationEnabled(currentCourse) : false;
    });
    protected readonly faComments = faComments;
    private router = inject(Router);
    private activatedRoute = inject(ActivatedRoute);
    private readonly selectionState = inject(ConversationSelectionState);
    private metisConversationService = inject(MetisConversationService);
    private metisService = inject(MetisService);
    private faqService = inject(FaqService);
    private courseOverviewService = inject(CourseOverviewService);
    private dialogService = inject(DialogService);
    private alertService = inject(AlertService);
    private eventManager = inject(EventManager);
    private breakpointObserver = inject(BreakpointObserver);

    readonly isMobile = getIsMobileSignal(this.breakpointObserver);

    private ngUnsubscribe = new Subject<void>();
    private closeSidebarEventSubscription: Subscription;
    private openSidebarEventSubscription: Subscription;
    private toggleSidebarEventSubscription: Subscription;
    private reloadSidebarEventSubscription: Subscription;
    course = signal<Course | undefined>(undefined);
    isLoading = false;
    isServiceSetUp = false;
    messagingEnabled = false;
    postInThread?: Post;
    private pendingThreadPostId: number | undefined;
    activeConversation?: ConversationDTO = undefined;
    conversationsOfUser: ConversationDTO[] = [];
    previousConversationBeforeSearch?: ConversationDTO;
    lastKnownConversationId?: number;

    conversationSelected = true;
    sidebarData: SidebarData;
    accordionConversationGroups: AccordionGroups;
    sidebarConversations: SidebarCardElement[] = [];
    isCollapsed = false;
    focusPostId: number | undefined = undefined;
    focusReplyId: number | undefined = undefined;
    openThreadOnFocus = false;
    selectedSavedPostStatus: undefined | SavedPostStatus = undefined;
    showOnlyPinned = false;
    pinnedCount: number = 0;
    isManagementView = false;

    readonly CHANNEL_TYPE_ICON = CHANNEL_TYPE_ICON;
    readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;
    protected readonly DEFAULT_SHOW_ALWAYS = DEFAULT_SHOW_ALWAYS;

    // set undefined so nothing gets displayed until isCodeOfConductAccepted is loaded
    isCodeOfConductAccepted?: boolean;
    isCodeOfConductPresented = false;

    courseWideSearch = viewChild<CourseWideSearchComponent>(CourseWideSearchComponent);
    globalSearchComponent = viewChild<ConversationGlobalSearchComponent>(ConversationGlobalSearchComponent);

    courseWideSearchConfig: CourseWideSearchConfig;

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faFilter = faFilter;

    createChannelFn?: (channel: ChannelDTO) => Observable<never>;
    readonly channelActions$ = output<ChannelAction>();

    private courseSidebarService = inject(CourseSidebarService);
    private changeDetector = inject(ChangeDetectorRef);

    getAsChannel = getAsChannelDTO;

    private subscribeToMetis() {
        this.metisService.posts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((posts: Post[]) => {
            if (this.pendingThreadPostId && posts) {
                const found = posts.find((post) => post.id === this.pendingThreadPostId);
                if (found) {
                    this.postInThread = found;
                    this.pendingThreadPostId = undefined;
                }
            } else if (this.postInThread?.id && posts) {
                this.postInThread = posts.find((post) => post.id === this.postInThread?.id);
            }
        });
    }

    togglePinnedView(): void {
        this.showOnlyPinned = !this.showOnlyPinned;
    }

    onPinnedCountChanged(newCount: number): void {
        this.pinnedCount = newCount;
        if (this.pinnedCount == 0 && this.showOnlyPinned) {
            this.showOnlyPinned = false;
        }
        this.changeDetector.detectChanges();
    }

    private setupMetis() {
        this.metisService.setPageType(PageType.OVERVIEW);
        const course = this.course();
        this.metisService.setCourse(course);
        if (course?.id) {
            this.faqService.findAllByCourseIdAndState(course.id, FaqState.ACCEPTED).subscribe({
                next: (res) => {
                    this.metisService.setFaqs(res.body ?? []);
                },
            });
        }
    }

    private getParentCourse(): Course | undefined {
        return this.activatedRoute.parent?.snapshot.data?.course;
    }

    ngOnInit(): void {
        this.course.set(this.getParentCourse());
        this.isManagementView = this.router.url.includes('course-management');

        this.openSidebarEventSubscription = this.courseSidebarService.openSidebar$.subscribe(() => {
            this.setIsCollapsed(true);
        });

        this.closeSidebarEventSubscription = this.courseSidebarService.closeSidebar$.subscribe(() => {
            this.setIsCollapsed(false);
        });

        this.toggleSidebarEventSubscription = this.courseSidebarService.toggleSidebar$.subscribe(() => {
            this.toggleSidebar();
        });

        this.reloadSidebarEventSubscription = this.courseSidebarService.reloadSidebar$.subscribe(() => {
            this.prepareSidebarData();
        });

        if (!this.isMobile()) {
            if (this.courseOverviewService.getSidebarCollapseStateFromStorage('conversation')) {
                this.courseSidebarService.openSidebar();
            } else {
                this.courseSidebarService.closeSidebar();
            }
        } else {
            this.courseSidebarService.openSidebar();
        }

        this.isLoading = true;
        this.metisConversationService.isServiceSetup$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((isServiceSetUp: boolean) => {
            if (isServiceSetUp) {
                this.course.set(this.metisConversationService.course!);
                this.initializeCourseWideSearchConfig();
                this.initializeSidebarAccordions();
                this.setupMetis();
                this.subscribeToMetis();
                this.subscribeToQueryParameter();
                // service is fully set up, now we can subscribe to the respective observables
                this.subscribeToActiveConversation();
                this.subscribeToIsCodeOfConductAccepted();
                this.subscribeToIsCodeOfConductPresented();
                this.subscribeToConversationsOfUser();
                this.updateQueryParameters();
                this.prepareSidebarData();
                this.metisConversationService.checkIsCodeOfConductAccepted(this.course()!);
                if (!this.isServiceSetUp) {
                    outputToObservable(this.channelActions$)
                        .pipe(
                            debounceTime(500),
                            distinctUntilChanged(
                                (prev, curr) =>
                                    curr.action !== 'create' && prev.action === curr.action && prev.channel.id === curr.channel.id && prev.channel.name === curr.channel.name,
                            ),
                            takeUntil(this.ngUnsubscribe),
                        )
                        .subscribe((channelAction) => {
                            this.performChannelAction(channelAction);
                        });
                }
                this.isServiceSetUp = true;
                this.isLoading = false;
            }

            this.createChannelFn = (channel: ChannelDTO) => this.metisConversationService.createChannel(channel);
        });
    }

    performChannelAction(channelAction: ChannelAction) {
        if (this.createChannelFn) {
            this.createChannelFn(channelAction.channel)
                .pipe(takeUntil(this.ngUnsubscribe))
                .subscribe({
                    complete: () => {
                        this.prepareSidebarData();
                    },
                    error: (error) => {
                        captureException('Error creating channel:', error);
                    },
                });
        }
    }

    subscribeToQueryParameter() {
        this.activatedRoute.queryParams.pipe(takeUntil(this.ngUnsubscribe)).subscribe((queryParams) => {
            if (queryParams.focusPostId) {
                this.focusPostId = Number(queryParams.focusPostId);
                this.scrollToAndHighlightPost(this.focusPostId);
            }
            if (queryParams.openThreadOnFocus) {
                this.openThreadOnFocus = queryParams.openThreadOnFocus;
            }

            // Process messageId BEFORE setting the active conversation.
            // setActiveConversation synchronously triggers updateQueryParameters (via subscribeToActiveConversation).
            // postInThread must already be set at that point so that messageId is preserved in the URL;
            // otherwise updateQueryParameters strips it, causing a cascading navigation loop.
            if (queryParams.messageId) {
                const messageId = Number(queryParams.messageId);
                // Only create a bare post skeleton if we don't already have this post loaded.
                // Re-setting postInThread to a bare object discards the full post data and
                // causes updateQueryParameters to strip messageId from the URL.
                if (this.postInThread?.id !== messageId) {
                    const conversationId = queryParams.conversationId && !isNaN(Number(queryParams.conversationId)) ? Number(queryParams.conversationId) : undefined;
                    this.pendingThreadPostId = messageId;
                    this.postInThread = { id: messageId, conversation: { id: conversationId } } as Post;
                    // Immediately try to resolve the full post from already-loaded posts
                    this.metisService.posts.pipe(take(1)).subscribe((posts) => {
                        if (posts) {
                            const found = posts.find((post) => post.id === messageId);
                            if (found) {
                                this.postInThread = found;
                                this.pendingThreadPostId = undefined;
                            }
                        }
                    });
                }
                if (queryParams.focusReplyId) {
                    this.focusReplyId = Number(queryParams.focusReplyId);
                    this.scrollToAndHighlightReply(this.focusReplyId);
                }
                this.closeSidebarOnMobile();
            } else {
                this.postInThread = undefined;
            }

            // NOTE: queryParams.conversationId can either be a number or a string according to SavedPostStatus
            if (queryParams.conversationId) {
                if (
                    isNaN(Number(queryParams.conversationId)) &&
                    Object.values(SavedPostStatus)
                        .map((status) => status.toString().toLowerCase())
                        .includes(queryParams.conversationId)
                ) {
                    this.selectedSavedPostStatus = toSavedPostStatus(queryParams.conversationId);
                } else {
                    this.metisConversationService.setActiveConversation(Number(queryParams.conversationId));
                    this.closeSidebarOnMobile();
                }
            }
        });
    }

    onNavigateToPost(post: Posting) {
        if (post.referencePostId === undefined || post.conversation?.id === undefined) {
            return;
        }

        this.focusPostId = post.referencePostId;
        this.openThreadOnFocus = (post.postingType as PostingType) === PostingType.ANSWER;
        this.metisConversationService.setActiveConversation(post.conversation!.id!);
        this.changeDetector.detectChanges();
    }

    updateQueryParameters() {
        const queryParams: Record<string, string | number | undefined> = {
            conversationId: this.activeConversation?.id ?? this.selectedSavedPostStatus?.toLowerCase(),
        };
        const threadBelongsToActiveConversation = this.postInThread?.id && this.postInThread.conversation?.id === this.activeConversation?.id;
        if (threadBelongsToActiveConversation) {
            queryParams.messageId = this.postInThread!.id;
        }
        this.router.navigate([], {
            relativeTo: this.activatedRoute,
            queryParams,
            replaceUrl: true,
        });
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.openSidebarEventSubscription?.unsubscribe();
        this.closeSidebarEventSubscription?.unsubscribe();
        this.toggleSidebarEventSubscription?.unsubscribe();
        this.reloadSidebarEventSubscription?.unsubscribe();
    }

    private subscribeToActiveConversation() {
        this.metisConversationService.activeConversation$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversation: ConversationDTO) => {
            const previousConversation = this.activeConversation;
            this.activeConversation = conversation;

            if (conversation?.id) {
                this.lastKnownConversationId = conversation.id;
            }

            if (this.isMobile() && conversation && previousConversation?.id !== conversation.id) {
                this.courseSidebarService.closeSidebar();
            }
            this.updateQueryParameters();
        });
    }

    private subscribeToIsCodeOfConductAccepted() {
        this.metisConversationService.isCodeOfConductAccepted$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((isCodeOfConductAccepted: boolean) => {
            this.isCodeOfConductAccepted = isCodeOfConductAccepted;
        });
    }

    private subscribeToIsCodeOfConductPresented() {
        this.metisConversationService.isCodeOfConductPresented$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((isCodeOfConductPresented: boolean) => {
            this.isCodeOfConductPresented = isCodeOfConductPresented;
        });
    }

    private subscribeToConversationsOfUser() {
        this.metisConversationService.conversationsOfUser$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversations: ConversationDTO[]) => {
            this.conversationsOfUser = conversations ?? [];
        });
    }

    acceptCodeOfConduct() {
        if (this.course()) {
            this.metisConversationService.acceptCodeOfConduct(this.course()!);
        }
    }

    initializeCourseWideSearchConfig() {
        this.courseWideSearchConfig = new CourseWideSearchConfig();
        this.courseWideSearchConfig.searchTerm = '';
        this.courseWideSearchConfig.filterToCourseWide = false;
        this.courseWideSearchConfig.filterToUnresolved = false;
        this.courseWideSearchConfig.filterToAnsweredOrReacted = false;
        this.courseWideSearchConfig.sortingOrder = SortDirection.ASCENDING;
        this.courseWideSearchConfig.selectedAuthors = [];
        this.courseWideSearchConfig.selectedConversations = [];
    }

    initializeSidebarAccordions() {
        this.messagingEnabled = isMessagingEnabled(this.course());
        this.accordionConversationGroups = this.messagingEnabled
            ? { ...DEFAULT_CHANNEL_GROUPS, groupChats: { entityData: [] }, directMessages: { entityData: [] } }
            : DEFAULT_CHANNEL_GROUPS;
    }

    onSearch(searchInfo: ConversationGlobalSearchConfig) {
        if (this.isMobile()) {
            const isSearchNonEmpty = searchInfo?.searchTerm || searchInfo?.selectedConversations.length > 0 || searchInfo?.selectedAuthors.length > 0;
            if (isSearchNonEmpty) {
                this.courseSidebarService.closeSidebar();
            } else {
                this.courseSidebarService.openSidebar();
            }
        }

        this.selectedSavedPostStatus = undefined;
        this.metisConversationService.setActiveConversation(undefined);
        this.activeConversation = undefined;
        this.updateQueryParameters();
        this.courseWideSearchConfig.searchTerm = searchInfo.searchTerm;
        this.courseWideSearchConfig.selectedConversations = searchInfo.selectedConversations;
        this.courseWideSearchConfig.selectedAuthors = searchInfo.selectedAuthors;
        this.courseWideSearch()?.onSearch();
    }

    onSelectionChange(searchInfo: ConversationGlobalSearchConfig) {
        if ((searchInfo.selectedConversations.length > 0 || searchInfo.selectedAuthors.length > 0) && this.activeConversation && !this.previousConversationBeforeSearch) {
            this.previousConversationBeforeSearch = this.activeConversation;
        }

        this.courseWideSearchConfig.selectedConversations = searchInfo.selectedConversations;
        this.courseWideSearchConfig.selectedAuthors = searchInfo.selectedAuthors;
        this.courseWideSearch()?.onSearchConfigSelectionChange();
    }

    onClearSearchAndRestorePrevious() {
        this.courseWideSearchConfig.searchTerm = '';
        this.courseWideSearchConfig.selectedConversations = [];
        this.courseWideSearchConfig.selectedAuthors = [];

        if (this.previousConversationBeforeSearch?.id) {
            this.metisConversationService.setActiveConversation(this.previousConversationBeforeSearch.id);
        } else if (this.lastKnownConversationId) {
            this.metisConversationService.setActiveConversation(this.lastKnownConversationId);
        } else {
            this.selectedSavedPostStatus = undefined;
            this.metisConversationService.setActiveConversation(undefined);
            this.activeConversation = undefined;
            this.updateQueryParameters();
            this.courseWideSearch()?.onSearch();
        }

        this.previousConversationBeforeSearch = undefined;
        this.closeSidebarOnMobile();
    }

    /**
     * Refreshes and prepares the sidebar data used for rendering channel and chat groups.
     *
     * - Forces MetisConversationService to refresh data (e.g., conversations).
     * - Maps the latest conversations to sidebar card elements.
     * - Regroups conversations under their respective accordion sections (e.g., general, exercise).
     * - Sets the 'recents' section based on the currently open conversation.
     * - Calls updateSidebarData() to finalize the sidebarData structure.
     */
    prepareSidebarData() {
        this.metisConversationService.forceRefresh().subscribe({
            complete: () => {
                this.sidebarConversations = this.courseOverviewService.mapConversationsToSidebarCardElements(this.course()!, this.conversationsOfUser);
                this.accordionConversationGroups = this.courseOverviewService.groupConversationsByChannelType(this.course()!, this.conversationsOfUser, this.messagingEnabled);
                this.accordionConversationGroups.recents.entityData = this.sidebarConversations?.filter((item) => item.isCurrent) || [];
                this.updateSidebarData();
            },
        });
    }

    updateSidebarData() {
        this.sidebarData = {
            groupByCategory: true,
            sidebarType: 'conversation',
            storageId: 'conversation',
            groupedData: this.accordionConversationGroups,
            ungroupedData: this.sidebarConversations,
            showAccordionLeadingIcon: true,
            messagingEnabled: isMessagingEnabled(this.course()),
            canCreateChannel: canCreateChannel(this.course()!),
        };
    }

    // NOTE: conversationId can either be a number or a string according to SavedPostStatus (for saved post status)
    onConversationSelected(conversationId: number | string) {
        this.closeSidebarOnMobile();
        this.focusPostId = undefined;
        this.openThreadOnFocus = false;
        if (typeof conversationId === 'string') {
            if (
                Object.values(SavedPostStatus)
                    .map((status) => status.toString().toLowerCase())
                    .includes(conversationId)
            ) {
                this.selectedSavedPostStatus = conversationId.toUpperCase() as SavedPostStatus;
                this.postInThread = undefined;
                this.metisConversationService.setActiveConversation(undefined);
                this.activeConversation = undefined;
                this.updateQueryParameters();
                this.metisService.resetCachedPosts();
                this.changeDetector.detectChanges();
            }
        } else {
            conversationId = +conversationId;
            this.selectedSavedPostStatus = undefined;
            this.metisConversationService.setActiveConversation(conversationId);
        }
    }

    toggleSidebar() {
        this.setIsCollapsed(!this.isCollapsed);
    }

    closeSidebarOnMobile() {
        if (this.isMobile()) {
            this.courseSidebarService.closeSidebar();
        }
    }

    setIsCollapsed(value: boolean) {
        this.isCollapsed = value;
        this.courseOverviewService.setSidebarCollapseState('conversation', this.isCollapsed);
    }

    openCreateGroupChatDialog() {
        const ref = this.dialogService.open(GroupChatCreateDialogComponent, {
            ...defaultFirstLayerDialogOptions,
            data: { course: this.course() },
        });
        ref?.onClose
            .pipe(
                filter((result: UserPublicInfoDTO[] | undefined) => !!result),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe((chatPartners: UserPublicInfoDTO[]) => {
                this.metisConversationService.createGroupChat(chatPartners?.map((partner) => partner.login!)).subscribe({
                    complete: () => {
                        this.prepareSidebarData();
                    },
                });
            });
    }

    openCreateOneToOneChatDialog() {
        const ref = this.dialogService.open(OneToOneChatCreateDialogComponent, {
            ...defaultFirstLayerDialogOptions,
            data: { course: this.course() },
        });
        ref?.onClose
            .pipe(
                filter((result: UserPublicInfoDTO | undefined) => !!result),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe((chatPartner: UserPublicInfoDTO) => {
                if (chatPartner?.login) {
                    this.metisConversationService.createOneToOneChat(chatPartner.login).subscribe({
                        complete: () => {
                            this.prepareSidebarData();
                        },
                    });
                }
            });
    }

    /**
     * Opens the create channel dialog.
     * Emits a create action for the given channel on confirmation.
     */
    openCreateChannelDialog() {
        const ref = this.dialogService.open(ChannelsCreateDialogComponent, {
            ...defaultSecondLayerDialogOptions,
            data: { course: this.course() },
        });
        ref?.onClose
            .pipe(
                filter((result: ChannelDTO | undefined) => !!result),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe((channel: ChannelDTO) => {
                this.channelActions$.emit({ action: 'create', channel });
            });
    }

    markAllChannelAsRead() {
        this.metisConversationService.markAllChannelsAsRead(this.course()).subscribe({
            complete: () => {
                this.metisConversationService.forceRefresh().subscribe({
                    complete: () => {
                        this.prepareSidebarData();
                        this.closeSidebarOnMobile();
                    },
                });
            },
        });
    }

    openChannelOverviewDialog() {
        const subType = undefined;
        const ref = this.dialogService.open(ChannelsOverviewDialogComponent, {
            ...defaultFirstLayerDialogOptions,
            data: {
                course: this.course(),
                createChannelFn: subType === ChannelSubType.GENERAL ? this.metisConversationService.createChannel : undefined,
                channelSubType: subType,
            },
        });
        ref?.onClose
            .pipe(
                filter((result) => !!result),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe((result) => {
                const [newActiveConversation, isModificationPerformed] = result;
                if (isModificationPerformed) {
                    this.metisConversationService.forceRefresh(!newActiveConversation, true).subscribe({
                        complete: () => {
                            if (newActiveConversation) {
                                this.metisConversationService.setActiveConversation(newActiveConversation);
                                this.closeSidebarOnMobile();
                            }
                        },
                    });
                } else {
                    if (newActiveConversation) {
                        this.metisConversationService.setActiveConversation(newActiveConversation);
                        this.closeSidebarOnMobile();
                    }
                }
                this.prepareSidebarData();
            });
    }

    triggerSearchInConversation() {
        if (this.globalSearchComponent() && this.activeConversation) {
            this.globalSearchComponent()!.focusWithSelectedConversation(this.activeConversation);
        }
    }

    openThread(postToOpen: Post | undefined) {
        this.selectionState.setOpenPostId(postToOpen?.id);
        this.postInThread = postToOpen;
        this.pendingThreadPostId = undefined;
    }

    closeThread() {
        this.postInThread = undefined;
        this.pendingThreadPostId = undefined;
        this.updateQueryParameters();
    }

    private scrollToAndHighlightPost(postId: number): void {
        const elementId = 'item-' + postId;

        const tryHighlight = () => {
            const host = document.getElementById(elementId);
            const postDiv = host?.querySelector('.post');
            if (postDiv) {
                postDiv.scrollIntoView({ behavior: 'smooth', block: 'center' });
                postDiv.classList.add('highlight-post');
                setTimeout(() => postDiv.classList.remove('highlight-post'), 2000);
                return true;
            }
            return false;
        };

        if (tryHighlight()) return;

        const observer = new MutationObserver(() => {
            if (tryHighlight()) {
                observer.disconnect();
            }
        });
        observer.observe(document.body, { childList: true, subtree: true });
        setTimeout(() => observer.disconnect(), 5000);
    }

    private scrollToAndHighlightReply(replyId: number): void {
        const elementId = 'item-' + replyId;

        // Check if element already exists
        const existing = document.getElementById(elementId);
        if (existing) {
            this.highlightElement(existing);
            return;
        }

        // Watch for the element to appear in the DOM
        const observer = new MutationObserver(() => {
            const element = document.getElementById(elementId);
            if (element) {
                observer.disconnect();
                this.highlightElement(element);
            }
        });

        observer.observe(document.body, { childList: true, subtree: true });

        // Clean up after 5 seconds if element never appears
        setTimeout(() => {
            observer.disconnect();
            this.focusReplyId = undefined;
        }, 5000);
    }

    private highlightElement(element: HTMLElement): void {
        element.scrollIntoView({ behavior: 'smooth', block: 'center' });
        element.classList.add('highlight-reply');
        setTimeout(() => element.classList.remove('highlight-reply'), 2000);
        this.focusReplyId = undefined;
    }

    /**
     * Determines which post to focus and whether to open thread view,
     * then sets the active conversation accordingly.
     *
     * @param post The post or answer post being navigated to
     */
    onTriggerNavigateToPost(post: Posting) {
        let id = (post as Post)?.conversation?.id;
        this.focusPostId = post.id;
        this.openThreadOnFocus = false;
        if (post.id === undefined) {
            return;
        } else if ((post as Post)?.conversation?.id === undefined) {
            this.openThreadOnFocus = true;
            id = (post as AnswerPost)?.post?.conversation?.id;
            this.focusPostId = (post as AnswerPost)?.post?.id;
        }

        this.metisConversationService.setActiveConversation(id);
        this.changeDetector.detectChanges();
    }
    async enableCommunication(withMessaging = true) {
        const id = this.course()?.id;
        if (id) {
            try {
                await firstValueFrom(this.metisService.enable(id, withMessaging));
                const updatedCourse = {
                    ...this.course()!,
                    courseInformationSharingConfiguration: withMessaging
                        ? CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING
                        : CourseInformationSharingConfiguration.COMMUNICATION_ONLY,
                };
                this.course.set(updatedCourse);

                this.eventManager.broadcast({
                    name: 'courseModification',
                    content: 'Changed course communication settings',
                });
            } catch (error) {
                this.alertService.error('artemisApp.metis.communicationDisabled.enableError');
            }
        }
    }
}
