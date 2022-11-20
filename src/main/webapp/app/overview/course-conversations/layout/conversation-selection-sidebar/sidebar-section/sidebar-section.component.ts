import { Component, ContentChild, EventEmitter, Input, OnInit, Output, TemplateRef } from '@angular/core';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { Course } from 'app/entities/course.model';
import { LocalStorageService } from 'ngx-webstorage';

@Component({
    selector: 'jhi-sidebar-section',
    templateUrl: './sidebar-section.component.html',
    styleUrls: ['./sidebar-section.component.scss'],
})
export class SidebarSectionComponent implements OnInit {
    @Output() conversationSelected = new EventEmitter<ConversationDto>();

    @Output()
    settingsChanged = new EventEmitter<void>();

    @Input()
    label: string;

    @Input()
    course: Course;

    @Input()
    activeConversation?: ConversationDto;

    @Input() headerKey: string;

    readonly prefix = 'collapsed.';

    @Input()
    set conversations(conversations: ConversationDto[]) {
        this.hiddenConversations = [];
        this.visibleConversations = [];
        conversations.forEach((conversation) => {
            if (conversation.isHidden) {
                this.hiddenConversations.push(conversation);
            } else {
                this.visibleConversations.push(conversation);
            }
        });
        this.numberOfConversations = this.visibleConversations.length + this.hiddenConversations.length;
    }

    isCollapsed: boolean;
    @ContentChild(TemplateRef) sectionButtons: TemplateRef<any>;
    toggleCollapsed() {
        this.isCollapsed = !this.isCollapsed;
        this.localStorageService.store(this.storageKey, this.isCollapsed);
    }

    hiddenConversations: ConversationDto[] = [];
    visibleConversations: ConversationDto[] = [];
    numberOfConversations = 0;

    getAsChannel = getAsChannelDto;
    getConversationName = this.conversationService.getConversationName;

    // icon imports
    faChevronRight = faChevronRight;

    constructor(public conversationService: ConversationService, private localStorageService: LocalStorageService) {}

    conversationsTrackByFn = (index: number, conversation: ConversationDto): number => conversation.id!;
    showHiddenConversations = false;

    ngOnInit(): void {
        this.isCollapsed = !!this.localStorageService.retrieve(this.storageKey);
        this.localStorageService.store(this.storageKey, this.isCollapsed);
    }

    get storageKey() {
        return this.prefix + this.headerKey;
    }
}
