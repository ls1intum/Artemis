import { Injectable, OnDestroy, inject } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { filter, tap } from 'rxjs/operators';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';

/**
 * Describes the build run state
 */
export enum BuildRunState {
    RUNNING = 'RUNNING',
    COMPLETED = 'COMPLETED',
}

export interface IProgrammingBuildRunService {
    /**
     * Subscribe for updates on running build runs. Atm we assume that only build run is running for the whole exercise.
     * @param programmingExerciseId
     */
    getBuildRunUpdates(programmingExerciseId: number): Observable<BuildRunState>;
}

/**
 * Provides methods to retrieve information about running exercise builds.
 */
@Injectable({ providedIn: 'root' })
export class ProgrammingBuildRunService implements OnDestroy {
    private websocketService = inject(JhiWebsocketService);

    // Boolean subject: true == build is running, false == build is not running.
    private buildRunSubjects: { [programmingExerciseId: number]: BehaviorSubject<BuildRunState | undefined> } = {};
    private buildRunTopics: { [programmingExerciseId: number]: string } = {};

    private BUILD_RUN_TEMPLATE_TOPIC = '/topic/programming-exercises/%programmingExerciseId%/all-builds-triggered';

    /**
     * Unsubscribe all buildRunSubjects
     */
    ngOnDestroy(): void {
        Object.values(this.buildRunSubjects).forEach((subject) => subject.unsubscribe());
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
            this.websocketService.subscribe(newSubmissionTopic);
            this.websocketService
                .receive(newSubmissionTopic)
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
            return subject.asObservable().pipe(filter((stateObj) => stateObj !== undefined)) as Observable<BuildRunState>;
        }
        const newSubject = new BehaviorSubject<BuildRunState | undefined>(undefined);
        this.buildRunSubjects[programmingExerciseId] = newSubject;
        this.subscribeWebsocket(programmingExerciseId);
        return newSubject.pipe(filter((stateObj) => stateObj !== undefined)) as Observable<BuildRunState>;
    }
}
