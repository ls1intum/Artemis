import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HyperionExerciseAdaptationService } from 'app/hyperion/services/hyperion-exercise-adaptation.service';
import { HyperionExerciseGenerationService } from 'app/hyperion/services/hyperion-exercise-generation.service';
import { AlertService } from 'app/foundation/service/alert.service';

describe('HyperionExerciseAdaptationService', () => {
    setupTestBed({ zoneless: true });
    let service: HyperionExerciseAdaptationService;
    let generationService: { generateExercise: ReturnType<typeof vi.fn> };
    let alertService: { info: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        generationService = { generateExercise: vi.fn().mockReturnValue(of({ jobId: 'job-1' })) };
        alertService = { info: vi.fn(), error: vi.fn() };

        TestBed.configureTestingModule({
            providers: [
                HyperionExerciseAdaptationService,
                { provide: HyperionExerciseGenerationService, useValue: generationService },
                { provide: AlertService, useValue: alertService },
            ],
        });
        service = TestBed.inject(HyperionExerciseAdaptationService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should start a run with the trimmed feedback and inform the instructor (single subscription by the host)', () => {
        // The returned stream is cold: the host subscribes exactly once, which sends the POST and triggers the info alert via tap.
        service.adaptExercise(42, '  address the signature mismatch  ')!.subscribe();

        expect(generationService.generateExercise).toHaveBeenCalledWith(42, 'address the signature mismatch');
        expect(alertService.info).toHaveBeenCalledWith('artemisApp.review.adaptExercise.started');
    });

    it('should return undefined and start nothing for empty feedback', () => {
        const result = service.adaptExercise(42, '   ');
        expect(result).toBeUndefined();
        expect(generationService.generateExercise).not.toHaveBeenCalled();
    });

    it('should surface an already-running alert on 409', () => {
        generationService.generateExercise.mockReturnValue(throwError(() => ({ status: 409 })));
        service.adaptExercise(42, 'feedback')!.subscribe({ error: () => {} });
        expect(alertService.error).toHaveBeenCalledWith('artemisApp.review.adaptExercise.alreadyRunning');
    });

    it('should surface a generic error alert on other failures', () => {
        generationService.generateExercise.mockReturnValue(throwError(() => ({ status: 500 })));
        service.adaptExercise(42, 'feedback')!.subscribe({ error: () => {} });
        expect(alertService.error).toHaveBeenCalledWith('artemisApp.review.adaptExercise.startError');
    });
});
