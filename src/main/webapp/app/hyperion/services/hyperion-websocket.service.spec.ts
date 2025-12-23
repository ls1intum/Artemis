import { TestBed } from '@angular/core/testing';
import { Observable, Subject } from 'rxjs';
import { HyperionEvent, HyperionWebsocketService } from 'app/hyperion/services/hyperion-websocket.service';
import { WebsocketService } from 'app/shared/service/websocket.service';

class MockWebsocketService {
    subjects = new Map<string, Subject<any>>();
    subscribeCalls: string[] = [];
    unsubscribeCalls: string[] = [];

    subscribe(channel: string): Observable<any> {
        this.subscribeCalls.push(channel);
        let subj = this.subjects.get(channel);
        if (!subj) {
            subj = new Subject<any>();
            this.subjects.set(channel, subj);
        }
        return new Observable((subscriber) => {
            const subscription = subj!.subscribe(subscriber);
            return () => {
                this.unsubscribeCalls.push(channel);
                subscription.unsubscribe();
            };
        });
    }
}

describe('HyperionWebsocketService', () => {
    let service: HyperionWebsocketService;
    let mockWs: MockWebsocketService;

    const channelFor = (jobId: string) => `/user/topic/hyperion/code-generation/jobs/${jobId}`;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [HyperionWebsocketService, { provide: WebsocketService, useClass: MockWebsocketService }],
        });

        service = TestBed.inject(HyperionWebsocketService);
        mockWs = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;
    });

    it('subscribes to a job and forwards websocket events', () => {
        const jobId = 'job-1';
        const events: HyperionEvent[] = [];
        const errors: any[] = [];
        const completes: boolean[] = [];

        service.subscribeToJob(jobId).subscribe({
            next: (e) => events.push(e),
            error: (err) => errors.push(err),
            complete: () => completes.push(true),
        });

        const ch = channelFor(jobId);
        expect(mockWs.subscribeCalls).toContain(ch);

        const subj = mockWs.subjects.get(ch)!;
        subj.next({ type: 'STARTED' });
        subj.next({ type: 'PROGRESS', iteration: 2 });

        expect(events).toEqual([{ type: 'STARTED' }, { type: 'PROGRESS', iteration: 2 }]);
        expect(errors).toHaveLength(0);
        expect(completes).toHaveLength(0);
    });

    it('reuses existing subscription for same job', () => {
        const jobId = 'job-dup';
        const ch = channelFor(jobId);

        const first$ = service.subscribeToJob(jobId);
        const second$ = service.subscribeToJob(jobId);

        // Both observables should emit the same underlying events
        const received1: HyperionEvent[] = [];
        const received2: HyperionEvent[] = [];
        first$.subscribe((e) => received1.push(e));
        second$.subscribe((e) => received2.push(e));

        const subj = mockWs.subjects.get(ch)!;
        subj.next({ type: 'PROGRESS', iteration: 1 });

        expect(received1).toStrictEqual([{ type: 'PROGRESS', iteration: 1 }]);
        expect(received2).toStrictEqual([{ type: 'PROGRESS', iteration: 1 }]);

        // Only one subscribe call expected for the channel
        const subsForChannel = mockWs.subscribeCalls.filter((c) => c === ch);
        expect(subsForChannel).toHaveLength(1);
    });

    it('cleans up map on websocket error and allows resubscribe', () => {
        const jobId = 'job-error';
        const ch = channelFor(jobId);
        const errors: any[] = [];

        service.subscribeToJob(jobId).subscribe({ error: (err) => errors.push(err) });
        const subj = mockWs.subjects.get(ch)!;

        subj.error(new Error('boom'));
        expect(errors).toHaveLength(1);

        // Resubscribe should create a new subject and subscribe call
        service.subscribeToJob(jobId);
        const subsForChannel = mockWs.subscribeCalls.filter((c) => c === ch);
        expect(subsForChannel).toHaveLength(2);
    });

    it('cleans up map on websocket complete and allows resubscribe', () => {
        const jobId = 'job-complete';
        const ch = channelFor(jobId);
        let completed = false;

        service.subscribeToJob(jobId).subscribe({ complete: () => (completed = true) });
        const subj = mockWs.subjects.get(ch)!;

        subj.complete();
        expect(completed).toBeTrue();

        // Resubscribe should create a new subject and subscribe call
        service.subscribeToJob(jobId);
        const subsForChannel = mockWs.subscribeCalls.filter((c) => c === ch);
        expect(subsForChannel).toHaveLength(2);
    });

    it('unsubscribeFromJob unsubscribes and completes consumer', () => {
        const jobId = 'job-unsub';
        const ch = channelFor(jobId);
        let completed = 0;
        const events: HyperionEvent[] = [];

        service.subscribeToJob(jobId).subscribe({
            next: (e) => events.push(e),
            complete: () => completed++,
        });

        service.unsubscribeFromJob(jobId);

        // Consumer completed and underlying websocket unsubscribed
        expect(completed).toBe(1);
        expect(mockWs.unsubscribeCalls).toContain(ch);

        // Further websocket messages do not reach consumer (subject was completed)
        const subj = mockWs.subjects.get(ch)!;
        subj.next({ type: 'PROGRESS', iteration: 99 });
        expect(events).toHaveLength(0);
    });

    it('ngOnDestroy unsubscribes and completes for all active jobs', () => {
        const jobA = 'job-a';
        const jobB = 'job-b';
        const chA = channelFor(jobA);
        const chB = channelFor(jobB);
        let completeA = false;
        let completeB = false;

        service.subscribeToJob(jobA).subscribe({ complete: () => (completeA = true) });
        service.subscribeToJob(jobB).subscribe({ complete: () => (completeB = true) });

        service.ngOnDestroy();

        expect(completeA).toBeTrue();
        expect(completeB).toBeTrue();
        expect(mockWs.unsubscribeCalls).toEqual(expect.arrayContaining([chA, chB]));
    });
});
