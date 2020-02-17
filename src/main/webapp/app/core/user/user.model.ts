import { Account } from 'app/core/user/account.model';
import { Agent } from 'app/entities/participation/agent.model';
import { Moment } from 'moment';
import { Team } from 'app/entities/team/team.model';

export class User extends Account implements Agent {
    public id: number | null;
    public groups: string[] | null;
    public createdBy: string | null;
    public createdDate: Date | null;
    public lastModifiedBy: string | null;
    public lastModifiedDate: Date | null;
    public lastNotificationRead: Moment | null;
    public visibleRegistrationNumber: string | null;
    public password: string | null;

    constructor(
        id?: number,
        login?: string,
        username?: string,
        firstName?: string,
        lastName?: string,
        email?: string,
        activated?: boolean,
        langKey?: string,
        authorities?: string[],
        groups?: string[],
        createdBy?: string,
        createdDate?: Date,
        lastModifiedBy?: string,
        lastModifiedDate?: Date,
        lastNotificationRead?: Moment,
        password?: string,
        imageUrl?: string,
    ) {
        super(
            activated || undefined,
            authorities || undefined,
            email || undefined,
            firstName || undefined,
            langKey || undefined,
            lastName || undefined,
            login || undefined,
            imageUrl || undefined,
        );
        this.id = id ? id : null;
        this.groups = groups ? groups : null;
        this.createdBy = createdBy ? createdBy : null;
        this.createdDate = createdDate ? createdDate : null;
        this.lastModifiedBy = lastModifiedBy ? lastModifiedBy : null;
        this.lastModifiedDate = lastModifiedDate ? lastModifiedDate : null;
        this.lastNotificationRead = lastNotificationRead ? lastNotificationRead : null;
        this.password = password ? password : null;
    }

    public getName(): string {
        return `${this.firstName} ${this.lastName}`;
    }

    public getUsername(): string {
        return this.login || '';
    }

    public holds(agent: Agent): boolean {
        if (agent instanceof User) {
            return this.login === agent.login;
        } else if (agent instanceof Team) {
            return false;
        } else {
            throw new Error('Unknown agent type.');
        }
    }
}
