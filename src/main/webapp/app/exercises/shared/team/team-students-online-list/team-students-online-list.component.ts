import { Component, Input, OnInit, OnDestroy } from '@angular/core';
import { OnlineTeamStudent, Team } from 'app/entities/team.model';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { orderBy } from 'lodash';
import { map } from 'rxjs/operators';
import * as moment from 'moment';
import { Moment } from 'moment';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

@Component({
    selector: 'jhi-team-students-online-list',
    templateUrl: './team-students-online-list.component.html',
    styleUrls: ['./team-students-online-list.component.scss'],
})
export class TeamStudentsOnlineListComponent implements OnInit, OnDestroy {
    readonly activeDuration = 2000; // ms

    @Input() participation: StudentParticipation;

    currentUser: User;
    onlineTeamStudents: OnlineTeamStudent[] = [];
    activeTeamStudents: OnlineTeamStudent[] = [];
    websocketTopic: string;

    constructor(private accountService: AccountService, private jhiWebsocketService: JhiWebsocketService) {}

    /**
     * Subscribes to the websocket topic "team" for the given participation
     *
     * The current list of online team members is sent upon subscribing, however, this message cannot be received yet by the
     * client sometimes and thus the list is explicitly requested once more after a short timeout to cover those cases.
     */
    ngOnInit(): void {
        this.accountService.identity().then((user: User) => {
            this.currentUser = user;
            this.websocketTopic = this.buildWebsocketTopic();
            this.jhiWebsocketService.subscribe(this.websocketTopic);
            this.jhiWebsocketService
                .receive(this.websocketTopic)
                .pipe(map(this.convertOnlineTeamStudentsFromServer))
                .subscribe(
                    (students: OnlineTeamStudent[]) => {
                        this.onlineTeamStudents = students;
                        this.computeActiveTeamStudents();
                    },
                    (error) => console.error(error),
                );
            setTimeout(() => {
                this.jhiWebsocketService.send(this.buildWebsocketTopic('/trigger'), {});
            }, 700);
        });
    }

    ngOnDestroy(): void {
        this.jhiWebsocketService.unsubscribe(this.websocketTopic);
    }

    /**
     * Topic for updates on online status of team members (needs to match route in ParticipationTeamWebsocketService.java)
     */
    buildWebsocketTopic(path = ''): string {
        return `/topic/participations/${this.participation.id}/team${path}`;
    }

    get team(): Team {
        return this.participation.team;
    }

    /**
     * @return list of team members (1. current user, x. other users sorted alphabetically by full name)
     */
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
        return this.onlineTeamStudents.map((student: OnlineTeamStudent) => student.login).includes(user.login!);
    };

    isActive = (user: User): boolean => {
        return this.activeTeamStudents.map((student: OnlineTeamStudent) => student.login).includes(user.login!);
    };

    computeActiveTeamStudents() {
        this.activeTeamStudents = this.onlineTeamStudents.filter((student: OnlineTeamStudent) => {
            return Boolean(student?.lastActionDate?.isAfter(moment().subtract(this.activeDuration, 'ms')));
        });
        if (this.activeTeamStudents.length > 0) {
            const lastActionDates = this.activeTeamStudents.map((student: OnlineTeamStudent) => student.lastActionDate).filter(Boolean);
            const earliestExpiration: Moment = moment(moment.min(lastActionDates)).add(this.activeDuration, 'ms');
            const timeToExpirationInMilliseconds = earliestExpiration.diff(moment());
            setTimeout(() => this.computeActiveTeamStudents(), timeToExpirationInMilliseconds);
        }
    }

    private convertOnlineTeamStudentsFromServer(students: OnlineTeamStudent[]) {
        return students.map((student) => {
            return { ...student, lastActionDate: student.lastActionDate !== null ? moment(student.lastActionDate) : null };
        });
    }
}
