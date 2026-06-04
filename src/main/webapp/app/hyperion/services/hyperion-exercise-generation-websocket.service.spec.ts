import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it } from 'vitest';
import { Observable, Subject } from 'rxjs';
import { ExerciseGenerationEvent, HyperionExerciseGenerationWebsocketService } from 'app/hyperion/services/hyperion-exercise-generation-websocket.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';

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

describe('HyperionExerciseGenerationWebsocketService', () => {
    setupTestBed({ zoneless: true });

    let service: HyperionExerciseGenerationWebsocketService;
    let mockWs: MockWebsocketService;

    const channelFor = (jobId: string) => `/user/topic/hyperion/exercise-generation/jobs/${jobId}`;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [HyperionExerciseGenerationWebsocketService, { provide: WebsocketService, useClass: MockWebsocketService }],
        });
        service = TestBed.inject(HyperionExerciseGenerationWebsocketService);
        mockWs = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;
    });

    it('subscribes to a job and forwards events on the exercise-generation channel', () => {
        const jobId = 'job-1';
        const events: ExerciseGenerationEvent[] = [];
        service.subscribeToJob(jobId).subscribe((e) => events.push(e));

        const ch = channelFor(jobId);
        expect(mockWs.subscribeCalls).toContain(ch);

        const subj = mockWs.subjects.get(ch)!;
        subj.next({ type: 'STARTED' });
        subj.next({ type: 'DONE', completionStatus: 'SUCCESS', message: 'saved' });

        expect(events).toEqual([{ type: 'STARTED' }, { type: 'DONE', completionStatus: 'SUCCESS', message: 'saved' }]);
    });

    it('reuses the existing subscription for the same job', () => {
        const jobId = 'job-dup';
        service.subscribeToJob(jobId);
        service.subscribeToJob(jobId);
        expect(mockWs.subscribeCalls.filter((c) => c === channelFor(jobId))).toHaveLength(1);
    });

    it('unsubscribes and tears down the channel', () => {
        const jobId = 'job-x';
        service.subscribeToJob(jobId).subscribe();
        service.unsubscribeFromJob(jobId);
        expect(mockWs.unsubscribeCalls).toContain(channelFor(jobId));
    });
});
