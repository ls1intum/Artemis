import { IProgrammingSubmissionService, ProgrammingSubmissionStateObj } from 'app/programming-submission/programming-submission.service';
import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { filter, tap } from 'rxjs/operators';
import { JhiWebsocketService } from 'app/core';

@Injectable({ providedIn: 'root' })
export class ProgrammingBuildRunService implements OnDestroy {
    // Boolean subject: true == build is running, false == build is not running.
    private buildRunSubjects: { [programmingExerciseId: number]: BehaviorSubject<boolean | undefined> } = {};
    private buildRunTopics: { [programmingExerciseId: number]: string } = {};

    private BUILD_RUN_TEMPLATE_TOPIC = '/topic/programming-exercises/%programmingExerciseId%/all-builds-triggered';

    constructor(private websocketService: JhiWebsocketService) {}

    ngOnDestroy(): void {
        Object.values(this.buildRunSubjects).forEach(subject => subject.unsubscribe());
    }

    private notifySubscribers(programmingExerciseId: number, isBuilding: boolean) {
        const subject = this.buildRunSubjects[programmingExerciseId];
        if (subject) {
            subject.next(isBuilding);
        } else {
            this.buildRunSubjects[programmingExerciseId] = new BehaviorSubject<boolean | undefined>(isBuilding);
        }
    }

    private subscribeWebsocket(programmingExerciseId: number) {
        if (!this.buildRunTopics[programmingExerciseId]) {
            const newSubmissionTopic = this.BUILD_RUN_TEMPLATE_TOPIC.replace('%programmingExerciseId%', programmingExerciseId.toString());
            this.buildRunTopics[programmingExerciseId] = newSubmissionTopic;
            this.websocketService.subscribe(newSubmissionTopic);
            this.websocketService
                .receive(newSubmissionTopic)
                .pipe(tap(() => this.notifySubscribers(programmingExerciseId, false))) // Atm we only get the message about completed builds from the server.
                .subscribe();
        }
    }

    emitBuildRunUpdate(programmingExerciseId: number, isBuilding: boolean) {
        this.notifySubscribers(programmingExerciseId, isBuilding);
    }

    getBuildRunUpdates(programmingExerciseId: number) {
        const subject = this.buildRunSubjects[programmingExerciseId];
        if (subject) {
            return subject.asObservable().pipe(filter(stateObj => stateObj !== undefined)) as Observable<boolean>;
        }
        const newSubject = new BehaviorSubject<boolean | undefined>(undefined);
        this.buildRunSubjects[programmingExerciseId] = newSubject;
        this.subscribeWebsocket(programmingExerciseId);
        return newSubject.pipe(filter(stateObj => stateObj !== undefined)) as Observable<boolean>;
    }
}
