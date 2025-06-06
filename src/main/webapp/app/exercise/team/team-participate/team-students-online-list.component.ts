import { Component, Input, OnDestroy, OnInit, ViewEncapsulation, inject } from '@angular/core';
import { OnlineTeamStudent, Team } from 'app/exercise/shared/entities/team/team.model';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { orderBy } from 'lodash-es';
import { Observable } from 'rxjs';
import { map, throttleTime } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { faCircle, faHistory } from '@fortawesome/free-solid-svg-icons';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { captureException } from '@sentry/angular';

@Component({
    selector: 'jhi-team-students-online-list',
    templateUrl: './team-students-online-list.component.html',
    styleUrls: ['./team-students-online-list.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [NgClass, FaIconComponent, NgbTooltip],
})
export class TeamStudentsOnlineListComponent implements OnInit, OnDestroy {
    private accountService = inject(AccountService);
    private websocketService = inject(WebsocketService);

    readonly SHOW_TYPING_DURATION = 2000; // ms
    readonly SEND_TYPING_INTERVAL = this.SHOW_TYPING_DURATION / 1.5;

    @Input() typing$: Observable<any>;
    @Input() participation: StudentParticipation;

    currentUser: User;
    onlineTeamStudents: OnlineTeamStudent[] = [];
    typingTeamStudents: OnlineTeamStudent[] = [];
    websocketTopic: string;

    // Icons
    faCircle = faCircle;
    faHistory = faHistory;

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
        this.websocketService.subscribe(this.websocketTopic);
        this.websocketService
            .receive(this.websocketTopic)
            .pipe(map(this.convertOnlineTeamStudentsFromServer))
            .subscribe({
                next: (students: OnlineTeamStudent[]) => {
                    this.onlineTeamStudents = students;
                    this.computeTypingTeamStudents();
                },
                error: (error) => captureException(error),
            });
        setTimeout(() => {
            this.websocketService.send<object>(this.buildWebsocketTopic('/trigger'), {});
        }, 700);
    }

    private setupTypingIndicatorSender() {
        if (this.typing$) {
            this.typing$.pipe(throttleTime(this.SEND_TYPING_INTERVAL)).subscribe({
                next: () => this.websocketService.send<object>(this.buildWebsocketTopic('/typing'), {}),
                error: (error) => captureException(error),
            });
        }
    }

    /**
     * Life cycle hook to indicate component destruction is done
     */
    ngOnDestroy(): void {
        this.websocketService.unsubscribe(this.websocketTopic);
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
     * Typing students are those online students whose {lastTypingDate} is more recent than {SHOW_TYPING_DURATION} ms ago.
     * If there are any typing students, find the timestamp of the earliest expiration of the typing state among them.
     * Then, schedule another computation for that timestamp.
     */
    private computeTypingTeamStudents() {
        this.typingTeamStudents = this.onlineTeamStudents.filter((student: OnlineTeamStudent) => {
            return Boolean(student.lastTypingDate?.isAfter(dayjs().subtract(this.SHOW_TYPING_DURATION, 'ms')));
        });
        if (this.typingTeamStudents.length > 0) {
            const lastTypingDates = this.typingTeamStudents.map((student: OnlineTeamStudent) => student.lastTypingDate).filter(Boolean);
            const minTypingDate = dayjs.min(lastTypingDates);
            if (minTypingDate) {
                const earliestExpiration = minTypingDate.add(this.SHOW_TYPING_DURATION, 'ms');
                const timeToExpirationInMilliseconds = earliestExpiration.diff(dayjs());
                setTimeout(() => this.computeTypingTeamStudents(), timeToExpirationInMilliseconds);
            }
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
