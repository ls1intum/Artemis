import { WritableSignal } from '@angular/core';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { Observable, Subscription, debounceTime, switchMap, tap } from 'rxjs';
import { UI_RELOAD_TIME } from 'app/foundation/constants/exercise-exam-constants';
import { FinishedBuildJob } from 'app/localci/shared/entities/build-job.model';

interface FinishedBuildJobLoading {
    loading: WritableSignal<boolean>;
    apply: (jobs: FinishedBuildJob[], headers: HttpHeaders) => void;
    error: (error: unknown) => void;
}

/** Applies finished jobs and pagination headers with the same loading/error lifecycle on both queue pages. */
export function subscribeToFinishedBuildJobs(request: Observable<HttpResponse<FinishedBuildJob[]>>, target: FinishedBuildJobLoading): Subscription {
    return request.subscribe({
        next: (response) => {
            target.apply(response.body || [], response.headers);
            target.loading.set(false);
        },
        error: (error: unknown) => {
            target.error(error);
            target.loading.set(false);
        },
    });
}

/** Debounces reader input and cancels the previous request when the next search is ready. */
export function subscribeToFinishedBuildJobSearch(
    trigger: Observable<void>,
    fetch: () => Observable<HttpResponse<FinishedBuildJob[]>>,
    target: FinishedBuildJobLoading,
): Subscription {
    return subscribeToFinishedBuildJobs(
        trigger.pipe(
            debounceTime(UI_RELOAD_TIME),
            tap(() => target.loading.set(true)),
            switchMap(fetch),
        ),
        target,
    );
}
