import { DestroyRef, Service, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, Subject, Subscriber, catchError, defer, finalize, mergeMap, takeUntil, tap, timeout } from 'rxjs';
import { HyperionExerciseGenerationApi } from 'app/openapi/api/hyperion-exercise-generation-api';
import { ExerciseGenerationCapabilities } from 'app/openapi/model/exercise-generation-capabilities';

interface CapabilityRequest {
    exerciseId: number;
    subscriber: Subscriber<ExerciseGenerationCapabilities>;
    cancelled: Subject<void>;
}

/** Bounds list-page fan-out without caching mutable lifecycle permissions or sharing them between accounts. */
@Service()
export class GenerationCapabilityRequestsService {
    private readonly api = inject(HyperionExerciseGenerationApi);
    private readonly destroyRef = inject(DestroyRef);
    private readonly requests = new Subject<CapabilityRequest>();

    constructor() {
        this.requests
            .pipe(
                mergeMap(
                    (request) =>
                        defer(() => (request.subscriber.closed ? EMPTY : this.api.getGenerationCapabilities(request.exerciseId))).pipe(
                            timeout(10_000),
                            takeUntil(request.cancelled),
                            tap((capabilities) => request.subscriber.next(capabilities)),
                            catchError((error: unknown) => {
                                request.subscriber.error(error);
                                return EMPTY;
                            }),
                            finalize(() => request.subscriber.complete()),
                        ),
                    4,
                ),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe();
    }

    /** Requests current permissions; cancelling a row also cancels its queued or active HTTP request. */
    describe(exerciseId: number): Observable<ExerciseGenerationCapabilities> {
        return new Observable((subscriber) => {
            const cancelled = new Subject<void>();
            this.requests.next({ exerciseId, subscriber, cancelled });
            return () => {
                cancelled.next();
                cancelled.complete();
            };
        });
    }
}
