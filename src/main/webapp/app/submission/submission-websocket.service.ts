import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, Subscription, of } from 'rxjs';
import { catchError, distinctUntilChanged, filter, switchMap, tap } from 'rxjs/operators';
import { JhiWebsocketService } from 'app/core';
import { Submission } from 'app/entities/submission/submission.model';
import { SERVER_API_URL } from 'app/app.constants';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';

@Injectable({ providedIn: 'root' })
export class SubmissionWebsocketService implements OnDestroy {
    private newSubmissionRouteTopic = '/topic/participation/%participationId%/newSubmission';
    private subscriptions: { [participationId: number]: string } = {};
    private resultSubscriptions: { [participationId: number]: Subscription } = {};
    private subjects: { [participationId: number]: BehaviorSubject<Submission | null> } = {};
    private latestValue: { [participationId: number]: Submission | null } = {};

    constructor(private websocketService: JhiWebsocketService, private http: HttpClient, private participationWebsocketService: ParticipationWebsocketService) {}

    ngOnDestroy(): void {
        Object.values(this.resultSubscriptions).forEach(sub => sub.unsubscribe());
    }

    private fetchLatestPendingSubmission = (participationId: number): Observable<Submission> => {
        return this.http.get<Submission>(SERVER_API_URL + 'api/participations/' + participationId + '/latest-submission');
    };

    private setupWebsocketSubscription = (participationId: number): void => {
        if (!this.subscriptions[participationId]) {
            const newSubmissionTopic = this.newSubmissionRouteTopic.replace('%participationId%', participationId.toString());
            this.websocketService.subscribe(newSubmissionTopic);
            this.subscriptions[participationId] = newSubmissionTopic;
            this.websocketService
                .receive(newSubmissionTopic)
                .pipe(
                    tap((submission: Submission) => {
                        const subject = this.subjects[participationId];
                        subject.next(submission);
                    }),
                    tap((submission: Submission) => (this.latestValue[participationId] = submission)),
                )
                .subscribe();
        }
    };

    private subscribeForLatestResult = (participationId: number) => {
        if (this.resultSubscriptions[participationId]) {
            return;
        }
        this.resultSubscriptions[participationId] = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(participationId)
            .pipe(
                filter(() => !!this.latestValue[participationId]),
                distinctUntilChanged(),
                tap(() => this.subjects[participationId].next(null)),
            )
            .subscribe();
    };

    public getLatestPendingSubmission = (participationId: number) => {
        const subject = this.subjects[participationId];
        if (subject) {
            return subject.asObservable();
        }
        return this.fetchLatestPendingSubmission(participationId).pipe(
            catchError(() => of(null)),
            tap(() => this.setupWebsocketSubscription(participationId)),
            switchMap((submission: Submission | null) => {
                const newSubject = new BehaviorSubject(submission);
                this.subjects[participationId] = newSubject;
                return newSubject.asObservable();
            }),
            tap(() => this.subscribeForLatestResult(participationId)),
        );
    };
}
