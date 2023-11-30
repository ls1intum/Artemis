import { Component, ContentChild, EventEmitter, Input, OnInit, Output, TemplateRef, ViewEncapsulation } from '@angular/core';
import { faChevronRight, faMessage } from '@fortawesome/free-solid-svg-icons';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { Course } from 'app/entities/course.model';
import { LocalStorageService } from 'ngx-webstorage';

@Component({
    selector: 'jhi-conversation-sidebar-section',
    templateUrl: './conversation-sidebar-section.component.html',
    styleUrls: ['./conversation-sidebar-section.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class ConversationSidebarSectionComponent implements OnInit {
    @Output() conversationSelected = new EventEmitter<ConversationDto>();
    @Output() settingsDidChange = new EventEmitter<void>();
    @Output() conversationIsFavoriteDidChange = new EventEmitter<void>();
    @Output() conversationIsHiddenDidChange = new EventEmitter<void>();
    @Output() conversationIsMutedDidChange = new EventEmitter<void>();

    @Input() label: string;
    @Input() course: Course;
    @Input() activeConversation?: ConversationDto;
    @Input() headerKey: string;
    @Input() searchTerm: string;
    @Input() hideIfEmpty = true;

    @Input() set conversations(conversations: ConversationDto[]) {
        this.hiddenConversations = [];
        this.mutedConversations = [];
        this.visibleConversations = [];
        this.allConversations = conversations ?? [];
        conversations.forEach((conversation) => {
            if (conversation.isHidden) {
                this.hiddenConversations.push(conversation);
            } else {
                if (conversation.isMuted && !conversation.isFavorite) {
                    this.mutedConversations.push(conversation);
                } else {
                    this.visibleConversations.push(conversation);
                }
            }
        });
        this.numberOfConversations = this.allConversations.length;
    }

    @ContentChild(TemplateRef) sectionButtons: TemplateRef<any>;

    readonly prefix = 'collapsed.';

    isCollapsed: boolean;
    isHiddenConversationListPresented = false;

    numberOfConversations = 0;
    allConversations: ConversationDto[] = [];
    visibleConversations: ConversationDto[] = [];
    mutedConversations: ConversationDto[] = [];
    hiddenConversations: ConversationDto[] = [];

    // icon imports
    faChevronRight = faChevronRight;
    faMessage = faMessage;

    constructor(
        public conversationService: ConversationService,
        public localStorageService: LocalStorageService,
    ) {}

    ngOnInit(): void {
        this.isCollapsed = !!this.localStorageService.retrieve(this.storageKey);
        this.localStorageService.store(this.storageKey, this.isCollapsed);
    }

    get storageKey() {
        return this.prefix + this.headerKey;
    }

    get anyConversationUnread(): boolean {
        // do not show unread badge for open conversation that the user is currently reading
        let containsUnreadConversation = false;
        for (const conversation of this.allConversations) {
            if (
                conversation.unreadMessagesCount &&
                conversation.unreadMessagesCount > 0 &&
                !(this.activeConversation && this.activeConversation.id === conversation.id) &&
                this.isCollapsed
            ) {
                containsUnreadConversation = true;
                break;
            }
        }
        return containsUnreadConversation;
    }

    get anyHiddenConversationUnread(): boolean {
        // do not show unread badge for open conversation that the user is currently reading
        let containsUnreadConversation = false;
        for (const conversation of this.hiddenConversations) {
            if (
                conversation.unreadMessagesCount &&
                conversation.unreadMessagesCount > 0 &&
                !(this.activeConversation && this.activeConversation.id === conversation.id) &&
                !this.isHiddenConversationListPresented
            ) {
                containsUnreadConversation = true;
                break;
            }
        }
        return containsUnreadConversation;
    }

    get isConversationListVisible() {
        return this.visibleConversations.length + this.mutedConversations.length > 0 || this.hiddenConversations.length > 0;
    }

    get isHiddenConversationListVisible() {
        return this.hiddenConversations && this.hiddenConversations.length > 0;
    }

    conversationsTrackByFn = (index: number, conversation: ConversationDto): number => conversation.id!;

    toggleCollapsed() {
        this.isCollapsed = !this.isCollapsed;
        this.localStorageService.store(this.storageKey, this.isCollapsed);
    }

    hide() {
        const noMatchesInSearch = this.searchTerm && this.searchTerm.length > 0 && !this.allConversations?.length;
        const emptyConversations = this.hideIfEmpty && !this.allConversations.length;
        return noMatchesInSearch || emptyConversations;
    }
}
