import { Component, Input, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { OnlineTeamStudent, Team } from 'app/entities/team.model';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { orderBy } from 'lodash-es';
import { Observable } from 'rxjs';
import { map, throttleTime } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { faCircle, faHistory } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-team-students-online-list',
    templateUrl: './team-students-online-list.component.html',
    styleUrls: ['./team-students-online-list.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class TeamStudentsOnlineListComponent implements OnInit, OnDestroy {
    readonly showTypingDuration = 2000; // ms
    readonly sendTypingInterval = this.showTypingDuration / 1.5;

    @Input() typing$: Observable<any>;
    @Input() participation: StudentParticipation;

    currentUser: User;
    onlineTeamStudents: OnlineTeamStudent[] = [];
    typingTeamStudents: OnlineTeamStudent[] = [];
    websocketTopic: string;

    // Icons
    faCircle = faCircle;
    faHistory = faHistory;

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
            this.setupOnlineTeamStudentsReceiver();
            this.setupTypingIndicatorSender();
        });
    }

    private setupOnlineTeamStudentsReceiver() {
        this.websocketTopic = this.buildWebsocketTopic();
        this.jhiWebsocketService.subscribe(this.websocketTopic);
        this.jhiWebsocketService
            .receive(this.websocketTopic)
            .pipe(map(this.convertOnlineTeamStudentsFromServer))
            .subscribe({
                next: (students: OnlineTeamStudent[]) => {
                    this.onlineTeamStudents = students;
                    this.computeTypingTeamStudents();
                },
                error: (error) => console.error(error),
            });
        setTimeout(() => {
            this.jhiWebsocketService.send(this.buildWebsocketTopic('/trigger'), {});
        }, 700);
    }

    private setupTypingIndicatorSender() {
        if (this.typing$) {
            this.typing$.pipe(throttleTime(this.sendTypingInterval)).subscribe({
                next: () => this.jhiWebsocketService.send(this.buildWebsocketTopic('/typing'), {}),
                error: (error) => console.error(error),
            });
        }
    }

    /**
     * Life cycle hook to indicate component destruction is done
     */
    ngOnDestroy(): void {
        this.jhiWebsocketService.unsubscribe(this.websocketTopic);
    }

    get team(): Team {
        return this.participation.team!;
    }

    /**
     * @return list of team members (1. current user, x. other users sorted alphabetically by full name)
     */
    get studentList(): User[] {
        return [...(this.self ? [this.self] : []), ...orderBy(this.otherStudents, ['name'])];
    }

    get self(): User | undefined {
        return this.team.students?.find(this.isSelf);
    }

    get otherStudents(): User[] {
        return this.team.students?.filter(this.isOther) || [];
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

    lastActionDate = (user: User): dayjs.Dayjs | undefined => {
        return this.onlineTeamStudents.find((student: OnlineTeamStudent) => student.login === user.login!)?.lastActionDate;
    };

    isTyping = (user: User): boolean => {
        return this.typingTeamStudents.map((student: OnlineTeamStudent) => student.login).includes(user.login!);
    };

    /**
     * Computes which of the online team members are currently typing
     *
     * Typing students are those online students whose {lastTypingDate} is more recent than {showTypingDuration} ms ago.
     * If there are any typing students, find the timestamp of the earliest expiration of the typing state among them.
     * Then, schedule another computation for that timestamp.
     */
    private computeTypingTeamStudents() {
        this.typingTeamStudents = this.onlineTeamStudents.filter((student: OnlineTeamStudent) => {
            return Boolean(student.lastTypingDate?.isAfter(dayjs().subtract(this.showTypingDuration, 'ms')));
        });
        if (this.typingTeamStudents.length > 0) {
            const lastTypingDates = this.typingTeamStudents.map((student: OnlineTeamStudent) => student.lastTypingDate).filter(Boolean);
            const earliestExpiration = dayjs.min(lastTypingDates).add(this.showTypingDuration, 'ms');
            const timeToExpirationInMilliseconds = earliestExpiration.diff(dayjs());
            setTimeout(() => this.computeTypingTeamStudents(), timeToExpirationInMilliseconds);
        }
    }

    private convertOnlineTeamStudentsFromServer(students: OnlineTeamStudent[]) {
        return students.map((student) => {
            return {
                ...student,
                lastTypingDate: student.lastTypingDate !== null ? dayjs(student.lastTypingDate) : null,
                lastActionDate: student.lastActionDate !== null ? dayjs(student.lastActionDate) : null,
            };
        });
    }

    /**
     * Topic for updates on online status of team members (needs to match route in ParticipationTeamWebsocketService.java)
     */
    private buildWebsocketTopic(path = ''): string {
        return `/topic/participations/${this.participation.id}/team${path}`;
    }
}
