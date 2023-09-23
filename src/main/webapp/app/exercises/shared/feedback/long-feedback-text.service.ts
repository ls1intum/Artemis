import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { LongFeedbackText } from 'app/entities/long-feedback-text.model';
import { Observable, Subject, Subscription, distinctUntilChanged, tap } from 'rxjs';
import { Cacheable } from 'ts-cacheable';
import { AccountService } from 'app/core/auth/account.service';

export type LongFeedbackResponse = HttpResponse<LongFeedbackText>;

const logoutSubject = new Subject<void>();

@Injectable({ providedIn: 'root' })
export class LongFeedbackTextService implements OnDestroy {
    private userChangeSubscription: Subscription;

    constructor(
        private http: HttpClient,
        private accountService: AccountService,
    ) {}

    init() {
        this.userChangeSubscription = this.accountService
            .getAuthenticationState()
            .pipe(
                // Fires on every event where the user has changed (login/logout).
                distinctUntilChanged(),
                tap(() => logoutSubject.next()),
            )
            .subscribe();
    }

    ngOnDestroy() {
        this.userChangeSubscription?.unsubscribe();
    }

    @Cacheable({
        cacheBusterObserver: logoutSubject.asObservable(),
        maxCacheCount: 20,
        maxAge: 30 * 60 * 1000, // 30 minutes
    })
    find(resultId: number, feedbackId: number): Observable<LongFeedbackResponse> {
        return this.http.get<LongFeedbackText>(`api/results/${resultId}/feedbacks/${feedbackId}/long-feedback`, { observe: 'response' });
    }
}
