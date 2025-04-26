import { NgClass } from '@angular/common';
import { ChangeDetectorRef, Component, ElementRef, EventEmitter, HostListener, OnDestroy, OnInit, ViewEncapsulation, inject, output, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
    faBookmark,
    faBoxArchive,
    faClock,
    faComment,
    faComments,
    faFile,
    faFilter,
    faGraduationCap,
    faHeart,
    faList,
    faMessage,
    faPersonChalkboard,
    faPlus,
    faSearch,
    faTimes,
} from '@fortawesome/free-solid-svg-icons';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { Course, isMessagingEnabled } from 'app/core/course/shared/entities/course.model';
import { ChannelDTO, ChannelSubType, getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { Posting, PostingType, SavedPostStatus, toSavedPostStatus } from 'app/communication/shared/entities/posting.model';
import { CourseWideSearchComponent, CourseWideSearchConfig } from 'app/communication/course-conversations-components/course-wide-search/course-wide-search.component';
import { ChannelsCreateDialogComponent } from 'app/communication/course-conversations-components/dialogs/channels-create-dialog/channels-create-dialog.component';
import { GroupChatCreateDialogComponent } from 'app/communication/course-conversations-components/group-chat-create-dialog/group-chat-create-dialog.component';
import { OneToOneChatCreateDialogComponent } from 'app/communication/course-conversations-components/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { defaultFirstLayerDialogOptions, defaultSecondLayerDialogOptions } from 'app/communication/course-conversations-components/other/conversation.util';
import { CourseOverviewService } from 'app/core/course/overview/services/course-overview.service';
import { CourseSidebarService } from 'app/core/course/overview/services/course-sidebar.service';
import { CustomBreakpointNames } from 'app/shared/breakpoints/breakpoints.service';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { ButtonComponent, ButtonType } from 'app/shared/components/button/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MetisService } from 'app/communication/service/metis.service';
import { PageType, SortDirection } from 'app/communication/metis.util';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { EMPTY, Observable, Subject, Subscription, from, take, takeUntil } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, filter } from 'rxjs/operators';
import { CourseConversationsCodeOfConductComponent } from 'app/communication/course-conversations-components/code-of-conduct/course-conversations-code-of-conduct.component';
import { ConversationHeaderComponent } from 'app/communication/course-conversations-components/layout/conversation-header/conversation-header.component';
import { ConversationMessagesComponent } from 'app/communication/course-conversations-components/layout/conversation-messages/conversation-messages.component';
import { ConversationThreadSidebarComponent } from 'app/communication/course-conversations-components/layout/conversation-thread-sidebar/conversation-thread-sidebar.component';
import { SavedPostsComponent } from 'app/communication/course-conversations-components/saved-posts/saved-posts.component';
import { captureException } from '@sentry/angular';
import { canCreateChannel } from 'app/communication/conversations/conversation-permissions.utils';
import {
    ChannelAction,
    ChannelsOverviewDialogComponent,
} from 'app/communication/course-conversations-components/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { AccordionGroups, ChannelTypeIcons, CollapseState, SidebarCardElement, SidebarData, SidebarItemShowAlways } from 'app/shared/types/sidebar';
import { LinkifyService } from 'app/communication/link-preview/services/linkify.service';
import { LinkPreviewService } from 'app/communication/link-preview/services/link-preview.service';

const DEFAULT_CHANNEL_GROUPS: AccordionGroups = {
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
    styleUrls: ['../../../core/course/overview/course-overview/course-overview.scss', './course-conversations.component.scss'],
    encapsulation: ViewEncapsulation.None,
    providers: [MetisService, LinkifyService, LinkPreviewService],
    imports: [
        LoadingIndicatorContainerComponent,
        FormsModule,
        ButtonComponent,
        CourseConversationsCodeOfConductComponent,
        TranslateDirective,
        NgClass,
        SidebarComponent,
        ConversationHeaderComponent,
        ConversationMessagesComponent,
        CourseWideSearchComponent,
        SavedPostsComponent,
        ConversationThreadSidebarComponent,
        ArtemisTranslatePipe,
    ],
})
export class CourseConversationsComponent implements OnInit, OnDestroy {
    private router = inject(Router);
    private activatedRoute = inject(ActivatedRoute);
    metisConversationService = inject(MetisConversationService);
    private metisService = inject(MetisService);
    private courseOverviewService = inject(CourseOverviewService);
    private modalService = inject(NgbModal);
    private profileService = inject(ProfileService);

    private ngUnsubscribe = new Subject<void>();
    private closeSidebarEventSubscription: Subscription;
    private openSidebarEventSubscription: Subscription;
    private toggleSidebarEventSubscription: Subscription;
    private breakpointSubscription: Subscription;
    course?: Course;
    isLoading = false;
    isServiceSetUp = false;
    messagingEnabled = false;
    postInThread?: Post;
    activeConversation?: ConversationDTO = undefined;
    conversationsOfUser: ConversationDTO[] = [];
    channelSearchCollapsed = true;

    conversationSelected = true;
    sidebarData: SidebarData;
    accordionConversationGroups: AccordionGroups;
    sidebarConversations: SidebarCardElement[] = [];
    isCollapsed = false;
    isProduction = true;
    isTestServer = false;
    isMobile = false;
    focusPostId: number | undefined = undefined;
    openThreadOnFocus = false;
    selectedSavedPostStatus: undefined | SavedPostStatus = undefined;
    showOnlyPinned = false;
    pinnedCount: number = 0;

    readonly CHANNEL_TYPE_ICON = CHANNEL_TYPE_ICON;
    readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;
    protected readonly DEFAULT_SHOW_ALWAYS = DEFAULT_SHOW_ALWAYS;

    // set undefined so nothing gets displayed until isCodeOfConductAccepted is loaded
    isCodeOfConductAccepted?: boolean;
    isCodeOfConductPresented = false;

    courseWideSearch = viewChild<CourseWideSearchComponent>(CourseWideSearchComponent);
    searchElement = viewChild<ElementRef>('courseWideSearchInput');
    highlightAnswerId = output<number>();
    answerId?: number;

    courseWideSearchConfig: CourseWideSearchConfig;
    courseWideSearchTerm = '';
    readonly ButtonType = ButtonType;

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faFilter = faFilter;
    faSearch = faSearch;

    createChannelFn?: (channel: ChannelDTO) => Observable<never>;
    channelActions$ = new EventEmitter<ChannelAction>();

    private courseSidebarService: CourseSidebarService = inject(CourseSidebarService);
    private layoutService: LayoutService = inject(LayoutService);
    private changeDetector: ChangeDetectorRef = inject(ChangeDetectorRef);

    getAsChannel = getAsChannelDTO;

    private subscribeToMetis() {
        this.metisService.posts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((posts: Post[]) => {
            if (this.postInThread?.id && posts) {
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

    onHighlightAnswerIdChanged(answerId: number | undefined): void {
        this.answerId = answerId;
        this.changeDetector.detectChanges();
    }

    private setupMetis() {
        this.metisService.setPageType(PageType.OVERVIEW);
        this.metisService.setCourse(this.course!);
    }

    ngOnInit(): void {
        this.isMobile = this.layoutService.isBreakpointActive(CustomBreakpointNames.extraSmall);

        this.breakpointSubscription = this.layoutService.subscribeToLayoutChanges().subscribe(() => {
            this.isMobile = this.layoutService.isBreakpointActive(CustomBreakpointNames.extraSmall);
        });

        this.openSidebarEventSubscription = this.courseSidebarService.openSidebar$.subscribe(() => {
            this.setIsCollapsed(true);
        });

        this.closeSidebarEventSubscription = this.courseSidebarService.closeSidebar$.subscribe(() => {
            this.setIsCollapsed(false);
        });

        this.toggleSidebarEventSubscription = this.courseSidebarService.toggleSidebar$.subscribe(() => {
            this.toggleSidebar();
        });

        if (!this.isMobile) {
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
                this.course = this.metisConversationService.course;
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
                this.metisConversationService.checkIsCodeOfConductAccepted(this.course!);
                this.isServiceSetUp = true;
                this.isLoading = false;
            }
            this.channelActions$
                .pipe(
                    debounceTime(500),
                    distinctUntilChanged(
                        (prev, curr) => curr.action !== 'create' && prev.action === curr.action && prev.channel.id === curr.channel.id && prev.channel.name === curr.channel.name,
                    ),
                    takeUntil(this.ngUnsubscribe),
                )
                .subscribe((channelAction) => {
                    this.performChannelAction(channelAction);
                });
            this.createChannelFn = (channel: ChannelDTO) => this.metisConversationService.createChannel(channel);
        });

        this.isProduction = this.profileService.isProduction();
        this.isTestServer = this.profileService.isTestServer();
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
        this.activatedRoute.queryParams.pipe(take(1), takeUntil(this.ngUnsubscribe)).subscribe((queryParams) => {
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
            if (queryParams.focusPostId) {
                this.focusPostId = Number(queryParams.focusPostId);
            }
            if (queryParams.openThreadOnFocus) {
                this.openThreadOnFocus = queryParams.openThreadOnFocus;
            }
            if (queryParams.messageId) {
                this.postInThread = { id: Number(queryParams.messageId) } as Post;

                this.closeSidebarOnMobile();
            } else {
                this.postInThread = undefined;
            }
        });
    }

    onNavigateToPost(post: Posting) {
        if (post.referencePostId === undefined || post.conversation?.id === undefined) {
            return;
        }
        this.openThreadOnFocus = (post.postingType as PostingType) === PostingType.ANSWER;
        this.metisConversationService.setActiveConversation(post.conversation!.id!);

        this.metisConversationService.activeConversation$
            .pipe(
                filter((conv) => conv?.id === post.conversation!.id!),
                take(1),
            )
            .subscribe(() => {
                if (post.isSaved) {
                    this.focusPostId = post.referencePostId;
                } else if ((post.postingType as PostingType) === PostingType.POST) {
                    this.focusPostId = post.referencePostId;
                } else {
                    this.focusPostId = (post as AnswerPost).post!.id;
                }
                setTimeout(() => {
                    this.focusPostId = undefined;
                    this.changeDetector.detectChanges();
                }, 1000);
            });
    }

    updateQueryParameters() {
        this.router.navigate([], {
            relativeTo: this.activatedRoute,
            queryParams: {
                conversationId: this.activeConversation?.id ?? this.selectedSavedPostStatus?.toLowerCase(),
            },
            replaceUrl: true,
        });
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.openSidebarEventSubscription?.unsubscribe();
        this.closeSidebarEventSubscription?.unsubscribe();
        this.toggleSidebarEventSubscription?.unsubscribe();
        this.breakpointSubscription?.unsubscribe();
    }

    private subscribeToActiveConversation() {
        this.metisConversationService.activeConversation$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversation: ConversationDTO) => {
            const previousConversation = this.activeConversation;
            this.activeConversation = conversation;
            if (this.isMobile && conversation && previousConversation?.id !== conversation.id) {
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
        if (this.course) {
            this.metisConversationService.acceptCodeOfConduct(this.course);
        }
    }

    initializeCourseWideSearchConfig() {
        this.courseWideSearchConfig = new CourseWideSearchConfig();
        this.courseWideSearchConfig.searchTerm = '';
        this.courseWideSearchConfig.filterToCourseWide = true;
        this.courseWideSearchConfig.filterToUnresolved = false;
        this.courseWideSearchConfig.filterToOwn = false;
        this.courseWideSearchConfig.filterToAnsweredOrReacted = false;
        this.courseWideSearchConfig.sortingOrder = SortDirection.ASCENDING;
    }

    initializeSidebarAccordions() {
        this.messagingEnabled = isMessagingEnabled(this.course);
        this.accordionConversationGroups = this.messagingEnabled
            ? { ...DEFAULT_CHANNEL_GROUPS, groupChats: { entityData: [] }, directMessages: { entityData: [] } }
            : DEFAULT_CHANNEL_GROUPS;
    }

    hideSearchTerm() {
        this.courseWideSearchTerm = '';
    }

    onSearch() {
        if (this.isMobile) {
            if (this.courseWideSearchTerm) {
                this.courseSidebarService.closeSidebar();
            } else {
                this.courseSidebarService.openSidebar();
            }
        }
        this.selectedSavedPostStatus = undefined;
        this.metisConversationService.setActiveConversation(undefined);
        this.activeConversation = undefined;
        this.updateQueryParameters();
        this.courseWideSearchConfig.searchTerm = this.courseWideSearchTerm;
        this.courseWideSearch()?.onSearch();
    }

    prepareSidebarData() {
        this.metisConversationService.forceRefresh().subscribe({
            complete: () => {
                this.sidebarConversations = this.courseOverviewService.mapConversationsToSidebarCardElements(this.course!, this.conversationsOfUser);
                this.accordionConversationGroups = this.courseOverviewService.groupConversationsByChannelType(this.course!, this.conversationsOfUser, this.messagingEnabled);
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
            messagingEnabled: isMessagingEnabled(this.course),
            canCreateChannel: canCreateChannel(this.course!),
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
        if (this.isMobile) {
            this.courseSidebarService.closeSidebar();
        }
    }

    setIsCollapsed(value: boolean) {
        this.isCollapsed = value;
        this.courseOverviewService.setSidebarCollapseState('conversation', this.isCollapsed);
    }

    openCreateGroupChatDialog() {
        const modalRef: NgbModalRef = this.modalService.open(GroupChatCreateDialogComponent, defaultFirstLayerDialogOptions);
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
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
        const modalRef: NgbModalRef = this.modalService.open(OneToOneChatCreateDialogComponent, defaultFirstLayerDialogOptions);
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
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

    openCreateChannelDialog() {
        const modalRef: NgbModalRef = this.modalService.open(ChannelsCreateDialogComponent, defaultSecondLayerDialogOptions);
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe((channel: ChannelDTO) => {
                this.channelActions$.emit({ action: 'create', channel });
            });
    }

    markAllChannelAsRead() {
        this.metisConversationService.markAllChannelsAsRead(this.course).subscribe({
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
        const modalRef: NgbModalRef = this.modalService.open(ChannelsOverviewDialogComponent, defaultFirstLayerDialogOptions);
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.createChannelFn = subType === ChannelSubType.GENERAL ? this.metisConversationService.createChannel : undefined;
        modalRef.componentInstance.channelSubType = subType;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
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

    toggleChannelSearch() {
        this.channelSearchCollapsed = !this.channelSearchCollapsed;
    }

    openThread(postToOpen: Post | undefined) {
        this.postInThread = postToOpen;
    }

    @HostListener('document:keydown', ['$event'])
    handleSearchShortcut(event: KeyboardEvent) {
        if ((event.metaKey || event.ctrlKey) && event.key === 'k') {
            event.preventDefault();
            this.searchElement()!.nativeElement.focus();
        }
    }

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
}
