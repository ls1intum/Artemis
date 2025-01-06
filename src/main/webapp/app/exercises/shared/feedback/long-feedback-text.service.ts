import { Injectable, OnDestroy, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, Subject, Subscription, distinctUntilChanged, tap } from 'rxjs';
import { Cacheable } from 'ts-cacheable';
import { AccountService } from 'app/core/auth/account.service';

export type LongFeedbackResponse = HttpResponse<string>;

const logoutSubject = new Subject<void>();

@Injectable({ providedIn: 'root' })
export class LongFeedbackTextService implements OnDestroy {
    private http = inject(HttpClient);
    private accountService = inject(AccountService);

    private userChangeSubscription: Subscription;

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
        return this.http.get(`api/results/${resultId}/feedbacks/${feedbackId}/long-feedback`, { observe: 'response', responseType: 'text' });
    }
}
