import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { ArtemisTestModule } from '../test.module';
import { HttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';

describe('ScienceService', () => {
    let scienceService: ScienceService;
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
                scienceService = new ScienceService(httpService);
                putStub = jest.spyOn(httpService, 'put');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should send a request to the server to log event', () => {
        scienceService.logEvent(ScienceEventType.LECTURE__OPEN);
        expect(putStub).toHaveBeenCalledExactlyOnceWith('api/science', ScienceEventType.LECTURE__OPEN);
    });
});
