import { ActivateService } from 'app/account/activate/activate.service';
import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { HttpParams } from '@angular/common/http';

describe('ActivateService', () => {
    let activateService: ActivateService;
    let httpService: MockHttpService;
    let getStub: jest.SpyInstance;

    const getURL = 'api/public/activate';

    beforeEach(() => {
        httpService = new MockHttpService();
        // @ts-ignore
        activateService = new ActivateService(httpService);
        getStub = jest.spyOn(httpService, 'get');
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
