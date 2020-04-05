import { Component, Input, OnInit, OnDestroy } from '@angular/core';
import { Team } from 'app/entities/team.model';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { orderBy } from 'lodash';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

class UserOnlineStatus {
    userId: number;
    online: boolean;
}

@Component({
    selector: 'jhi-team-students-online-list',
    templateUrl: './team-students-online-list.component.html',
    styleUrls: ['./team-students-online-list.component.scss'],
})
export class TeamStudentsOnlineListComponent implements OnInit, OnDestroy {
    @Input() participation: StudentParticipation;

    currentUser: User;
    onlineUserIds = new Set<number>();
    websocketChannel: string;

    constructor(private accountService: AccountService, private jhiWebsocketService: JhiWebsocketService) {}

    ngOnInit(): void {
        this.accountService.identity().then((user: User) => {
            this.currentUser = user;
            this.onlineUserIds.add(user.id!);

            this.websocketChannel = `/topic/participation/${this.participation.id}/team`;
            this.jhiWebsocketService.subscribe(this.websocketChannel);
            this.jhiWebsocketService.receive(this.websocketChannel).subscribe(({ userId, online }: UserOnlineStatus) => {
                return online ? this.onlineUserIds.add(userId) : this.onlineUserIds.delete(userId);
            });
            setTimeout(() => this.jhiWebsocketService.send(this.websocketChannel, this.userOnlineStatus(true)));
        });
    }

    ngOnDestroy(): void {
        this.jhiWebsocketService.send(this.websocketChannel, this.userOnlineStatus(false));
        this.jhiWebsocketService.unsubscribe(this.websocketChannel);
    }

    get team(): Team {
        return this.participation.team;
    }

    get studentList(): User[] {
        return [...(this.self ? [this.self] : []), ...orderBy(this.otherStudents, ['name'])];
    }

    get self(): User | undefined {
        return this.team.students.find(this.isSelf);
    }

    get otherStudents(): User[] {
        return this.team.students.filter(this.isOther);
    }

    isSelf = (user: User): boolean => {
        return user.id === this.currentUser?.id;
    };

    isOther = (user: User): boolean => {
        return !this.isSelf(user);
    };

    isOnline = (user: User): boolean => {
        return this.onlineUserIds.has(user.id!);
    };

    userOnlineStatus(online: boolean): UserOnlineStatus {
        return {
            userId: this.currentUser.id!,
            online,
        };
    }
}
