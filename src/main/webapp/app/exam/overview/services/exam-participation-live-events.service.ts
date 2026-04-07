import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ConnectionState, WebsocketService } from 'app/shared/service/websocket.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import dayjs from 'dayjs/esm';
import { BehaviorSubject, Observable, Subject, Subscription, distinct, filter, map, tap } from 'rxjs';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { User } from 'app/core/user/user.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';

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

/**
 * Root-level singleton service that manages real-time delivery of exam live events
 * (announcements, working time changes, problem statement updates, attendance checks)
 * to students participating in an exam.
 *
 * Event delivery uses a two-channel strategy:
 *   1. WebSocket (primary) — events are pushed in real time via two STOMP topics:
 *        - /topic/exam-participation/studentExam/{id}/events  (student-specific events)
 *        - /topic/exam-participation/exam/{id}/events          (exam-wide announcements)
 *   2. REST fallback (secondary) — GET /api/exam/.../student-exams/live-events fetches
 *      all persisted events from the database, used to backfill events that may have been
 *      missed during initial page load or WebSocket disconnection.
 *
 * Deduplication happens at two levels:
 *   - receiveExamLiveEvent() checks the in-memory events array to skip already-known events.
 *   - The distinct() operator on observer pipes ensures each subscriber processes an event
 *     at most once, even when replayEvents() re-emits all events through the subjects.
 *
 * Acknowledgement state is persisted to localStorage so the UI can distinguish between
 * events the system has already auto-processed and events the user has explicitly dismissed.
 */
@Injectable({ providedIn: 'root' })
export class ExamParticipationLiveEventsService {
    private websocketService = inject(WebsocketService);
    private examParticipationService = inject(ExamParticipationService);
    private localStorageService = inject(LocalStorageService);
    private httpClient = inject(HttpClient);

    private courseId?: number;
    private examId?: number;
    private studentExamId?: number;
    private studentExam?: StudentExam;
    private lastAcknowledgedEventStatus?: StudentExamAcknowledgedEvents;

    /** The STOMP topic paths currently subscribed to (one student-specific, one exam-wide). */
    private currentWebsocketChannels?: string[];

    /** RxJS subscriptions to the WebSocket observables — kept so we can unsubscribe on cleanup. */
    private currentWebsocketReceiveSubscriptions?: Subscription[];

    /** Handle for the delayed fetchPreviousExamEvents() call, so it can be cancelled on cleanup. */
    private fetchEventsTimeoutHandle?: ReturnType<typeof setTimeout>;

    /**
     * In-memory store of all known events for the current student exam.
     * Populated by both WebSocket messages and the REST fallback fetch.
     * Used as the source of truth for replay and for the allEventsSubject emission.
     */
    private events: ExamLiveEvent[] = [];

    /**
     * Emits individual events that the UI should handle programmatically (e.g., updating the
     * working time display or refreshing a problem statement). Subscribers use
     * observeNewEventsAsUser() or observeNewEventsAsSystem() which pipe through this subject.
     */
    private newUserEventSubject = new Subject<ExamLiveEvent>();

    /**
     * Emits individual events for system-level processing (e.g., auto-acknowledging a working
     * time update). Separate from newUserEventSubject because system and user acknowledgements
     * track independently — the system can mark an event as processed while the user dialog
     * remains open.
     */
    private newSystemEventSubject = new Subject<ExamLiveEvent>();

    /**
     * BehaviorSubject that always holds the complete current list of events.
     * Used by the events overlay to render the full event history.
     */
    private allEventsSubject = new BehaviorSubject<ExamLiveEvent[]>([]);

    constructor() {
        this.clearOldAcknowledgement();

        // Handle WebSocket reconnection.
        // When the STOMP connection drops and re-establishes (e.g., after a network interruption),
        // RxStomp automatically re-subscribes to all previously watched topics, so new events will
        // flow again. However, any events published to the topic while the socket was down are lost
        // (STOMP topics are pub/sub, not queued). To recover those missed events, we immediately
        // fetch the full event list from the REST endpoint and merge it into our in-memory store.
        // The guard conditions ensure we only do this on a *re*connection (wasEverConnectedBefore)
        // and only when we actually have an active student exam to fetch events for.
        this.websocketService.connectionState.subscribe((connectionState: ConnectionState) => {
            if (connectionState.connected && connectionState.wasEverConnectedBefore && this.studentExamId) {
                this.fetchPreviousExamEvents();
            }
        });

        // React to student exam changes.
        // ExamParticipationService emits on this subject whenever a student exam is loaded
        // (e.g., from getOwnStudentExam or loadStudentExamWithExercisesForConduction).
        // We use the student exam ID to detect whether this is a genuinely new exam or just
        // a re-emission of the same one (which happens on the conduction reload after clicking
        // "Start Exam") — in the latter case we skip re-initialization.
        this.examParticipationService.currentlyLoadedStudentExam.subscribe((studentExam) => {
            if (studentExam?.id === this.studentExamId) {
                return;
            }

            // A different student exam was loaded — tear down everything from the previous exam
            // and start fresh. This includes unsubscribing from old WebSocket channels,
            // cancelling any pending REST fetch, and clearing the in-memory event list.
            this.lastAcknowledgedEventStatus = undefined;
            this.unsubscribeFromExamLiveEvents();
            if (this.fetchEventsTimeoutHandle) {
                clearTimeout(this.fetchEventsTimeoutHandle);
                this.fetchEventsTimeoutHandle = undefined;
            }
            this.events = [];

            // Live events are only enabled for real student exams, not for instructor test runs.
            // Test runs are short-lived instructor-only dry runs where live event delivery is
            // not needed (the instructor is both sender and receiver).
            if (studentExam && !studentExam.testRun) {
                this.studentExamId = studentExam.id;
                this.examId = studentExam.exam?.id;
                this.courseId = studentExam.exam?.course?.id;
                this.studentExam = studentExam;

                if (!this.studentExamId || !this.examId || !this.courseId) {
                    throw new Error('ExamParticipationLiveEventsService: Received invalid values for student exam id, exam id or course id');
                }

                // Restore previously acknowledged events from localStorage so we don't
                // re-show events the user has already dismissed in a prior page load.
                this.lastAcknowledgedEventStatus = this.getLastAcknowledgedEventOfStudentExam();

                // Step 1: Subscribe to the WebSocket topics immediately.
                // This ensures that any event published from this moment onward is captured
                // in real time. Previously, both the subscription and the REST fetch were
                // delayed by 5 seconds, which created a window where events were silently lost.
                this.subscribeToExamLiveEvents();

                // Step 2: After a short delay, fetch all historical events from the REST endpoint.
                // This catches any events that were created before the WebSocket subscription
                // became active (e.g., an announcement made seconds before the student loaded
                // the page). The 2-second delay is intentional: it spreads the HTTP requests
                // across time when hundreds of students start the exam simultaneously, reducing
                // peak load on the server. The WebSocket subscription above already covers the
                // real-time path, so this delay does not create a gap in event delivery.
                this.fetchEventsTimeoutHandle = setTimeout(() => this.fetchPreviousExamEvents(), 2000);
            } else {
                // No valid student exam (or it's a test run) — reset all identity fields so that
                // the reconnection handler doesn't fetch events for a stale exam, and emit an
                // empty event list so that any UI components observing events clear their state.
                this.studentExamId = undefined;
                this.examId = undefined;
                this.courseId = undefined;
                this.studentExam = undefined;
                this.allEventsSubject.next([]);
            }
        });
    }

    /**
     * Marks an event as acknowledged, either by the system (automatic processing) or by the user
     * (explicit dismissal in the UI). The acknowledgement is persisted to localStorage so it
     * survives page reloads. Two independent timestamps are tracked per event:
     *   - system: set when the event was auto-processed (e.g., working time updated in the timer)
     *   - user: set when the user explicitly dismissed the event notification
     *
     * @param event  the event to acknowledge
     * @param byUser true if the user explicitly dismissed it, false if the system auto-processed it
     */
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

        // Update the in-memory timestamp on the event object so UI components can react immediately
        this.setEventAcknowledgeTimestamps(event);

        // Persist to localStorage so acknowledgements survive page reloads
        this.storeLastAcknowledgedEventsOfStudentExam();
    }

    /**
     * Subscribes to both WebSocket topics for the current student exam:
     *   - Student-specific channel: receives working time updates, problem statement changes,
     *     and attendance checks targeted at this particular student exam.
     *   - Exam-wide channel: receives announcements broadcast to all students in the exam.
     *
     * Each incoming message is routed through receiveExamLiveEvent() for deduplication and
     * distribution to the event subjects.
     */
    private subscribeToExamLiveEvents() {
        this.currentWebsocketChannels = [`/topic/exam-participation/studentExam/${this.studentExamId}/events`, `/topic/exam-participation/exam/${this.examId}/events`];
        this.currentWebsocketReceiveSubscriptions = this.currentWebsocketChannels.map((channel) => {
            return this.websocketService.subscribe<ExamLiveEvent>(channel).subscribe((event: ExamLiveEvent) => this.receiveExamLiveEvent(event));
        });
    }

    /**
     * Processes a single incoming exam live event (from WebSocket).
     * Deduplicates against the in-memory events array by event ID, converts the server
     * date format, and pushes the event to both the system and user subjects so that
     * all active observers (working time handler, problem statement handler, event overlay)
     * are notified.
     */
    private receiveExamLiveEvent(event: ExamLiveEvent) {
        // Skip if we already have this event (e.g., from a prior REST fetch or duplicate WS delivery)
        if (this.events.some((e) => e.id === event.id)) {
            return;
        }

        event.createdDate = convertDateFromServer(event.createdDate)!;
        event.user = this.studentExam?.user;

        // Add to the front of the array (newest first) and notify all observers
        this.events.unshift(event);
        this.newSystemEventSubject.next(event);
        this.newUserEventSubject.next(event);
        this.allEventsSubject.next(this.events);
    }

    /**
     * Tears down WebSocket subscriptions for the current student exam.
     * Called when switching to a different student exam or during cleanup.
     */
    private unsubscribeFromExamLiveEvents() {
        this.currentWebsocketReceiveSubscriptions?.forEach((subscription) => subscription.unsubscribe());
        this.currentWebsocketReceiveSubscriptions = undefined;

        this.currentWebsocketChannels = undefined;
    }

    /**
     * Fetches all persisted events for the current student exam from the REST endpoint
     * (GET /api/exam/courses/{courseId}/exams/{examId}/student-exams/live-events).
     *
     * The server query returns both exam-wide events (studentExamId IS NULL) and
     * student-specific events (studentExamId = this student's exam), covering all four
     * event types: announcements, working time updates, problem statement updates,
     * and attendance checks.
     *
     * IMPORTANT: This method merges fetched events into the existing in-memory array
     * rather than replacing it. This is critical because WebSocket events may have arrived
     * between the moment the HTTP request was sent and the moment the response came back.
     * A naive overwrite (this.events = fetchedEvents) would silently discard those
     * WebSocket-delivered events, causing intermittent event loss.
     *
     * After merging, all events are replayed through the system and user subjects so that
     * observers can process any events they missed (e.g., if a subscriber was created after
     * the WebSocket message was already emitted).
     */
    private fetchPreviousExamEvents() {
        this.httpClient.get<ExamLiveEvent[]>(`/api/exam/courses/${this.courseId}/exams/${this.examId}/student-exams/live-events`).subscribe((fetchedEvents: ExamLiveEvent[]) => {
            fetchedEvents.forEach((event) => {
                event.createdDate = convertDateFromServer(event.createdDate)!;
            });

            // Build a set of IDs already present in the in-memory array (from WebSocket delivery)
            // and only add events from the REST response that we don't already have.
            const existingIds = new Set(this.events.map((e) => e.id));
            const newEvents = fetchedEvents.filter((e) => !existingIds.has(e.id));
            this.events = [...this.events, ...newEvents];
            // Sort newest-first for consistent display order, since WebSocket events (unshifted)
            // and REST events (appended) may interleave in unpredictable order.
            this.events.sort((a, b) => b.createdDate.valueOf() - a.createdDate.valueOf());

            // Replay all events through both subjects. Observers that have already seen an event
            // (via prior WebSocket delivery) will ignore the duplicate thanks to the distinct()
            // operator in their pipe. Observers that were created after the initial WebSocket
            // emission (e.g., the working time subscriber set up during exam initialization)
            // will now receive and process the event for the first time.
            this.replayEvents();

            this.allEventsSubject.next(this.events);
        });
    }

    /**
     * Re-emits every event in the in-memory array through both the system and user subjects.
     * This is used after fetching historical events and when a new observer subscribes
     * (triggered via setTimeout in observeNewEventsAsSystem/AsUser).
     *
     * Callers rely on the distinct(event => event.id) operator in the observer pipes to
     * prevent double-processing — replaying is safe even if the observer has already seen
     * some of these events.
     */
    private replayEvents() {
        this.events.forEach((event) => {
            this.newSystemEventSubject.next(event);
            this.newUserEventSubject.next(event);
        });
    }

    /**
     * Persists the current acknowledgement state for this student exam to localStorage.
     * The storage format is a JSON map keyed by studentExamId, allowing acknowledgement
     * tracking for multiple exams on the same browser.
     */
    private storeLastAcknowledgedEventsOfStudentExam() {
        const examLastAcknowledgedEvents = this.loadAcknowledgedEventsMapFromLocalStorage();
        examLastAcknowledgedEvents[String(this.studentExamId!)] = this.lastAcknowledgedEventStatus!;
        this.localStorageService.store(EVENT_ACKNOWLEDGEMENT_LOCAL_STORAGE_KEY, JSON.stringify(examLastAcknowledgedEvents));
    }

    /**
     * Loads the full acknowledgement map from localStorage. Returns an empty object
     * if nothing is stored yet. The map is keyed by studentExamId (as a string).
     */
    private loadAcknowledgedEventsMapFromLocalStorage(): { [studentExamId: string]: StudentExamAcknowledgedEvents } {
        const fromStorage = this.localStorageService.retrieve<string>(EVENT_ACKNOWLEDGEMENT_LOCAL_STORAGE_KEY);
        return fromStorage ? JSON.parse(fromStorage) : {};
    }

    /**
     * Returns the stored acknowledgement state for the current student exam, or undefined
     * if no acknowledgements have been recorded yet (first visit).
     */
    private getLastAcknowledgedEventOfStudentExam(): StudentExamAcknowledgedEvents | undefined {
        const examLastAcknowledgedEventMap = this.loadAcknowledgedEventsMapFromLocalStorage();
        return examLastAcknowledgedEventMap[this.studentExamId!];
    }

    /**
     * Housekeeping: removes acknowledgement entries older than 24 hours from localStorage.
     * This prevents unbounded growth of the stored JSON over time. Called once in the
     * constructor (on service initialization).
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

    /**
     * Returns an observable that emits events intended for system-level processing (e.g.,
     * ExamParticipationComponent subscribes to WORKING_TIME_UPDATE events to update the
     * exam timer, and to PROBLEM_STATEMENT_UPDATE events to refresh exercise content).
     *
     * The pipe applies three stages:
     *   1. filter — drops events that have already been system-acknowledged (from localStorage)
     *      and events whose type doesn't match the requested eventTypes filter.
     *   2. tap — attaches the stored acknowledgement timestamps to the event object so
     *      downstream consumers can inspect when it was last processed.
     *   3. distinct — ensures each event ID is emitted at most once through this pipe,
     *      even when replayEvents() re-emits all events. This is distinct() (not
     *      distinctUntilChanged), so it maintains a permanent set of seen IDs for the
     *      lifetime of the subscription. Each call to observeNewEventsAsSystem() creates
     *      a fresh pipe with a fresh distinct set.
     *
     * After creating the observable, a microtask-deferred replayEvents() is scheduled.
     * This ensures that events already in memory (from a prior WebSocket delivery or
     * REST fetch) are immediately delivered to the new subscriber.
     *
     * @param eventTypes optional filter — if provided, only events of these types are emitted
     */
    public observeNewEventsAsSystem(eventTypes: ExamLiveEventType[] = []): Observable<ExamLiveEvent> {
        const observable = this.newSystemEventSubject.asObservable().pipe(
            filter(
                (event: ExamLiveEvent) =>
                    !this.lastAcknowledgedEventStatus?.acknowledgedEvents[String(event.id)]?.system && (eventTypes.length === 0 || eventTypes.includes(event.eventType)),
            ),
            tap((event: ExamLiveEvent) => this.setEventAcknowledgeTimestamps(event)),
            distinct((event) => event.id),
        );
        // Schedule a replay in a microtask so the caller can subscribe to the returned observable
        // before events start flowing. Without this, events already in memory would not reach
        // a subscriber that was just created.
        setTimeout(() => this.replayEvents());
        return observable;
    }

    /**
     * Returns an observable that emits events intended for user-facing notification
     * (e.g., the exam live events overlay that shows announcements and attendance checks).
     *
     * Similar to observeNewEventsAsSystem() but with an additional filter:
     *   - Problem statement updates created before the exam start date are suppressed,
     *     because instructors may update problem statements during the preparation phase
     *     and students should not see stale pre-exam notifications.
     *
     * @param eventTypes    optional filter for specific event types
     * @param examStartDate the exam's official start date, used to suppress pre-exam events
     */
    public observeNewEventsAsUser(eventTypes: ExamLiveEventType[] = [], examStartDate: dayjs.Dayjs): Observable<ExamLiveEvent> {
        const observable = this.newUserEventSubject.asObservable().pipe(
            filter(
                (event: ExamLiveEvent) =>
                    !this.lastAcknowledgedEventStatus?.acknowledgedEvents[String(event.id)]?.user &&
                    (eventTypes.length === 0 || eventTypes.includes(event.eventType)) &&
                    // Suppress problem statement updates that were created before the exam started,
                    // as these reflect instructor preparation, not live exam changes
                    !(event.eventType === ExamLiveEventType.PROBLEM_STATEMENT_UPDATE && event.createdDate.isBefore(examStartDate)),
            ),
            tap((event: ExamLiveEvent) => this.setEventAcknowledgeTimestamps(event)),
            distinct((event) => event.id),
        );
        setTimeout(() => this.replayEvents());
        return observable;
    }

    /**
     * Returns an observable that emits the complete list of events whenever the list changes.
     * Unlike the system/user observers above, this emits arrays (not individual events) and
     * does not use distinct() — it always delivers the latest snapshot. Used by the events
     * overlay component to render the full event history.
     *
     * @param eventTypes optional filter — if provided, only events of these types are included
     */
    public observeAllEvents(eventTypes: ExamLiveEventType[] = []): Observable<ExamLiveEvent[]> {
        return this.allEventsSubject.asObservable().pipe(
            map((events: ExamLiveEvent[]) => (eventTypes.length === 0 ? events : events.filter((event) => eventTypes.includes(event.eventType)))),
            tap((events: ExamLiveEvent[]) => events.forEach((event) => this.setEventAcknowledgeTimestamps(event))),
        );
    }

    /**
     * Hydrates the acknowledgeTimestamps field on an event object from the localStorage-backed
     * acknowledgement state. This allows UI components to check whether an event has been
     * acknowledged without directly querying localStorage.
     */
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
