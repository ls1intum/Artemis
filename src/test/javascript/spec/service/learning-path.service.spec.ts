import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';

describe('LearningPathService', () => {
    let learningPathService: LearningPathService;
    let httpService: MockHttpService;
    let putStub: jest.SpyInstance;

    beforeEach(() => {
        httpService = new MockHttpService();
        learningPathService = new LearningPathService(httpService);
        putStub = jest.spyOn(httpService, 'put');
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should send a request to the server to activate the user', () => {
        learningPathService.enableLearningPaths(1).subscribe();
        expect(putStub).toHaveBeenCalledOnce();
        expect(putStub).toHaveBeenCalledWith('api/courses/1/learning-paths/enable', null, { observe: 'response' });
    });
});
