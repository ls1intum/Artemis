import { OnDestroy, Service, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { filter, map, tap } from 'rxjs/operators';
import { WebsocketService } from 'app/foundation/service/websocket.service';

/**
 * Describes the build run state
 */
export enum BuildRunState {
    RUNNING = 'RUNNING',
    COMPLETED = 'COMPLETED',
}

/**
 * A run that queues the builds of selected participations. The server returns it when it accepts the run and sends it again when the run has queued its last build.
 */
export interface SelectedBuildRun {
    runId: string;
}

/**
 * Whether a run that queues the builds of selected participations has queued its last build. A run the server does not know, because it is still going on or ended long ago, is not completed.
 */
export interface SelectedBuildRunStatus {
    completed: boolean;
}

export interface IProgrammingBuildRunService {
    /**
     * Subscribe for updates on running build runs. Atm we assume that only build run is running for the whole exercise.
     * @param programmingExerciseId
     */
    getBuildRunUpdates(programmingExerciseId: number): Observable<BuildRunState>;

    /**
     * Subscribe for the ends of runs that queue the builds of selected participations. Each emitted value names the run that is done.
     * @param programmingExerciseId
     */
    getSelectedBuildRunCompletions(programmingExerciseId: number): Observable<string>;
}

/**
 * Provides methods to retrieve information about running exercise builds.
 */
@Service()
export class ProgrammingBuildRunService implements OnDestroy {
    private websocketService = inject(WebsocketService);

    // Boolean subject: true == build is running, false == build is not running.
    private buildRunSubjects: { [programmingExerciseId: number]: BehaviorSubject<BuildRunState | undefined> } = {};
    private buildRunTopics: { [programmingExerciseId: number]: string } = {};
    private buildRunSubscriptions: { [programmingExerciseId: number]: Subscription } = {};

    private BUILD_RUN_TEMPLATE_TOPIC = '/topic/programming-exercises/%programmingExerciseId%/all-builds-triggered';
    private SELECTED_BUILD_RUN_COMPLETED_TEMPLATE_TOPIC = '/topic/programming-exercises/%programmingExerciseId%/selected-builds-run-completed';

    /**
     * Unsubscribe all buildRunSubjects
     */
    ngOnDestroy(): void {
        Object.values(this.buildRunSubjects).forEach((subject) => subject.unsubscribe());
        Object.values(this.buildRunSubscriptions).forEach((subscription) => subscription.unsubscribe());
    }

    private notifySubscribers(programmingExerciseId: number, buildRunState: BuildRunState) {
        const subject = this.buildRunSubjects[programmingExerciseId];
        if (subject) {
            subject.next(buildRunState);
        } else {
            this.buildRunSubjects[programmingExerciseId] = new BehaviorSubject<BuildRunState | undefined>(buildRunState);
        }
    }

    private subscribeWebsocket(programmingExerciseId: number) {
        if (!this.buildRunTopics[programmingExerciseId]) {
            const newSubmissionTopic = this.BUILD_RUN_TEMPLATE_TOPIC.replace('%programmingExerciseId%', programmingExerciseId.toString());
            this.buildRunTopics[programmingExerciseId] = newSubmissionTopic;
            this.buildRunSubscriptions[programmingExerciseId] = this.websocketService
                .subscribe<BuildRunState>(newSubmissionTopic)
                // Atm we only get the message about completed builds from the server.
                .pipe(tap((buildRunState: BuildRunState) => this.notifySubscribers(programmingExerciseId, buildRunState)))
                .subscribe();
        }
    }

    /**
     * Subscribe for updates on running build runs. Atm we assume that only build run is running for the whole exercise.
     *
     * @param programmingExerciseId
     */
    getBuildRunUpdates(programmingExerciseId: number) {
        const subject = this.buildRunSubjects[programmingExerciseId];
        if (subject) {
            return subject.asObservable().pipe(filter((stateObj) => stateObj !== undefined));
        }
        const newSubject = new BehaviorSubject<BuildRunState | undefined>(undefined);
        this.buildRunSubjects[programmingExerciseId] = newSubject;
        this.subscribeWebsocket(programmingExerciseId);
        return newSubject.pipe(filter((stateObj) => stateObj !== undefined));
    }

    /**
     * Subscribe for the ends of runs that queue the builds of selected participations. Several runs of one exercise may overlap, so every value names the run that is done.
     * The caller has to unsubscribe, which also ends the websocket subscription.
     *
     * @param programmingExerciseId
     */
    getSelectedBuildRunCompletions(programmingExerciseId: number): Observable<string> {
        const topic = this.SELECTED_BUILD_RUN_COMPLETED_TEMPLATE_TOPIC.replace('%programmingExerciseId%', programmingExerciseId.toString());
        return this.websocketService.subscribe<SelectedBuildRun>(topic).pipe(map((run) => run.runId));
    }
}
