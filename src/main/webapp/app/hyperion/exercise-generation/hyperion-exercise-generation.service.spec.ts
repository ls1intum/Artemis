import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { Observable, Subject } from 'rxjs';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';

class MockWebsocketService {
    channels: string[] = [];
    subscribe(channel: string): Observable<unknown> {
        this.channels.push(channel);
        return new Subject<unknown>().asObservable();
    }
}

describe('HyperionExerciseGenerationService', () => {
    setupTestBed({ zoneless: true });

    let service: HyperionExerciseGenerationService;
    let httpMock: HttpTestingController;
    let websocket: MockWebsocketService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [HyperionExerciseGenerationService, provideHttpClient(), provideHttpClientTesting(), { provide: WebsocketService, useClass: MockWebsocketService }],
        });
        service = TestBed.inject(HyperionExerciseGenerationService);
        httpMock = TestBed.inject(HttpTestingController);
        websocket = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;
    });

    afterEach(() => httpMock.verify());

    it('starts an adaptation run with the explicit mode and selected feedback threads', () => {
        service.generate(42, { mode: 'ADAPT', prompt: 'do it', selectedFeedbackThreadIds: [7, 9] }).subscribe();
        const request = httpMock.expectOne('http://localhost:8080/api/hyperion/programming-exercises/42/generate-exercise');
        expect(request.request.method).toBe('POST');
        expect(request.request.body).toEqual({ mode: 'ADAPT', prompt: 'do it', selectedFeedbackThreadIds: [7, 9] });
        request.flush({ jobId: 'j1' });
    });

    it('requests the run status with observed response', () => {
        service.getStatus(42).subscribe();
        const request = httpMock.expectOne('http://localhost:8080/api/hyperion/programming-exercises/42/generate-exercise/status');
        expect(request.request.method).toBe('GET');
        request.flush({ jobId: 'j1', running: false, events: [] });
    });

    it('deletes the job to cancel it for the owner', () => {
        service.cancel(42, 'j1').subscribe();
        const request = httpMock.expectOne('http://localhost:8080/api/hyperion/programming-exercises/42/generate-exercise/jobs/j1');
        expect(request.request.method).toBe('DELETE');
        request.flush(null);
    });

    it('reverts the last generated change', () => {
        service.revertExerciseGeneration(42).subscribe((result) => expect(result.fullyReverted).toBe(true));
        const request = httpMock.expectOne('http://localhost:8080/api/hyperion/programming-exercises/42/generate-exercise/revert');
        expect(request.request.method).toBe('POST');
        request.flush({ fullyReverted: true, revertedRepositories: ['exercise', 'solution', 'tests'], completedAt: '2026-07-10T20:00:00Z' });
    });

    it('subscribes to the owner-private stream topic', () => {
        service.subscribeToStream('j1').subscribe();
        expect(websocket.channels).toContain('/user/topic/hyperion/exercise-generation/jobs/j1');
    });
});
