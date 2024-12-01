import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LearningObjectType } from 'app/entities/competency/learning-path.model';
import { provideHttpClient } from '@angular/common/http';
import { SearchTermPageableSearch, SortingOrder } from 'app/shared/table/pageable-table';

describe('LearningPathApiService', () => {
    let httpClient: HttpTestingController;
    let learningPathApiService: LearningPathApiService;

    const baseUrl = 'api';

    const learningPathId = 1;
    const courseId = 2;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [LearningPathApiService, provideHttpClient(), provideHttpClientTesting()],
        });

        learningPathApiService = TestBed.inject(LearningPathApiService);
        httpClient = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpClient.verify();
    });

    it('should get learning path for current user', async () => {
        const courseId = 1;

        const methodCall = learningPathApiService.getLearningPathForCurrentUser(courseId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/learning-path/me` });
        response.flush({});
        await methodCall;
    });

    it('should get learning path navigation', async () => {
        const methodCall = learningPathApiService.getLearningPathNavigation(learningPathId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/learning-path/${learningPathId}/navigation` });
        response.flush({});
        await methodCall;
    });

    it('should get relative learning path navigation', async () => {
        const learningObjectId = 2;
        const competencyId = 3;
        const learningObjectType = LearningObjectType.LECTURE;
        const methodCall = learningPathApiService.getRelativeLearningPathNavigation(learningPathId, learningObjectId, learningObjectType, competencyId);
        const response = httpClient.expectOne({
            method: 'GET',
            url: `${baseUrl}/learning-path/${learningPathId}/relative-navigation?learningObjectId=${learningObjectId}&learningObjectType=${learningObjectType}&competencyId=${competencyId}`,
        });
        response.flush({});
        await methodCall;
    });

    it('should start learning path for current user', async () => {
        const methodCall = learningPathApiService.startLearningPathForCurrentUser(learningPathId);
        const response = httpClient.expectOne({ method: 'PATCH', url: `${baseUrl}/learning-path/${learningPathId}/start` });
        response.flush({});
        await methodCall;
    });

    it('should get learning path navigation overview', async () => {
        const methodCall = learningPathApiService.getLearningPathNavigationOverview(learningPathId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/learning-path/${learningPathId}/navigation-overview` });
        response.flush({});
        await methodCall;
    });

    it('should generate learning path for current user', async () => {
        const methodCall = learningPathApiService.generateLearningPathForCurrentUser(courseId);
        const response = httpClient.expectOne({ method: 'POST', url: `${baseUrl}/courses/${courseId}/learning-path` });
        response.flush({});
        await methodCall;
    });

    it('should get learning path competency graph', async () => {
        const methodCall = learningPathApiService.getLearningPathCompetencyGraph(learningPathId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/learning-path/${learningPathId}/competency-graph` });
        response.flush({});
        await methodCall;
    });

    it('should get learning path instructor competency graph', async () => {
        const methodCall = learningPathApiService.getLearningPathInstructorCompetencyGraph(courseId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/learning-path/competency-instructor-graph` });
        response.flush({});
        await methodCall;
    });

    it('should get learning path competencies', async () => {
        const methodCall = learningPathApiService.getLearningPathCompetencies(learningPathId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/learning-path/${learningPathId}/competencies` });
        response.flush([]);
        await methodCall;
    });

    it('should get learning path competency learning objects', async () => {
        const competencyId = 3;
        const methodCall = learningPathApiService.getLearningPathCompetencyLearningObjects(learningPathId, competencyId);
        const response = httpClient.expectOne({
            method: 'GET',
            url: `${baseUrl}/learning-path/${learningPathId}/competencies/${competencyId}/learning-objects`,
        });
        response.flush([]);
        await methodCall;
    });

    it('should get learning paths configuration', async () => {
        const methodCall = learningPathApiService.getLearningPathsConfiguration(courseId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/learning-paths/configuration` });
        response.flush({});
        await methodCall;
    });

    it('should get learning path health status', async () => {
        const methodCall = learningPathApiService.getLearningPathHealthStatus(courseId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/learning-path-health` });
        response.flush({});
        await methodCall;
    });

    it('should put enable learning paths', async () => {
        const methodCall = learningPathApiService.enableLearningPaths(courseId);
        const response = httpClient.expectOne({ method: 'PUT', url: `${baseUrl}/courses/${courseId}/learning-paths/enable` });
        response.flush({});
        await methodCall;
    });

    it('should generate missing learning paths', async () => {
        const methodCall = learningPathApiService.generateMissingLearningPaths(courseId);
        const response = httpClient.expectOne({ method: 'PUT', url: `${baseUrl}/courses/${courseId}/learning-paths/generate-missing` });
        response.flush({});
        await methodCall;
    });

    it('should get learning path information', async () => {
        const pageable = <SearchTermPageableSearch>{
            pageSize: 10,
            page: 1,
            searchTerm: 'search',
            sortingOrder: SortingOrder.DESCENDING,
            sortedColumn: 'column',
        };
        const methodCall = learningPathApiService.getLearningPathInformation(courseId, pageable);
        const response = httpClient.expectOne({
            method: 'GET',
            url: `${baseUrl}/courses/${courseId}/learning-paths?pageSize=${pageable.pageSize}&page=${pageable.page}&sortingOrder=${pageable.sortingOrder}&searchTerm=${pageable.searchTerm}&sortedColumn=${pageable.sortedColumn}`,
        });
        response.flush({});
        await methodCall;
    });
});
