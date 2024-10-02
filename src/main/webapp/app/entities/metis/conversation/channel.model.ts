import { Conversation, ConversationDTO, ConversationType } from 'app/entities/metis/conversation/conversation.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { Exam } from 'app/entities/exam/exam.model';

// IMPORTANT NOTICE: The following strings have to be consistent with
// the types defined in ChannelSubType.java
export enum ChannelSubType {
    GENERAL = 'general',
    EXERCISE = 'exercise',
    LECTURE = 'lecture',
    EXAM = 'exam',
}

/**
 * Entity
 */
export class Channel extends Conversation {
    public name?: string; // max 30 characters
    public description?: string; // max 250 characters
    public topic?: string; // max 250 characters;
    public isPublic?: boolean;
    public isAnnouncementChannel?: boolean;
    public isArchived?: boolean;
    public isCourseWide?: boolean;

    public exercise?: Exercise;
    public lecture?: Lecture;
    public exam?: Exam;

    constructor() {
        super(ConversationType.CHANNEL);
    }
}

/**
 * DTO
 */
export class ChannelDTO extends ConversationDTO {
    public subType?: ChannelSubType;
    public subTypeReferenceId?: number;
    public name?: string;
    public description?: string;
    public topic?: string;
    public isPublic?: boolean;
    public isAnnouncementChannel?: boolean;
    public isArchived?: boolean;
    public isChannelModerator?: boolean;
    public hasChannelModerationRights?: boolean;
    public isCourseWide?: boolean;

    public tutorialGroupId?: number;

    public tutorialGroupTitle?: string;

    constructor() {
        super(ConversationType.CHANNEL);
    }
}

/**
 * A DTO representing a channel which contains only the id and name
 */
export class ChannelIdAndNameDTO {
    public id?: number;
    public name?: string;
}

export function isChannelDTO(conversation: ConversationDTO | Conversation): conversation is ChannelDTO {
    return conversation.type === ConversationType.CHANNEL;
}

export function getAsChannelDTO(conversation: ConversationDTO | Conversation | undefined): ChannelDTO | undefined {
    if (!conversation) {
        return undefined;
    }
    return isChannelDTO(conversation) ? conversation : undefined;
}
