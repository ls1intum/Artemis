import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { HyperionExerciseGenerationService } from 'app/hyperion/services/hyperion-exercise-generation.service';

describe('HyperionExerciseGenerationService', () => {
    setupTestBed({ zoneless: true });

    let service: HyperionExerciseGenerationService;
    let http: { post: ReturnType<typeof vi.fn>; get: ReturnType<typeof vi.fn>; delete: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        http = { post: vi.fn(), get: vi.fn(), delete: vi.fn() };
        TestBed.configureTestingModule({
            providers: [HyperionExerciseGenerationService, { provide: HttpClient, useValue: http }],
        });
        service = TestBed.inject(HyperionExerciseGenerationService);
    });

    it('posts to the generate-exercise endpoint with the prompt', () => {
        http.post.mockReturnValue(of({ jobId: 'job-1' }));
        let result: { jobId: string } | undefined;
        service.generateExercise(42, 'make a sorting exercise').subscribe((r) => (result = r));

        expect(http.post).toHaveBeenCalledWith('api/hyperion/programming-exercises/42/generate-exercise', { prompt: 'make a sorting exercise' });
        expect(result).toEqual({ jobId: 'job-1' });
    });

    it('omits the prompt when none is given', () => {
        http.post.mockReturnValue(of({ jobId: 'job-2' }));
        service.generateExercise(7).subscribe();
        expect(http.post).toHaveBeenCalledWith('api/hyperion/programming-exercises/7/generate-exercise', { prompt: undefined });
    });

    it('gets the run status for reconnection', () => {
        const status = { jobId: 'job-3', running: true, events: [{ type: 'PROGRESS', message: 'x' }] };
        http.get.mockReturnValue(of(status));
        let result: unknown;
        service.getStatus(42).subscribe((r) => (result = r));
        expect(http.get).toHaveBeenCalledWith('api/hyperion/programming-exercises/42/generate-exercise/status');
        expect(result).toEqual(status);
    });

    it('normalises an empty (204) status response to undefined', () => {
        http.get.mockReturnValue(of(null));
        let result: unknown = 'unset';
        service.getStatus(42).subscribe((r) => (result = r));
        expect(result).toBeUndefined();
    });

    it('cancels a job by id via DELETE', () => {
        http.delete.mockReturnValue(of(undefined));
        service.cancel(9, 'job-3').subscribe();
        expect(http.delete).toHaveBeenCalledWith('api/hyperion/programming-exercises/9/generate-exercise/jobs/job-3');
    });
});
