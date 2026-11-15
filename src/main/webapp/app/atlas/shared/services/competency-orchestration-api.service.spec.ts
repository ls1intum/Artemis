import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CompetencyOrchestrationApiService } from 'app/atlas/shared/services/competency-orchestration-api.service';
import { CompetencyOrchestrationResultDTO, CompetencyOrchestrationStatus } from 'app/atlas/shared/dto/competency-orchestration-dto';

describe('CompetencyOrchestrationApiService', () => {
    let service: CompetencyOrchestrationApiService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(CompetencyOrchestrationApiService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('should POST run for exercise', async () => {
        const expected: CompetencyOrchestrationResultDTO = { status: CompetencyOrchestrationStatus.Success, summary: 'ok', appliedActions: [] };
        const call = service.runForExercise(7);
        const req = httpMock.expectOne({ method: 'POST', url: 'api/atlas/orchestrator/exercises/7/run' });
        req.flush(expected);
        expect(await call).toEqual(expected);
    });

    it('should POST run for lecture unit', async () => {
        const expected: CompetencyOrchestrationResultDTO = { status: CompetencyOrchestrationStatus.Success, summary: 'already correct' };
        const call = service.runForLectureUnit(9);
        const req = httpMock.expectOne({ method: 'POST', url: 'api/atlas/orchestrator/lecture-units/9/run' });
        req.flush(expected);
        expect(await call).toEqual(expected);
    });
});
