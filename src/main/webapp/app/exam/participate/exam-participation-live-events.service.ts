import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ConnectionState, JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import dayjs from 'dayjs/esm';
import { LocalStorageService } from 'ngx-webstorage';
import { BehaviorSubject, Observable, Subject, Subscription, distinct, filter, map, tap } from 'rxjs';
import { convertDateFromServer } from 'app/utils/date.utils';
import { User } from 'app/core/user/user.model';
import { StudentExam } from 'app/entities/student-exam.model';

const EVENT_ACKNOWLEDGEMENT_LOCAL_STORAGE_KEY = 'examLastAcknowledgedEvent';

type UnixAcknowledgeTimestamps = { system: number; user: number };
export type AcknowledgeTimestamps = { system?: dayjs.Dayjs; user?: dayjs.Dayjs };
type StudentExamAcknowledgedEvents = { lastChange: number; acknowledgedEvents: { [eventId: string]: UnixAcknowledgeTimestamps } };

export enum ExamLiveEventType {
    EXAM_WIDE_ANNOUNCEMENT = 'examWideAnnouncement',
    WORKING_TIME_UPDATE = 'workingTimeUpdate',
    EXAM_ATTENDANCE_CHECK = 'examAttendanceCheck',
    PROBLEM_STATEMENT_UPDATE = 'problemStatementUpdate',
}

export type ExamLiveEvent = {
    id: number;
    createdBy: string;
    createdDate: dayjs.Dayjs;
    eventType: ExamLiveEventType;
    acknowledgeTimestamps?: AcknowledgeTimestamps;
    user?: User;
};

export type ExamWideAnnouncementEvent = ExamLiveEvent & {
    text: string;
};

export type ExamAttendanceCheckEvent = ExamLiveEvent & {
    text: string;
};

export type WorkingTimeUpdateEvent = ExamLiveEvent & {
    oldWorkingTime: number;
    newWorkingTime: number;
    courseWide: boolean;
};

export type ProblemStatementUpdateEvent = ExamLiveEvent & {
    text: string;
    problemStatement: string;
    exerciseId: number;
    exerciseName: string;
};

@Injectable({ providedIn: 'root' })
export class ExamParticipationLiveEventsService {
    private websocketService = inject(JhiWebsocketService);
    private examParticipationService = inject(ExamParticipationService);
    private localStorageService = inject(LocalStorageService);
    private httpClient = inject(HttpClient);

    private courseId?: number;
    private examId?: number;
    private studentExamId?: number;
    private studentExam?: StudentExam;
    private lastAcknowledgedEventStatus?: StudentExamAcknowledgedEvents;

    private currentWebsocketChannels?: string[];
    private currentWebsocketReceiveSubscriptions?: Subscription[];

    private events: ExamLiveEvent[] = [];

    // Subject that emits events for the user to acknowledge
    private newUserEventSubject = new Subject<ExamLiveEvent>();

    // Subject that emits events for the system to acknowledge
    private newSystemEventSubject = new Subject<ExamLiveEvent>();

    // Subject that emits all events when the array of events changes
    private allEventsSubject = new BehaviorSubject<ExamLiveEvent[]>([]);

    constructor() {
        this.clearOldAcknowledgement();

        // Listen to updates of the connection state; if we reconnect, we should fetch the list of events
        // to replay any missed events
        this.websocketService.connectionState.subscribe((connectionState: ConnectionState) => {
            if (connectionState.connected && connectionState.wasEverConnectedBefore && this.studentExamId) {
                setTimeout(() => this.fetchPreviousExamEvents(), 5000);
            }
        });

        this.examParticipationService.currentlyLoadedStudentExam.subscribe((studentExam) => {
            // Ignore updates if the loaded student exam is the same as the one we already have
            if (studentExam?.id === this.studentExamId) {
                return;
            }

            // The loaded student exam is different, so we need to reset the state
            this.lastAcknowledgedEventStatus = undefined;
            this.unsubscribeFromExamLiveEvents();
            this.events = [];

            // If we have a new student exam, we need to subscribe to the new websocket channel
            // and fetch the previous exam events
            // Note: This feature is not available for test runs
            if (studentExam && !studentExam.testRun) {
                this.studentExamId = studentExam.id;
                this.examId = studentExam.exam?.id;
                this.courseId = studentExam.exam?.course?.id;
                this.studentExam = studentExam;

                if (!this.studentExamId || !this.examId || !this.courseId) {
                    throw new Error('ExamParticipationLiveEventsService: Received invalid values for student exam id, exam id or course id');
                }

                this.lastAcknowledgedEventStatus = this.getLastAcknowledgedEventOfStudentExam();

                setTimeout(() => {
                    this.fetchPreviousExamEvents();
                    this.subscribeToExamLiveEvents();
                }, 5000); // We wait a bit before fetching events to avoid overloading anything
            } else {
                this.allEventsSubject.next([]);
            }
        });
    }

    public acknowledgeEvent(event: ExamLiveEvent, byUser: boolean) {
        if (!this.lastAcknowledgedEventStatus) {
            this.lastAcknowledgedEventStatus = {
                lastChange: -1,
                acknowledgedEvents: {},
            };
        }

        const nowUnix = dayjs().unix();
        const eventAcknowledgement = this.lastAcknowledgedEventStatus.acknowledgedEvents[String(event.id)] || { system: 0, user: 0 };

        if (byUser) {
            eventAcknowledgement.user = nowUnix;
        } else {
            eventAcknowledgement.system = nowUnix;
        }

        this.lastAcknowledgedEventStatus.acknowledgedEvents[String(event.id)] = eventAcknowledgement;
        this.lastAcknowledgedEventStatus.lastChange = nowUnix;

        this.setEventAcknowledgeTimestamps(event);

        this.storeLastAcknowledgedEventsOfStudentExam();
    }

    private subscribeToExamLiveEvents() {
        this.currentWebsocketChannels = [`/topic/exam-participation/studentExam/${this.studentExamId}/events`, `/topic/exam-participation/exam/${this.examId}/events`];
        this.currentWebsocketReceiveSubscriptions = this.currentWebsocketChannels.map((channel) => {
            this.websocketService.subscribe(channel);
            return this.websocketService.receive(channel).subscribe((event: ExamLiveEvent) => this.receiveExamLiveEvent(event));
        });
    }

    private receiveExamLiveEvent(event: ExamLiveEvent) {
        if (this.events.some((e) => e.id === event.id)) {
            return;
        }

        event.createdDate = convertDateFromServer(event.createdDate)!;
        event.user = this.studentExam?.user;

        this.events.unshift(event);
        this.newSystemEventSubject.next(event);
        this.newUserEventSubject.next(event);
        this.allEventsSubject.next(this.events);
    }

    private unsubscribeFromExamLiveEvents() {
        this.currentWebsocketReceiveSubscriptions?.forEach((subscription) => subscription.unsubscribe());
        this.currentWebsocketReceiveSubscriptions = undefined;

        this.currentWebsocketChannels?.forEach((channel) => this.websocketService.unsubscribe(channel));
        this.currentWebsocketChannels = undefined;
    }

    private fetchPreviousExamEvents() {
        this.httpClient.get<ExamLiveEvent[]>(`/api/courses/${this.courseId}/exams/${this.examId}/student-exams/live-events`).subscribe((events: ExamLiveEvent[]) => {
            this.events = events;
            this.events.forEach((event) => {
                event.createdDate = convertDateFromServer(event.createdDate)!;
            });

            // Replay events so unacknowledged events can be processed
            this.replayEvents();

            this.allEventsSubject.next(this.events);
        });
    }

    private replayEvents() {
        this.events.forEach((event) => {
            this.newSystemEventSubject.next(event);
            this.newUserEventSubject.next(event);
        });
    }

    private storeLastAcknowledgedEventsOfStudentExam() {
        const examLastAcknowledgedEvents = this.loadAcknowledgedEventsMapFromLocalStorage();
        examLastAcknowledgedEvents[String(this.studentExamId!)] = this.lastAcknowledgedEventStatus!;
        this.localStorageService.store(EVENT_ACKNOWLEDGEMENT_LOCAL_STORAGE_KEY, JSON.stringify(examLastAcknowledgedEvents));
    }

    private loadAcknowledgedEventsMapFromLocalStorage(): { [studentExamId: string]: StudentExamAcknowledgedEvents } {
        const fromStorage = this.localStorageService.retrieve(EVENT_ACKNOWLEDGEMENT_LOCAL_STORAGE_KEY);
        return fromStorage ? JSON.parse(fromStorage) : {};
    }

    private getLastAcknowledgedEventOfStudentExam(): StudentExamAcknowledgedEvents | undefined {
        const examLastAcknowledgedEventMap = this.loadAcknowledgedEventsMapFromLocalStorage();
        return examLastAcknowledgedEventMap[this.studentExamId!];
    }

    /**
     * Clears entries from the acknowledgement array that are older than a day for housekeeping purposes
     */
    private clearOldAcknowledgement() {
        const examLastAcknowledgedEvent = this.loadAcknowledgedEventsMapFromLocalStorage();
        const yesterday = dayjs().subtract(1, 'day').unix();
        for (const studentExamId in examLastAcknowledgedEvent) {
            if (examLastAcknowledgedEvent[studentExamId].lastChange < yesterday) {
                delete examLastAcknowledgedEvent[studentExamId];
            }
        }
        this.localStorageService.store(EVENT_ACKNOWLEDGEMENT_LOCAL_STORAGE_KEY, JSON.stringify(examLastAcknowledgedEvent));
    }

    public observeNewEventsAsSystem(eventTypes: ExamLiveEventType[] = []): Observable<ExamLiveEvent> {
        const observable = this.newSystemEventSubject.asObservable().pipe(
            filter(
                (event: ExamLiveEvent) =>
                    !this.lastAcknowledgedEventStatus?.acknowledgedEvents[String(event.id)]?.system && (eventTypes.length === 0 || eventTypes.includes(event.eventType)),
            ),
            tap((event: ExamLiveEvent) => this.setEventAcknowledgeTimestamps(event)),
            distinct((event) => event.id),
        );
        setTimeout(() => this.replayEvents());
        return observable;
    }

    public observeNewEventsAsUser(eventTypes: ExamLiveEventType[] = [], examStartDate: dayjs.Dayjs): Observable<ExamLiveEvent> {
        const observable = this.newUserEventSubject.asObservable().pipe(
            filter(
                (event: ExamLiveEvent) =>
                    !this.lastAcknowledgedEventStatus?.acknowledgedEvents[String(event.id)]?.user &&
                    (eventTypes.length === 0 || eventTypes.includes(event.eventType)) &&
                    !(event.eventType === ExamLiveEventType.PROBLEM_STATEMENT_UPDATE && event.createdDate.isBefore(examStartDate)),
            ),
            tap((event: ExamLiveEvent) => this.setEventAcknowledgeTimestamps(event)),
            distinct((event) => event.id),
        );
        setTimeout(() => this.replayEvents());
        return observable;
    }

    public observeAllEvents(eventTypes: ExamLiveEventType[] = []): Observable<ExamLiveEvent[]> {
        return this.allEventsSubject.asObservable().pipe(
            map((events: ExamLiveEvent[]) => (eventTypes.length === 0 ? events : events.filter((event) => eventTypes.includes(event.eventType)))),
            tap((events: ExamLiveEvent[]) => events.forEach((event) => this.setEventAcknowledgeTimestamps(event))),
        );
    }

    private setEventAcknowledgeTimestamps(event: ExamLiveEvent) {
        const unixTimestamps = this.lastAcknowledgedEventStatus?.acknowledgedEvents[String(event.id)];
        if (!unixTimestamps) {
            return;
        }
        event.acknowledgeTimestamps = {
            system: unixTimestamps.system > 0 ? dayjs.unix(unixTimestamps.system) : undefined,
            user: unixTimestamps.user > 0 ? dayjs.unix(unixTimestamps.user) : undefined,
        };
    }
}
