import { AfterViewInit, Component, OnInit } from '@angular/core';
import interact from 'interactjs';
import { faChevronLeft, faChevronRight, faComments, faGripLinesVertical, faPlus } from '@fortawesome/free-solid-svg-icons';

import { from } from 'rxjs';
import { createUserPublicInfoDTOFromUser, User, UserPublicInfoDTO } from 'app/core/user/user.model';
import { Course } from 'app/entities/course.model';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ChannelsOverviewDialogComponent } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { ChannelsCreateDialogComponent } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channels-create-dialog.component';
import { Channel, ChannelDTO, isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { GroupChat, GroupChatDto, isGroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { canCreateChannel } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { AccountService } from 'app/core/auth/account.service';
import { OneToOneChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { isOneToOneChatDto, OneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';

@Component({
    selector: 'jhi-conversation-selection-sidebar',
    styleUrls: ['./conversation-selection-sidebar.component.scss'],
    templateUrl: './conversation-selection-sidebar.component.html',
})
export class ConversationSelectionSidebarComponent implements AfterViewInit, OnInit {
    canCreateChannel = canCreateChannel;

    course?: Course;
    activeConversation?: ConversationDto;
    allConversations: ConversationDto[] = [];
    starredConversations: ConversationDto[] = [];
    channelConversations: ChannelDTO[] = [];
    oneToOneChats: OneToOneChatDTO[] = [];

    collapsed: boolean;
    isLoading = false;
    isSearching = false;
    searchFailed = false;
    searchNoResults = false;
    isTransitioning = false;

    // Icons
    faChevronLeft = faChevronLeft;
    faChevronRight = faChevronRight;
    faGripLinesVertical = faGripLinesVertical;
    faConversation = faComments;
    faPlus = faPlus;

    constructor(
        private modalService: NgbModal,
        // instantiated at course-conversation.component.ts
        public metisConversationService: MetisConversationService,
        public accountService: AccountService,
    ) {}

    ngOnInit(): void {
        this.course = this.metisConversationService.course;
        this.subscribeToActiveConversation();
        this.subscribeToConversationsOfUser();
    }

    private subscribeToActiveConversation() {
        this.metisConversationService.activeConversation$.subscribe((conversation: ConversationDto) => {
            this.activeConversation = conversation;
        });
    }

    private subscribeToConversationsOfUser() {
        this.metisConversationService.conversationsOfUser$.subscribe((conversations: ConversationDto[]) => {
            this.onConversationsUpdate(conversations);
        });
    }

    onConversationsUpdate(conversations: ConversationDto[]) {
        this.allConversations = conversations ?? [];
        this.channelConversations = this.allConversations
            .filter((conversation) => isChannelDto(conversation))
            .map((channel) => channel as Channel)
            .sort((a, b) => a.name!.localeCompare(b.name!));
        this.oneToOneChats = this.allConversations
            .filter((conversation) => isOneToOneChatDto(conversation))
            .map((oneToOneChat) => oneToOneChat as OneToOneChatDTO)
            .sort((a, b) => {
                // sort by last message date
                const aLastMessageDate = a.lastMessageDate ? a.lastMessageDate : a.creationDate;
                const bLastMessageDate = b.lastMessageDate ? b.lastMessageDate : b.creationDate;
                // newest messages at the top of the list
                return bLastMessageDate!.isAfter(aLastMessageDate!) ? 1 : -1;
            });
    }

    ngAfterViewInit(): void {
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

    openCreateChannelDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ChannelsCreateDialogComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.initialize();
        from(modalRef.result).subscribe((channelToCreate: ChannelDTO) => {
            this.metisConversationService.createNewConversation(channelToCreate).subscribe({
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
        from(modalRef.result).subscribe((userToChatWith: UserPublicInfoDTO | undefined) => {
            if (userToChatWith) {
                this.accountService.identity().then((user: User) => {
                    const currentUser = createUserPublicInfoDTOFromUser(user!);
                    const creationDTO = new OneToOneChatDTO();
                    creationDTO.members = [currentUser, userToChatWith];
                    this.metisConversationService.createNewConversation(creationDTO).subscribe({
                        complete: () => {
                            this.metisConversationService.forceRefresh().subscribe(() => {});
                        },
                    });
                });
            }
        });
    }

    openChannelOverviewDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ChannelsOverviewDialogComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.createChannelFn = this.metisConversationService.createNewConversation;
        modalRef.componentInstance.initialize();
        from(modalRef.result).subscribe((newActiveConversation: ConversationDto) => {
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
