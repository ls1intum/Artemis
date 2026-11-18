import { User } from 'app/account/user/user.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { BaseEntity } from 'app/foundation/model/base-entity';
import dayjs from 'dayjs/esm';

export enum TeamImportStrategyType {
    PURGE_EXISTING = 'PURGE_EXISTING',
    CREATE_ONLY = 'CREATE_ONLY',
}

export interface OnlineTeamStudent {
    login: string;
    lastTypingDate: dayjs.Dayjs;
    lastActionDate: dayjs.Dayjs;
}

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class TeamAssignmentPayload {
    public exerciseId!: number;
    public teamId?: number;
    public studentParticipations!: StudentParticipation[];
}

export class Team implements BaseEntity {
    public id?: number;
    public name?: string;
    public shortName?: string;
    public image?: string;
    public students?: User[];
    public owner?: User;

    public createdBy?: string;
    public createdDate?: dayjs.Dayjs;
    public lastModifiedBy?: string;
    public lastModifiedDate?: dayjs.Dayjs;

    constructor() {
        this.students = []; // default value
    }
}

/**
 * This class is used for importing teams from a file
 * Either registration number or login must be set
 * Additionally student has teamName to identify which team they belong to
 */
export interface StudentWithTeam {
    firstName?: string;
    lastName?: string;
    registrationNumber?: string;
    teamName: string;
    username?: string;
}
