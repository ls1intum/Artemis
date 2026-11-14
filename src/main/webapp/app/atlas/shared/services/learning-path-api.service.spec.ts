import { LearningPathApiService } from 'app/atlas/shared/services/learning-path-api.service';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { CompetencyGraphNodeValueType, LearningObjectType } from 'app/atlas/shared/entities/learning-path.model';
import { HealthStatus, LearningPathHealthDTO } from 'app/atlas/shared/entities/learning-path-health.model';
import { CompetencyRelationType } from 'app/atlas/shared/entities/competency.model';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { SearchTermPageableSearch, SortingOrder } from 'app/foundation/pagination/pageable-table';
import { LearningPathApi } from 'app/openapi/api/learning-path-api';
import { LearningPathNavigationObject } from 'app/openapi/model/learning-path-navigation-object';
import { lastValueFrom } from 'rxjs';

describe('LearningPathApiService', () => {
    let httpClient: HttpTestingController;
    let learningPathApiService: LearningPathApiService;

    const baseUrl = '/api/atlas';

    const learningPathId = 1;
    const courseId = 2;

    const lectureObject: LearningPathNavigationObject = { id: 4, completed: true, name: 'Lecture unit', competencyId: 3, type: 'LECTURE', repeatedTest: false, unreleased: false };
    // An unreleased learning object has an empty name, which the server omits.
    const unreleasedExerciseObject: LearningPathNavigationObject = { id: 5, completed: false, competencyId: 3, type: 'EXERCISE', repeatedTest: true, unreleased: true };

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

    function expectBodylessRequest(method: string, url: string): TestRequest {
        const request = httpClient.expectOne({ method, url });
        expect(request.request.body).toBeNull();
        return request;
    }

    it('should get learning path for current user', async () => {
        const methodCall = learningPathApiService.getLearningPathForCurrentUser(courseId);
        httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/learning-path/me` }).flush({ id: learningPathId, startedByStudent: false, progress: 0 });
        await expect(methodCall).resolves.toEqual({ id: learningPathId, startedByStudent: false, progress: 0 });
    });

    it('should reject with the original error if the current user has no learning path', async () => {
        const methodCall = learningPathApiService.getLearningPathForCurrentUser(courseId);
        httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/learning-path/me` }).flush(null, { status: 404, statusText: 'Not Found' });
        await expect(methodCall).rejects.toBeInstanceOf(HttpErrorResponse);
        await expect(methodCall).rejects.toMatchObject({ status: 404 });
    });

    it('should get learning path navigation', async () => {
        const methodCall = learningPathApiService.getLearningPathNavigation(learningPathId);
        httpClient
            .expectOne({ method: 'GET', url: `${baseUrl}/learning-paths/${learningPathId}/navigation` })
            .flush({ predecessorLearningObject: lectureObject, currentLearningObject: unreleasedExerciseObject, progress: 50 });

        const navigation = await methodCall;

        expect(navigation).toEqual({
            predecessorLearningObject: { ...lectureObject, type: LearningObjectType.LECTURE },
            currentLearningObject: { ...unreleasedExerciseObject, name: undefined, type: LearningObjectType.EXERCISE },
            successorLearningObject: undefined,
            progress: 50,
        });
    });

    it('should keep a navigation without learning objects', async () => {
        const methodCall = learningPathApiService.getLearningPathNavigation(learningPathId);
        httpClient.expectOne({ method: 'GET', url: `${baseUrl}/learning-paths/${learningPathId}/navigation` }).flush({ progress: 0 });

        const navigation = await methodCall;

        expect(navigation.progress).toBe(0);
        expect(navigation.predecessorLearningObject).toBeUndefined();
        expect(navigation.currentLearningObject).toBeUndefined();
        expect(navigation.successorLearningObject).toBeUndefined();
    });

    it.each([LearningObjectType.LECTURE, LearningObjectType.EXERCISE])(
        'should get relative learning path navigation for a %s without sending repeatedTest',
        async (learningObjectType) => {
            const learningObjectId = 2;
            const competencyId = 3;
            const methodCall = learningPathApiService.getRelativeLearningPathNavigation(learningPathId, learningObjectId, learningObjectType, competencyId);
            const request = httpClient.expectOne((req) => req.method === 'GET' && req.url.startsWith(`${baseUrl}/learning-paths/${learningPathId}/relative-navigation?`));
            const query = new URL(request.request.urlWithParams, 'http://localhost').searchParams;
            expect([...query.keys()].sort()).toEqual(['competencyId', 'learningObjectId', 'learningObjectType']);
            expect(query.get('learningObjectId')).toBe(String(learningObjectId));
            expect(query.get('learningObjectType')).toBe(learningObjectType);
            expect(query.get('competencyId')).toBe(String(competencyId));
            request.flush({ currentLearningObject: lectureObject, successorLearningObject: unreleasedExerciseObject, progress: 25 });

            const navigation = await methodCall;

            expect(navigation.currentLearningObject?.type).toBe(LearningObjectType.LECTURE);
            expect(navigation.successorLearningObject?.type).toBe(LearningObjectType.EXERCISE);
        },
    );

    it.each([false, true])('should send repeatedTest=%s when the generated operation is called with it', async (repeatedTest) => {
        const learningPathApi = TestBed.inject(LearningPathApi);
        const call = lastValueFrom(learningPathApi.getRelativeLearningPathNavigation(learningPathId, 2, 'EXERCISE', 3, repeatedTest));
        const request = httpClient.expectOne((req) => req.url.startsWith(`${baseUrl}/learning-paths/${learningPathId}/relative-navigation?`));
        expect(new URL(request.request.urlWithParams, 'http://localhost').searchParams.get('repeatedTest')).toBe(String(repeatedTest));
        request.flush({ progress: 0 });
        await call;
    });

    it('should start learning path for current user', async () => {
        const methodCall = learningPathApiService.startLearningPathForCurrentUser(learningPathId);
        expectBodylessRequest('PATCH', `${baseUrl}/learning-paths/${learningPathId}/start`).flush(null, { status: 204, statusText: 'No Content' });
        await expect(methodCall).resolves.toBeUndefined();
    });

    it('should reject with the original error if the learning path was already started', async () => {
        const methodCall = learningPathApiService.startLearningPathForCurrentUser(learningPathId);
        expectBodylessRequest('PATCH', `${baseUrl}/learning-paths/${learningPathId}/start`).flush(null, { status: 409, statusText: 'Conflict' });
        await expect(methodCall).rejects.toMatchObject({ status: 409 });
    });

    it('should get learning path navigation overview', async () => {
        const methodCall = learningPathApiService.getLearningPathNavigationOverview(learningPathId);
        httpClient.expectOne({ method: 'GET', url: `${baseUrl}/learning-paths/${learningPathId}/navigation-overview` }).flush({ learningObjects: [lectureObject] });
        await expect(methodCall).resolves.toEqual({ learningObjects: [{ ...lectureObject, type: LearningObjectType.LECTURE }] });
    });

    it('should return an empty navigation overview if the server omits the learning objects', async () => {
        const methodCall = learningPathApiService.getLearningPathNavigationOverview(learningPathId);
        httpClient.expectOne({ method: 'GET', url: `${baseUrl}/learning-paths/${learningPathId}/navigation-overview` }).flush({});
        await expect(methodCall).resolves.toEqual({ learningObjects: [] });
    });

    it('should generate learning path for current user from the created response', async () => {
        const learningPath = { id: learningPathId, startedByStudent: false, progress: 0 };
        const methodCall = learningPathApiService.generateLearningPathForCurrentUser(courseId);
        expectBodylessRequest('POST', `${baseUrl}/courses/${courseId}/learning-path`).flush(learningPath, {
            status: 201,
            statusText: 'Created',
            headers: { Location: `/api/atlas/learning-paths/${learningPathId}` },
        });
        await expect(methodCall).resolves.toEqual(learningPath);
    });

    it('should reject if the created response has no body', async () => {
        const methodCall = learningPathApiService.generateLearningPathForCurrentUser(courseId);
        expectBodylessRequest('POST', `${baseUrl}/courses/${courseId}/learning-path`).flush(null, { status: 201, statusText: 'Created' });
        await expect(methodCall).rejects.toThrow('no body');
    });

    it('should map the learning path competency graph', async () => {
        const methodCall = learningPathApiService.getLearningPathCompetencyGraph(learningPathId);
        httpClient.expectOne({ method: 'GET', url: `${baseUrl}/learning-paths/${learningPathId}/competency-graph` }).flush({
            nodes: [
                { id: '1', label: 'Competency 1', softDueDate: '2026-11-13T10:15:00+01:00', value: 40, valueType: 'MASTERY_PROGRESS' },
                { id: '2', label: 'Competency 2', value: 0, valueType: 'MASTERY_PROGRESS' },
            ],
            edges: [{ id: '3', source: '1', target: '2', relationType: 'ASSUMES' }],
        });

        const graph = await methodCall;

        expect(graph.nodes).toHaveLength(2);
        expect(graph.nodes[0].softDueDate?.toISOString()).toBe('2026-11-13T09:15:00.000Z');
        expect(graph.nodes[0].valueType).toBe(CompetencyGraphNodeValueType.MASTERY_PROGRESS);
        expect(graph.nodes[1]).toEqual({ id: '2', label: 'Competency 2', softDueDate: undefined, value: 0, valueType: CompetencyGraphNodeValueType.MASTERY_PROGRESS });
        expect(graph.nodes[1].dimension).toBeUndefined();
        expect(graph.edges).toEqual([{ id: '3', source: '1', target: '2', relationType: CompetencyRelationType.ASSUMES }]);
    });

    it('should map the instructor competency graph', async () => {
        const methodCall = learningPathApiService.getLearningPathInstructorCompetencyGraph(courseId);
        httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/learning-path/competency-instructor-graph` }).flush({
            nodes: [{ id: '1', label: 'Competency 1', value: 12.5, valueType: 'AVERAGE_MASTERY_PROGRESS' }],
            edges: [
                { id: '2', source: '1', target: '1', relationType: 'EXTENDS' },
                { id: '3', source: '1', target: '1', relationType: 'MATCHES' },
            ],
        });

        const graph = await methodCall;

        expect(graph.nodes[0].valueType).toBe(CompetencyGraphNodeValueType.AVERAGE_MASTERY_PROGRESS);
        expect(graph.nodes[0].value).toBe(12.5);
        expect(graph.edges?.map((edge) => edge.relationType)).toEqual([CompetencyRelationType.EXTENDS, CompetencyRelationType.MATCHES]);
    });

    it('should return no nodes and no edges for an empty graph', async () => {
        const methodCall = learningPathApiService.getLearningPathInstructorCompetencyGraph(courseId);
        httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/learning-path/competency-instructor-graph` }).flush({});
        await expect(methodCall).resolves.toEqual({ nodes: [], edges: undefined });
    });

    it('should reject a learning path graph node without a value', async () => {
        const methodCall = learningPathApiService.getLearningPathCompetencyGraph(learningPathId);
        httpClient.expectOne({ method: 'GET', url: `${baseUrl}/learning-paths/${learningPathId}/competency-graph` }).flush({ nodes: [{ id: '1', valueType: 'MASTERY_PROGRESS' }] });
        await expect(methodCall).rejects.toThrow('node value');
    });

    it('should get learning path competencies in server order', async () => {
        const competencies = [
            { id: 2, title: 'Second', masteryProgress: 0 },
            { id: 1, title: 'First', masteryProgress: 1 },
        ];
        const methodCall = learningPathApiService.getLearningPathCompetencies(learningPathId);
        httpClient.expectOne({ method: 'GET', url: `${baseUrl}/learning-paths/${learningPathId}/competencies` }).flush(competencies);
        await expect(methodCall).resolves.toEqual(competencies);
    });

    it('should get learning path competency learning objects in server order', async () => {
        const competencyId = 3;
        const methodCall = learningPathApiService.getLearningPathCompetencyLearningObjects(learningPathId, competencyId);
        httpClient
            .expectOne({ method: 'GET', url: `${baseUrl}/learning-paths/${learningPathId}/competencies/${competencyId}/learning-objects` })
            .flush([unreleasedExerciseObject, lectureObject]);

        const learningObjects = await methodCall;

        expect(learningObjects.map((learningObject) => learningObject.id)).toEqual([unreleasedExerciseObject.id, lectureObject.id]);
        expect(learningObjects.map((learningObject) => learningObject.type)).toEqual([LearningObjectType.EXERCISE, LearningObjectType.LECTURE]);
    });

    it('should return no learning objects for an empty competency', async () => {
        const methodCall = learningPathApiService.getLearningPathCompetencyLearningObjects(learningPathId, 3);
        httpClient.expectOne({ method: 'GET', url: `${baseUrl}/learning-paths/${learningPathId}/competencies/3/learning-objects` }).flush([]);
        await expect(methodCall).resolves.toEqual([]);
    });

    it('should get learning path health status', async () => {
        const methodCall = learningPathApiService.getLearningPathHealthStatus(courseId);
        httpClient
            .expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/learning-path-health` })
            .flush({ status: ['MISSING', 'NO_COMPETENCIES', 'NO_RELATIONS'], missingLearningPaths: 2 });

        const health = await methodCall;

        expect(health).toBeInstanceOf(LearningPathHealthDTO);
        expect(health.status).toEqual([HealthStatus.MISSING, HealthStatus.NO_COMPETENCIES, HealthStatus.NO_RELATIONS]);
        expect(health.missingLearningPaths).toBe(2);
    });

    it('should get an empty health status for a healthy course', async () => {
        const methodCall = learningPathApiService.getLearningPathHealthStatus(courseId);
        httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/learning-path-health` }).flush({});

        const health = await methodCall;

        expect(health.status).toEqual([]);
        expect(health.missingLearningPaths).toBeUndefined();
    });

    it('should put enable learning paths', async () => {
        const methodCall = learningPathApiService.enableLearningPaths(courseId);
        expectBodylessRequest('PUT', `${baseUrl}/courses/${courseId}/learning-paths/enable`).flush(null, { status: 200, statusText: 'OK' });
        await expect(methodCall).resolves.toBeUndefined();
    });

    it('should reject with the original error if learning paths are already enabled', async () => {
        const methodCall = learningPathApiService.enableLearningPaths(courseId);
        expectBodylessRequest('PUT', `${baseUrl}/courses/${courseId}/learning-paths/enable`).flush(null, { status: 400, statusText: 'Bad Request' });
        await expect(methodCall).rejects.toMatchObject({ status: 400 });
    });

    it('should generate missing learning paths', async () => {
        const methodCall = learningPathApiService.generateMissingLearningPaths(courseId);
        expectBodylessRequest('PUT', `${baseUrl}/courses/${courseId}/learning-paths/generate-missing`).flush(null, { status: 200, statusText: 'OK' });
        await expect(methodCall).resolves.toBeUndefined();
    });

    describe('getLearningPathInformation', () => {
        const learningPathInformation = { id: learningPathId, user: { name: 'Student Name', login: 'student1' }, progress: 0 };

        function expectSearchRequest(pageable: SearchTermPageableSearch): TestRequest {
            const request = httpClient.expectOne((req) => req.method === 'GET' && req.url.startsWith(`${baseUrl}/courses/${courseId}/learning-paths?`));
            const query = new URL(request.request.urlWithParams, 'http://localhost').searchParams;
            expect([...query.keys()].sort()).toEqual(['page', 'pageSize', 'searchTerm', 'sortedColumn', 'sortingOrder']);
            expect(query.get('page')).toBe(String(pageable.page));
            expect(query.get('pageSize')).toBe(String(pageable.pageSize));
            expect(query.get('sortingOrder')).toBe(pageable.sortingOrder);
            expect(query.get('sortedColumn')).toBe(pageable.sortedColumn);
            expect(query.get('searchTerm')).toBe(pageable.searchTerm);
            return request;
        }

        it.each([SortingOrder.ASCENDING, SortingOrder.DESCENDING])('should send the search sorted %s and return the page', async (sortingOrder) => {
            const pageable: SearchTermPageableSearch = { pageSize: 10, page: 1, searchTerm: '', sortingOrder, sortedColumn: 'ID' };
            const methodCall = learningPathApiService.getLearningPathInformation(courseId, pageable);
            expectSearchRequest(pageable).flush({ resultsOnPage: [learningPathInformation], numberOfPages: 3 });
            await expect(methodCall).resolves.toEqual({ resultsOnPage: [learningPathInformation], numberOfPages: 3 });
        });

        it('should encode a search term with reserved characters', async () => {
            const pageable: SearchTermPageableSearch = {
                pageSize: 100,
                page: 2,
                searchTerm: 'Max Mustermann+1 & co',
                sortingOrder: SortingOrder.DESCENDING,
                sortedColumn: 'USER_NAME',
            };
            const methodCall = learningPathApiService.getLearningPathInformation(courseId, pageable);
            expectSearchRequest(pageable).flush({ resultsOnPage: [learningPathInformation], numberOfPages: 1 });
            await methodCall;
        });

        it('should return an empty page if the server omits the results', async () => {
            const pageable: SearchTermPageableSearch = { pageSize: 100, page: 1, searchTerm: 'nobody', sortingOrder: SortingOrder.ASCENDING, sortedColumn: 'ID' };
            const methodCall = learningPathApiService.getLearningPathInformation(courseId, pageable);
            expectSearchRequest(pageable).flush({ numberOfPages: 0 });
            await expect(methodCall).resolves.toEqual({ resultsOnPage: [], numberOfPages: 0 });
        });
    });

    it('should get the average progress of a course', async () => {
        const averageProgress = { courseId, averageProgress: 0, totalStudents: 0 };
        const methodCall = learningPathApiService.getAverageProgressForCourse(courseId);
        httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/learning-path/average-progress` }).flush(averageProgress);
        await expect(methodCall).resolves.toEqual(averageProgress);
    });
});
