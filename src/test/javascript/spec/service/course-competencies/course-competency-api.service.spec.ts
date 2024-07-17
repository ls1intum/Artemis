import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';

describe('CourseCompetencyApiService', () => {
    let httpClient: HttpTestingController;
    let courseCompetencyApiService: CourseCompetencyApiService;

    const baseUrl = 'api';

    const courseId = 1;
    const courseCompetencyId = 2;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            providers: [provideHttpClient(), provideHttpClientTesting(), CourseCompetencyApiService],
        });

        httpClient = TestBed.inject(HttpTestingController);
        courseCompetencyApiService = TestBed.inject(CourseCompetencyApiService);
    });

    afterEach(() => {
        httpClient.verify();
    });

    it('should get course competencies by course id', async () => {
        const methodCall = courseCompetencyApiService.getCourseCompetenciesByCourseId(courseId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/${courseId}/course-competencies` });

        response.flush({});
        await methodCall;
    });

    it('should get course competency by id', async () => {
        const methodCall = courseCompetencyApiService.getCourseCompetencyById(courseId, courseCompetencyId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/${courseId}/course-competencies/${courseCompetencyId}` });

        response.flush({});
        await methodCall;
    });

    it('should get course competency progress with refresh', async () => {
        const methodCall = courseCompetencyApiService.getCourseCompetencyProgressById(courseId, courseCompetencyId, true);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/${courseId}/course-competencies/${courseCompetencyId}/student-progress?refresh=true` });

        response.flush({});
        await methodCall;
    });

    it('should get course competency without refresh', async () => {
        const methodCall = courseCompetencyApiService.getCourseCompetencyProgressById(courseId, courseCompetencyId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/${courseId}/course-competencies/${courseCompetencyId}/student-progress?refresh=false` });

        response.flush({});
        await methodCall;
    });

    it('should get JoL', async () => {
        const methodCall = courseCompetencyApiService.getJoL(courseId, courseCompetencyId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/${courseId}/course-competencies/${courseCompetencyId}/jol` });

        response.flush({});
        await methodCall;
    });
});
