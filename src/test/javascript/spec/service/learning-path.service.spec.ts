import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { ArtemisTestModule } from '../test.module';
import { HttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

describe('LearningPathService', () => {
    let learningPathService: LearningPathService;
    let httpService: HttpClient;
    let putStub: jest.SpyInstance;

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
            });
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
