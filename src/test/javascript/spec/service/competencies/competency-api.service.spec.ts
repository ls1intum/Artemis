import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CompetencyApiService } from 'app/course/competencies/services/competency-api.service';

describe('CompetencyApiService', () => {
    let httpClient: HttpTestingController;
    let competenciesApiService: CompetencyApiService;

    const baseUrl = 'api';

    const courseId = 1;
    const competencyId = 2;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            providers: [provideHttpClient(), provideHttpClientTesting(), CompetencyApiService],
        });

        httpClient = TestBed.inject(HttpTestingController);
        competenciesApiService = TestBed.inject(CompetencyApiService);
    });

    afterEach(() => {
        httpClient.verify();
    });

    it('should get competencies by course id', async () => {
        const methodCall = competenciesApiService.getCompetenciesByCourseId(courseId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/competencies` });

        response.flush({});
        await methodCall;
    });

    it('should get competency by id', async () => {
        const methodCall = competenciesApiService.getCompetencyById(courseId, competencyId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/competencies/${competencyId}` });

        response.flush({});
        await methodCall;
    });

    it('should get JoL', async () => {
        const methodCall = competenciesApiService.getJoL(courseId, competencyId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/competencies/${competencyId}/jol` });

        response.flush({});
        await methodCall;
    });

    it('should get competency progress with refresh', async () => {
        const methodCall = competenciesApiService.getCompetencyProgress(courseId, competencyId, true);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/competencies/${competencyId}/student-progress?refresh=true` });

        response.flush({});
        await methodCall;
    });

    it('should get competency progress without refresh', async () => {
        const methodCall = competenciesApiService.getCompetencyProgress(courseId, competencyId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/competencies/${competencyId}/student-progress?refresh=false` });

        response.flush({});
        await methodCall;
    });
});
