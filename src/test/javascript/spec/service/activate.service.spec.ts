import { ActivateService } from 'app/account/activate/activate.service';
import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { HttpClient, HttpParams } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

describe('ActivateService', () => {
    let activateService: ActivateService;
    let httpService: HttpClient;
    let getStub: jest.SpyInstance;

    const getURL = 'api/public/activate';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: HttpClient, useClass: MockHttpService }],
        })
            .compileComponents()
            .then(() => {
                httpService = TestBed.inject(HttpClient);
                activateService = TestBed.inject(ActivateService);
                getStub = jest.spyOn(httpService, 'get');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should send a request to the server to activate the user', () => {
        const key = 'key';

        activateService.get(key).subscribe();

        expect(getStub).toHaveBeenCalledOnce();
        expect(getStub).toHaveBeenCalledWith(getURL, {
            params: new HttpParams().set('key', key),
        });
    });
});
