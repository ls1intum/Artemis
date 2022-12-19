import { User } from 'app/core/user/user.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';

export enum TeamImportStrategyType {
    PURGE_EXISTING = 'PURGE_EXISTING',
    CREATE_ONLY = 'CREATE_ONLY',
}

export class OnlineTeamStudent {
    public login: string;
    public lastTypingDate: dayjs.Dayjs;
    public lastActionDate: dayjs.Dayjs;
}

export class TeamAssignmentPayload {
    public exerciseId: number;
    public teamId?: number;
    public studentParticipations: StudentParticipation[];
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
 * Additionally student has teamName to identify which team he/she belongs to
 */
export class StudentWithTeam {
    public firstName?: string;
    public lastName?: string;
    public registrationNumber?: string;
    public teamName: string;
    public username?: string;
}
