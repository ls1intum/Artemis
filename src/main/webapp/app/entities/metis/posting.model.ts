import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';
import { Reaction } from 'app/entities/metis/reaction.model';
import { UserRole } from 'app/communication/metis.util';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';

export enum SavedPostStatus {
    PROGRESS = 0,
    COMPLETED = 1,
    ARCHIVED = 2,
}

export enum SavedPostStatusMap {
    PROGRESS = 'progress',
    COMPLETED = 'completed',
    ARCHIVED = 'archived',
}

export enum PostingType {
    POST = 0,
    ANSWER = 1,
}

export abstract class Posting implements BaseEntity {
    public id?: number;
    public referencePostId?: number;
    public author?: User;
    public authorRole?: UserRole;
    public creationDate?: dayjs.Dayjs;
    public updatedDate?: dayjs.Dayjs;
    public content?: string;
    public isSaved?: boolean;
    public savedPostStatus?: number;
    public postingType?: number;
    public reactions?: Reaction[];
    public hasForwardedMessages?: boolean = false;
    public isConsecutive?: boolean = false;
    public conversation?: Conversation;

    public static mapToStatus(map: SavedPostStatusMap) {
        switch (map) {
            case SavedPostStatusMap.COMPLETED:
                return SavedPostStatus.COMPLETED;
            case SavedPostStatusMap.ARCHIVED:
                return SavedPostStatus.ARCHIVED;
            default:
                return SavedPostStatus.PROGRESS;
        }
    }

    public static statusToMap(status: SavedPostStatus) {
        switch (status) {
            case SavedPostStatus.COMPLETED:
                return SavedPostStatusMap.COMPLETED;
            case SavedPostStatus.ARCHIVED:
                return SavedPostStatusMap.ARCHIVED;
            default:
                return SavedPostStatusMap.PROGRESS;
        }
    }
}
