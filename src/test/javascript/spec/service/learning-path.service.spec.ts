import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { ArtemisTestModule } from '../test.module';
import { HttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

describe('LearningPathService', () => {
    let learningPathService: LearningPathService;
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
                learningPathService = new LearningPathService(httpService);
                putStub = jest.spyOn(httpService, 'put');
                getStub = jest.spyOn(httpService, 'get');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should send a request to the server to enable learning paths for course', () => {
        learningPathService.enableLearningPaths(1).subscribe();
        expect(putStub).toHaveBeenCalledOnce();
        expect(putStub).toHaveBeenCalledWith('api/courses/1/learning-paths/enable', null, { observe: 'response' });
    });

    it('should send request to the server to generate missing learning paths for course', () => {
        learningPathService.generateMissingLearningPathsForCourse(1).subscribe();
        expect(putStub).toHaveBeenCalledOnce();
        expect(putStub).toHaveBeenCalledWith('api/courses/1/learning-paths/generate-missing', null, { observe: 'response' });
    });

    it('should send a request to the server to get health status of learning paths for course', () => {
        learningPathService.getHealthStatusForCourse(1).subscribe();
        expect(getStub).toHaveBeenCalledOnce();
        expect(getStub).toHaveBeenCalledWith('api/courses/1/learning-path-health', { observe: 'response' });
    });

    it('should send a request to the server to get ngx representation of learning path', () => {
        learningPathService.getLearningPathNgxGraph(1).subscribe();
        expect(getStub).toHaveBeenCalledOnce();
        expect(getStub).toHaveBeenCalledWith('api/learning-path/1/graph', { observe: 'response' });
    });
});
