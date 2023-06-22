import { Conversation, ConversationDto, ConversationType } from 'app/entities/metis/conversation/conversation.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { Exam } from 'app/entities/exam.model';

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
export class ChannelDTO extends ConversationDto {
    public subType?: ChannelSubType;
    public name?: string;
    public description?: string;
    public topic?: string;
    public isPublic?: boolean;
    public isAnnouncementChannel?: boolean;
    public isArchived?: boolean;
    public isChannelModerator?: boolean;
    public hasChannelModerationRights?: boolean;

    public tutorialGroupId?: number;

    public tutorialGroupTitle?: string;
    constructor() {
        super(ConversationType.CHANNEL);
    }
}
export function isChannelDto(conversation: ConversationDto): conversation is ChannelDTO {
    return conversation.type === ConversationType.CHANNEL;
}

export function isGeneralChannelDto(conversation: ConversationDto): conversation is ChannelDTO {
    return isChannelDto(conversation) && conversation.subType === ChannelSubType.GENERAL;
}

export function isExerciseChannelDto(conversation: ConversationDto): conversation is ChannelDTO {
    return isChannelDto(conversation) && conversation.subType === ChannelSubType.EXERCISE;
}

export function isLectureChannelDto(conversation: ConversationDto): conversation is ChannelDTO {
    return isChannelDto(conversation) && conversation.subType === ChannelSubType.LECTURE;
}

export function isExamChannelDto(conversation: ConversationDto): conversation is ChannelDTO {
    return isChannelDto(conversation) && conversation.subType === ChannelSubType.EXAM;
}

export function getAsChannelDto(conversation: ConversationDto | undefined): ChannelDTO | undefined {
    if (!conversation) {
        return undefined;
    }
    return isChannelDto(conversation) ? conversation : undefined;
}
