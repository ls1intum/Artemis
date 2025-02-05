import { Injectable, OnDestroy, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, Subject, Subscription, distinctUntilChanged, tap } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';

export type LongFeedbackResponse = HttpResponse<string>;

const logoutSubject = new Subject<void>();

@Injectable({ providedIn: 'root' })
export class LongFeedbackTextService implements OnDestroy {
    private http = inject(HttpClient);
    private accountService = inject(AccountService);

    private userChangeSubscription: Subscription;
    debounceFind = BaseApiHttpService.debounce((resultId: number, feedbackId: number) => this.find(resultId, feedbackId), 300);

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

    find(resultId: number, feedbackId: number): Observable<LongFeedbackResponse> {
        return this.http.get(`api/results/${resultId}/feedbacks/${feedbackId}/long-feedback`, { observe: 'response', responseType: 'text' });
    }

    debounceFindWithReturn(resultId: number, feedbackId: number): Observable<LongFeedbackResponse> {
        return new Observable<LongFeedbackResponse>(() => {
            this.debounceFind(resultId, feedbackId);
        });
    }
}
