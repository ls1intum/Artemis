import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { ArtemisTestModule } from '../test.module';
import { HttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { LearningPathStorageService } from 'app/course/learning-paths/participate/learning-path-storage.service';

describe('LearningPathService', () => {
    let learningPathService: LearningPathService;
    let storageService: LearningPathStorageService;
    let httpService: HttpClient;
    let putStub: jest.SpyInstance;
    let getStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [{ provide: HttpClient, useClass: MockHttpService }],
        })
            .compileComponents()
            .then(() => {
                httpService = TestBed.inject(HttpClient);
                storageService = TestBed.inject(LearningPathStorageService);
                learningPathService = new LearningPathService(httpService, storageService);
                putStub = jest.spyOn(httpService, 'put');
                getStub = jest.spyOn(httpService, 'get');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should send a request to the server to enable learning paths for course', () => {
        learningPathService.enableLearningPaths(1).subscribe();
        expect(putStub).toHaveBeenCalledExactlyOnceWith('api/courses/1/learning-paths/enable', null, { observe: 'response' });
    });

    it('should send request to the server to generate missing learning paths for course', () => {
        learningPathService.generateMissingLearningPathsForCourse(1).subscribe();
        expect(putStub).toHaveBeenCalledExactlyOnceWith('api/courses/1/learning-paths/generate-missing', null, { observe: 'response' });
    });

    it('should send a request to the server to get health status of learning paths for course', () => {
        learningPathService.getHealthStatusForCourse(1).subscribe();
        expect(getStub).toHaveBeenCalledExactlyOnceWith('api/courses/1/learning-path-health', { observe: 'response' });
    });

    it('should send a request to the server to get learning path information', () => {
        learningPathService.getLearningPath(1).subscribe();
        expect(getStub).toHaveBeenCalledExactlyOnceWith('api/learning-path/1', { observe: 'response' });
    });

    it('should send a request to the server to get ngx graph representation of learning path', () => {
        learningPathService.getLearningPathNgxGraph(1).subscribe();
        expect(getStub).toHaveBeenCalledExactlyOnceWith('api/learning-path/1/graph', { observe: 'response' });
    });

    it('should send a request to the server to get ngx path representation of learning path', () => {
        learningPathService.getLearningPathNgxPath(1).subscribe();
        expect(getStub).toHaveBeenCalledExactlyOnceWith('api/learning-path/1/path', { observe: 'response' });
    });

    it('should send a request to the server to get learning path id of the current user in the course', () => {
        learningPathService.getLearningPathId(1).subscribe();
        expect(getStub).toHaveBeenCalledExactlyOnceWith('api/courses/1/learning-path-id', { observe: 'response' });
    });

    it('should send a request to the server to get competency progress for learning path', () => {
        learningPathService.getCompetencyProgressForLearningPath(1).subscribe();
        expect(getStub).toHaveBeenCalledExactlyOnceWith('api/learning-path/1/competency-progress', { observe: 'response' });
    });
});
