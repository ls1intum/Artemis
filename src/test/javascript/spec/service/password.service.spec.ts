import { async } from '@angular/core/testing';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { SinonStub, stub } from 'sinon';
import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { PasswordService } from 'app/account/password/password.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ActivateService', () => {
    let passwordService: PasswordService;
    let httpService: MockHttpService;
    let postStub: SinonStub;

    const postURL = SERVER_API_URL + 'api/account/change-password';

    beforeEach(async(() => {
        httpService = new MockHttpService();
        // @ts-ignore
        passwordService = new PasswordService(httpService);
        postStub = stub(httpService, 'post');
    }));

    afterEach(() => {
        postStub.restore();
    });

    it('should set a new password for the current user', async () => {
        const newPassword = 'newPassword';
        const currentPassword = 'currentPassword';

        await passwordService.save(newPassword, currentPassword);

        expect(postStub).to.have.been.calledOnceWithExactly(postURL, { currentPassword, newPassword });
    });
});
