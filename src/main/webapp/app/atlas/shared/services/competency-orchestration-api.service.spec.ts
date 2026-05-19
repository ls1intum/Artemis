import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { CompetencyOrchestrationApiService } from 'app/atlas/shared/services/competency-orchestration-api.service';
import { CompetencyOrchestrationResultDTO, CompetencyOrchestrationStatus } from 'app/atlas/shared/dto/competency-orchestration-dto';

describe('CompetencyOrchestrationApiService', () => {
    setupTestBed({ zoneless: true });
    let service: CompetencyOrchestrationApiService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(CompetencyOrchestrationApiService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('should POST run for programming exercise', async () => {
        const expected: CompetencyOrchestrationResultDTO = { status: CompetencyOrchestrationStatus.Success, summary: 'ok', appliedActions: [] };
        const call = service.runForProgrammingExercise(7);
        const req = httpMock.expectOne({ method: 'POST', url: 'api/atlas/orchestrator/programming-exercises/7/run' });
        req.flush(expected);
        expect(await call).toEqual(expected);
    });
});
