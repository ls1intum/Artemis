import { BaseEntity } from 'app/shared/model/base-entity';

export enum AchievementRank {
    UNRANKED = 'UNRANKED',
    BRONZE = 'BRONZE',
    SILVER = 'SILVER',
    GOLD = 'GOLD',
}

export enum AchievementType {
    POINT,
    TIME,
    PROGRESS,
}

export class Achievement implements BaseEntity {
    id?: number;
    title?: string;
    description?: string;
    icon?: string;
    rank?: AchievementRank;
    type?: AchievementType;

    constructor() {}
}
