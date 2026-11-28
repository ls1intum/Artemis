import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { MetisSettingsQueue } from 'app/metis/manage/exercise-settings/metis-settings-queue.service';

describe('MetisSettingsQueue', () => {
    let queue: MetisSettingsQueue;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        queue = TestBed.inject(MetisSettingsQueue);
    });

    it('should start a task only once the one queued before it has finished', () => {
        const first = new Subject<void>();
        const started: string[] = [];
        queue.enqueue(() => {
            started.push('first');
            return first;
        });
        queue.enqueue(() => {
            started.push('second');
            return of(undefined);
        });

        expect(started).toEqual(['first']);

        first.next();
        first.complete();

        expect(started).toEqual(['first', 'second']);
    });

    it('should go on with the next task after one that failed', () => {
        const started: string[] = [];
        queue.enqueue(() => {
            started.push('failing');
            return throwError(() => new Error('refused'));
        });
        queue.enqueue(() => {
            started.push('next');
            return of(undefined);
        });

        expect(started).toEqual(['failing', 'next']);
    });
});
