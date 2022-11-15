import { Component, ContentChild, EventEmitter, Input, Output, TemplateRef } from '@angular/core';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-sidebar-section',
    templateUrl: './sidebar-section.component.html',
    styleUrls: ['./sidebar-section.component.scss'],
})
export class SidebarSectionComponent {
    @Output() conversationSelected = new EventEmitter<ConversationDto>();

    @Output()
    settingsChanged = new EventEmitter<void>();

    @Input()
    label: string;

    @Input()
    course: Course;

    @Input()
    activeConversation?: ConversationDto;

    @Input()
    conversations: ConversationDto[] = [];

    @Input()
    isCollapsed = false;
    @ContentChild(TemplateRef) sectionButtons: TemplateRef<any>;

    getAsChannel = getAsChannelDto;
    getConversationName = this.conversationService.getConversationName;

    // icon imports
    faChevronRight = faChevronRight;

    constructor(public conversationService: ConversationService) {}

    conversationsTrackByFn = (index: number, conversation: ConversationDto): number => conversation.id!;

    isConversationUnread(conversation: ConversationDto): boolean {
        // ToDo: Refactor as we do not have participants for course-wide conversations (dto or transient property)
        //
        // const conversationParticipant = conversation.conversationParticipants!.find(
        //     (conversationParticipants) => conversationParticipants.user.id === this.courseMessagesService.userId,
        // )!;
        //
        // if (this.activeConversation && conversation.id !== this.activeConversation.id && !!conversation.lastMessageDate && !!conversationParticipant.lastRead) {
        //     if (conversationParticipant.lastRead.isBefore(conversation.lastMessageDate.subtract(1, 'second'), 'second')) {
        //         return true;
        //     }
        // }
        return false;
    }
}
