import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PrerequisiteApiService } from 'app/course/competencies/services/prerequisite-api.service';

describe('PrerequisiteApiService', () => {
    let httpClient: HttpTestingController;
    let prerequisiteApiService: PrerequisiteApiService;

    const baseUrl = 'api';

    const courseId = 1;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            providers: [provideHttpClient(), provideHttpClientTesting(), PrerequisiteApiService],
        });

        httpClient = TestBed.inject(HttpTestingController);
        prerequisiteApiService = TestBed.inject(PrerequisiteApiService);
    });

    afterEach(() => {
        httpClient.verify();
    });

    it('should get prerequisites by course id', async () => {
        const methodCall = prerequisiteApiService.getPrerequisitesByCourseId(courseId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/competencies/prerequisites` });

        response.flush({});
        await methodCall;
    });

    it('should get prerequisite by id', async () => {
        const prerequisiteId = 2;
        const methodCall = prerequisiteApiService.getPrerequisiteById(courseId, prerequisiteId);
        const response = httpClient.expectOne({
            method: 'GET',
            url: `${baseUrl}/courses/${courseId}/competencies/prerequisites/${prerequisiteId}`,
        });

        response.flush({});
        await methodCall;
    });
});
