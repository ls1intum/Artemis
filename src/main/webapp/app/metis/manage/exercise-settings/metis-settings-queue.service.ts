import { Service } from '@angular/core';
import { EMPTY, Observable, Subject, catchError, concatMap } from 'rxjs';

/**
 * Runs the reads and writes of the Metis settings sections one after another, in the order they were queued, across the
 * lifetimes of the sections. A section that closes, for instance when the edit form switches to the simple mode, still
 * sends the writes it queued, and a section opened afterwards reads the stored state only after them, so it neither shows
 * nor overwrites an older state.
 */
@Service()
export class MetisSettingsQueue {
    private readonly tasks = new Subject<() => Observable<unknown>>();

    constructor() {
        // Lives as long as the application, so that no queued write is dropped when the section that queued it closes; a
        // failing task handles its error itself, and the catchError only keeps the queue going should one not do so.
        this.tasks.pipe(concatMap((task) => task().pipe(catchError(() => EMPTY)))).subscribe();
    }

    /**
     * Queues a task. It starts once every task queued before it has finished.
     * @param task creates the request when its turn has come
     */
    enqueue(task: () => Observable<unknown>): void {
        this.tasks.next(task);
    }
}
