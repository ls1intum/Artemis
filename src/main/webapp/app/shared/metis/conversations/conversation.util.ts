import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { isGroupChatDto } from 'app/entities/metis/conversation/groupChat.model';

export function getConversationName(conversation: ConversationDto): string {
    if (!conversation) {
        return '';
    }
    if (isChannelDto(conversation)) {
        return conversation.name ?? '';
    } else if (isGroupChatDto(conversation)) {
        const namesOfOtherMembers = conversation.namesOfOtherMembers ?? [];
        if (namesOfOtherMembers.length === 0) {
            return '';
        } else if (namesOfOtherMembers.length === 1) {
            return namesOfOtherMembers[0];
        } else if (namesOfOtherMembers.length === 2) {
            return `${namesOfOtherMembers[0]}, ${namesOfOtherMembers[1]}`;
        } else {
            return (
                `${namesOfOtherMembers[0]}, ${namesOfOtherMembers[1]}, ` +
                this.translationService.instant('artemisApp.messages.conversation.others', { count: namesOfOtherMembers.length - 2 })
            );
        }
    } else {
        return '';
    }
}
