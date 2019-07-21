import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';

import { ArTEMiSTestModule } from '../../test.module';
import { ArTEMiSSharedModule } from 'app/shared';
import { SERVER_API_URL } from 'app/app.constants';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('Service Tests', () => {
    describe('GuidedTourService', () => {
        let service: GuidedTourService;
        let httpMock: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule, ArTEMiSSharedModule, HttpClientTestingModule],
                providers: [GuidedTourService],
            });

            service = TestBed.get(GuidedTourService);
            httpMock = TestBed.get(HttpTestingController);
        });

        afterEach(() => {
            httpMock.verify();
        });

        describe('Service methods', () => {
            it('should call correct URL', () => {
                const req = httpMock.expectOne({ method: 'GET' });
                const resourceUrl = SERVER_API_URL + 'api/guided-tour-settings';
                expect(req.request.url).equal(`${resourceUrl}`);
            });

            it('should return json', () => {
                const req = httpMock.expectOne({ method: 'GET' });
                expect(req.request.responseType).to.equal('json');
            });
        });
    });
});
