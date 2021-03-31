import { async } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonStub, stub } from 'sinon';
import { ActivateService } from 'app/account/activate/activate.service';
import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { HttpParams } from '@angular/common/http';

import { SERVER_API_URL } from 'app/app.constants';

chai.use(sinonChai);
const expect = chai.expect;

describe('ActivateService', () => {
    let activateService: ActivateService;
    let httpService: MockHttpService;
    let getStub: SinonStub;

    const getURL = SERVER_API_URL + 'api/activate';

    beforeEach(async(() => {
        httpService = new MockHttpService();
        // @ts-ignore
        activateService = new ActivateService(httpService);
        getStub = stub(httpService, 'get');
    }));

    afterEach(() => {
        getStub.restore();
    });

    it('should send a request to the server to activate the user', async () => {
        const key = 'key';

        await activateService.get(key);

        expect(getStub).to.have.been.calledOnceWithExactly(getURL, {
            params: new HttpParams().set('key', key),
        });
    });
});
