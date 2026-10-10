import { TestBed } from '@angular/core/testing';
import { Observable, Subject, of } from 'rxjs';
import { HyperionExerciseGenerationApi } from 'app/openapi/api/hyperion-exercise-generation-api';
import { ExerciseGenerationCapabilities } from 'app/openapi/model/exercise-generation-capabilities';
import { GenerationCapabilityRequestsService } from './generation-capability-requests.service';

describe('Generation capability request concurrency', () => {
    const requests = new Map<number, Subject<ExerciseGenerationCapabilities>>();
    const getCapabilities = vi.fn<(id: number) => Observable<ExerciseGenerationCapabilities>>((id) => {
        const response = new Subject<ExerciseGenerationCapabilities>();
        requests.set(id, response);
        return response;
    });
    let service: GenerationCapabilityRequestsService;

    beforeEach(() => {
        requests.clear();
        getCapabilities.mockClear();
        TestBed.configureTestingModule({ providers: [{ provide: HyperionExerciseGenerationApi, useValue: { getGenerationCapabilities: getCapabilities } }] });
        service = TestBed.inject(GenerationCapabilityRequestsService);
    });

    afterEach(() => {
        TestBed.resetTestingModule();
        vi.useRealTimers();
    });

    it('bounds a large exercise list and skips destroyed rows before making an HTTP request', () => {
        const subscriptions = Array.from({ length: 30 }, (_, index) => service.describe(index + 1).subscribe());
        expect(getCapabilities).toHaveBeenCalledTimes(4);
        subscriptions[4].unsubscribe();
        requests.get(1)!.complete();
        expect(getCapabilities).toHaveBeenLastCalledWith(6);
        expect(getCapabilities).not.toHaveBeenCalledWith(5);
        subscriptions.forEach((subscription) => subscription.unsubscribe());
        expect([...requests.values()].some((request) => request.observed)).toBe(false);
    });

    it('cancels active HTTP requests and immediately admits the next row', () => {
        const subscriptions = Array.from({ length: 5 }, (_, index) => service.describe(index + 1).subscribe());
        subscriptions[0].unsubscribe();
        expect(requests.get(1)!.observed).toBe(false);
        expect(getCapabilities).toHaveBeenLastCalledWith(5);
        subscriptions.forEach((subscription) => subscription.unsubscribe());
    });

    it('does not cache an earlier lifecycle decision', () => {
        const first = vi.fn();
        service.describe(42).subscribe(first);
        requests.get(42)!.next({ canAdapt: true });
        requests.get(42)!.complete();
        const second = vi.fn();
        service.describe(42).subscribe(second);
        requests.get(42)!.next({ canAdapt: false });
        expect(getCapabilities).toHaveBeenCalledTimes(2);
        expect(first).toHaveBeenCalledWith({ canAdapt: true });
        expect(second).toHaveBeenCalledWith({ canAdapt: false });
    });

    it('releases capacity after a hung request without ending the queue', () => {
        vi.useFakeTimers();
        const error = vi.fn();
        for (let id = 1; id <= 5; id++) service.describe(id).subscribe({ error });
        vi.advanceTimersByTime(10_000);
        expect(error).toHaveBeenCalledTimes(4);
        expect(getCapabilities).toHaveBeenLastCalledWith(5);
        requests.get(5)!.complete();
        getCapabilities.mockReturnValueOnce(of({ canAdapt: true }));
        const next = vi.fn();
        service.describe(6).subscribe(next);
        expect(next).toHaveBeenCalledWith({ canAdapt: true });
    });
});
