import { Injectable } from '@angular/core';
import { ProgrammingExercise } from '../entities/programming-exercise';
import { ProgrammingSubmissionService } from '../programming-submission';
import { ParticipationWebsocketService } from '../entities/participation';
import { Result } from '../entities/result';
import { filter, map, tap } from 'rxjs/operators';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';
import { CodeEditorBuildLogService, DomainType } from 'app/code-editor';
import { BuildLogEntryArray } from 'app/entities/build-log';

@Injectable({
    providedIn: 'root',
})
export class IdeBuildAndTestService {
    constructor(
        private submissionService: ProgrammingSubmissionService,
        private participationWebsocketService: ParticipationWebsocketService,
        private javaBridge: JavaBridgeService,
        private buildLogService: CodeEditorBuildLogService,
    ) {}

    buildAndTestExercise(exercise: ProgrammingExercise) {
        // Get the participation data for the current participation ID
        const participationId = exercise.studentParticipations[0].id;
        this.buildLogService.setDomain([DomainType.PARTICIPATION, exercise.studentParticipations[0]]);

        // Trigger a build for the current participation
        this.submissionService.triggerBuild(participationId).subscribe();
        this.javaBridge.onBuildStarted();

        // Listen for the new result on the websocket
        this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(participationId)
            .pipe(
                filter(Boolean),
                map(result => result as Result),
                tap(result => {
                    if ((result && result.successful) || (result && !result.successful && result.feedbacks && result.feedbacks.length)) {
                        result.feedbacks.forEach(feedback => this.javaBridge.onTestResult(!!feedback.positive, feedback.detailText!));
                        this.javaBridge.onBuildFinished();
                    } else {
                        this.forwardBuildLogs();
                    }
                }),
            )
            .subscribe();

        // If there is no feedback, because the build failed, fetch the logs, otherwise forward the feedback
    }

    private forwardBuildLogs() {
        this.buildLogService
            .getBuildLogs()
            .pipe(
                map(logs => new BuildLogEntryArray(...logs)),
                tap((logs: BuildLogEntryArray) => {
                    const logErrors = logs.extractErrors();
                    this.javaBridge.onBuildFailed(JSON.stringify(logErrors));
                }),
            )
            .subscribe();
    }
}
