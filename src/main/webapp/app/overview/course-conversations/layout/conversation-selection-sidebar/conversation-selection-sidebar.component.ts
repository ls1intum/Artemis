import { AfterViewInit, ChangeDetectorRef, Component, OnDestroy, OnInit, QueryList, ViewChildren } from '@angular/core';
import interact from 'interactjs';
import { faChevronLeft, faChevronRight, faComments, faCompress, faExpand, faGripLinesVertical, faMagnifyingGlass, faPlus } from '@fortawesome/free-solid-svg-icons';

import { Subject, delay, from, map, startWith, takeUntil } from 'rxjs';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { Course } from 'app/entities/course.model';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ChannelsOverviewDialogComponent } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { ChannelsCreateDialogComponent } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channels-create-dialog.component';
import { Channel, ChannelDTO, isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { GroupChatDto, isGroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { canCreateChannel } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { AccountService } from 'app/core/auth/account.service';
import { OneToOneChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { OneToOneChatDTO, isOneToOneChatDto } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { GroupChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/group-chat-create-dialog/group-chat-create-dialog.component';
import { debounceTime, distinctUntilChanged, tap } from 'rxjs/operators';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { SidebarSectionComponent } from 'app/overview/course-conversations/layout/conversation-selection-sidebar/sidebar-section/sidebar-section.component';

interface SearchQuery {
    searchTerm: string;
    force: boolean;
}
@Component({
    selector: 'jhi-conversation-selection-sidebar',
    styleUrls: ['./conversation-selection-sidebar.component.scss'],
    templateUrl: './conversation-selection-sidebar.component.html',
})
export class ConversationSelectionSidebarComponent implements AfterViewInit, OnInit, OnDestroy {
    @ViewChildren(SidebarSectionComponent) sidebarSections: QueryList<SidebarSectionComponent>;
    collapseToggles: SidebarSectionComponent[] = [];
    private ngUnsubscribe = new Subject<void>();
    private readonly search$ = new Subject<SearchQuery>();
    searchTerm = '';

    canCreateChannel = canCreateChannel;

    course?: Course;

    activeConversation?: ConversationDto;
    allConversations: ConversationDto[] = [];
    starredConversations: ConversationDto[] = [];
    displayedStarredConversations: ConversationDto[] = [];
    channelConversations: ChannelDTO[] = [];
    displayedChannelConversations: ChannelDTO[] = [];
    oneToOneChats: OneToOneChatDTO[] = [];
    displayedOneToOneChats: OneToOneChatDTO[] = [];
    groupChats: GroupChatDto[] = [];
    displayedGroupChats: GroupChatDto[] = [];
    collapsed: boolean;
    // Icons
    faChevronLeft = faChevronLeft;
    faChevronRight = faChevronRight;
    faGripLinesVertical = faGripLinesVertical;
    faConversation = faComments;
    faPlus = faPlus;
    faMagnifyingGlass = faMagnifyingGlass;
    faExpand = faExpand;
    faCompress = faCompress;

    constructor(
        private modalService: NgbModal,
        private cdr: ChangeDetectorRef,
        // instantiated at course-conversation.component.ts
        public metisConversationService: MetisConversationService,
        public accountService: AccountService,
        public conversationService: ConversationService,
    ) {}

    ngOnInit(): void {
        this.course = this.metisConversationService.course;
        this.subscribeToSearch();
        this.subscribeToActiveConversation();
        this.subscribeToConversationsOfUser();
    }

    private subscribeToSearch() {
        this.search$
            .pipe(
                debounceTime(300),
                distinctUntilChanged((prev, curr) => {
                    if (curr.force === true) {
                        return false;
                    } else {
                        return prev === curr;
                    }
                }),
                tap(() => {
                    this.displayedStarredConversations = [];
                    this.displayedChannelConversations = [];
                    this.displayedOneToOneChats = [];
                    this.displayedGroupChats = [];
                }),
                map((query: SearchQuery) => {
                    const searchTerm = query.searchTerm !== null && query.searchTerm !== undefined ? query.searchTerm : '';
                    return searchTerm.trim().toLowerCase();
                }),
                tap((searchTerm: string) => {
                    this.searchTerm = searchTerm;
                }),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (searchTerm: string) => {
                    this.displayedStarredConversations = this.starredConversations.filter((conversation) => {
                        return this.conversationService.getConversationName(conversation).toLowerCase().includes(searchTerm);
                    });
                    this.displayedChannelConversations = this.channelConversations.filter((conversation) => {
                        return this.conversationService.getConversationName(conversation).toLowerCase().includes(searchTerm);
                    });
                    this.displayedOneToOneChats = this.oneToOneChats.filter((conversation) => {
                        return this.conversationService.getConversationName(conversation).toLowerCase().includes(searchTerm);
                    });
                    this.displayedGroupChats = this.groupChats.filter((conversation) => {
                        return this.conversationService.getConversationName(conversation).toLowerCase().includes(searchTerm);
                    });
                    this.cdr.detectChanges();
                },
            });
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    private subscribeToActiveConversation() {
        this.metisConversationService.activeConversation$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversation: ConversationDto) => {
            this.activeConversation = conversation;
        });
    }

    private subscribeToConversationsOfUser() {
        this.metisConversationService.conversationsOfUser$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversations: ConversationDto[]) => {
            this.onConversationsUpdate(conversations);
        });
    }

    onSearchQueryInput($event: Event) {
        const searchTerm = ($event.target as HTMLInputElement).value?.trim().toLowerCase() ?? '';
        this.search$.next({
            searchTerm,
            force: false,
        });
    }

    onConversationsUpdate(conversations: ConversationDto[]) {
        this.allConversations = conversations ?? [];
        this.starredConversations = this.allConversations
            .filter((conversation) => conversation.isFavorite)
            .sort((a, b) => {
                // sort by last message date
                const aLastMessageDate = a.lastMessageDate ? a.lastMessageDate : a.creationDate;
                const bLastMessageDate = b.lastMessageDate ? b.lastMessageDate : b.creationDate;
                // newest messages at the top of the list
                return bLastMessageDate!.isAfter(aLastMessageDate!) ? 1 : -1;
            });
        this.channelConversations = this.allConversations
            .filter((conversation) => isChannelDto(conversation) && !conversation.isFavorite)
            .map((channel) => channel as Channel)
            .sort((a, b) => a.name!.localeCompare(b.name!));
        this.oneToOneChats = this.allConversations
            .filter((conversation) => isOneToOneChatDto(conversation) && !conversation.isFavorite)
            .map((oneToOneChat) => oneToOneChat as OneToOneChatDTO)
            .sort((a, b) => {
                // sort by last message date
                const aLastMessageDate = a.lastMessageDate ? a.lastMessageDate : a.creationDate;
                const bLastMessageDate = b.lastMessageDate ? b.lastMessageDate : b.creationDate;
                // newest messages at the top of the list
                return bLastMessageDate!.isAfter(aLastMessageDate!) ? 1 : -1;
            });
        this.groupChats = this.allConversations
            .filter((conversation) => isGroupChatDto(conversation) && !conversation.isFavorite)
            .map((groupChatDto) => groupChatDto as GroupChatDto)
            .sort((a, b) => {
                // sort by last message date
                const aLastMessageDate = a.lastMessageDate ? a.lastMessageDate : a.creationDate;
                const bLastMessageDate = b.lastMessageDate ? b.lastMessageDate : b.creationDate;
                // newest messages at the top of the list
                return bLastMessageDate!.isAfter(aLastMessageDate!) ? 1 : -1;
            });
        this.search$.next({
            searchTerm: this.searchTerm,
            force: true,
        });
    }

    collectSections() {
        if (this.sidebarSections) {
            this.collapseToggles = this.sidebarSections.toArray();
        }
    }

    collapseAll() {
        this.collapseToggles.forEach((section) => {
            if (!section.isCollapsed) {
                section.toggleCollapsed();
            }
        });
    }

    expandAll() {
        this.collapseToggles.forEach((section) => {
            if (section.isCollapsed) {
                section.toggleCollapsed();
            }
        });
    }

    ngAfterViewInit(): void {
        this.sidebarSections.changes
            .pipe(
                startWith([undefined]), // to catch the initial value
                delay(0), // wait for all current async tasks to finish, which could change the query list using ngIf etc.
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(() => {
                this.collectSections();
            });

        // allows the conversation sidebar to be resized towards the right-hand side
        interact('.expanded-conversations')
            .resizable({
                edges: { left: false, right: '.draggable-right', bottom: false, top: false },
                modifiers: [
                    // Set maximum width of the conversation sidebar
                    interact.modifiers!.restrictSize({
                        min: { width: 230, height: 0 },
                        max: { width: 500, height: 4000 },
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

    onSettingsChanged() {
        this.metisConversationService
            .forceRefresh()
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe(() => {});
    }

    openCreateChannelDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ChannelsCreateDialogComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe((channelToCreate: ChannelDTO) => {
                this.metisConversationService.createChannel(channelToCreate).subscribe({
                    complete: () => {
                        this.metisConversationService.forceRefresh().subscribe(() => {});
                    },
                });
            });
    }
    openCreateGroupChatDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(GroupChatCreateDialogComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe((chatPartners: UserPublicInfoDTO[]) => {
                this.metisConversationService.createGroupChat(chatPartners?.map((partner) => partner.login!)).subscribe({
                    complete: () => {
                        this.metisConversationService.forceRefresh().subscribe(() => {});
                    },
                });
            });
    }
    openCreateOneToOneChatDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(OneToOneChatCreateDialogComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe((chatPartner: UserPublicInfoDTO) => {
                if (chatPartner?.login) {
                    this.metisConversationService.createOneToOneChat(chatPartner.login).subscribe({
                        complete: () => {
                            this.metisConversationService.forceRefresh().subscribe(() => {});
                        },
                    });
                }
            });
    }

    openChannelOverviewDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ChannelsOverviewDialogComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.createChannelFn = this.metisConversationService.createChannel;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe((newActiveConversation: ConversationDto) => {
                this.metisConversationService.forceRefresh().subscribe(() => {
                    if (newActiveConversation) {
                        this.metisConversationService.setActiveConversation(newActiveConversation);
                    }
                });
            });
    }

    onConversationSelected($event: ConversationDto) {
        this.metisConversationService.setActiveConversation($event);
    }
}
