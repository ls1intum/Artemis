import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { ArTEMiSTestModule } from '../../test.module';
import { ArTEMiSSharedModule } from 'app/shared';
import { SERVER_API_URL } from 'app/app.constants';
import { GuidedTourSettings } from 'app/guided-tour/guided-tour-settings.model';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';

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
                expect(req.request.url).toEqual(`${resourceUrl}`);
            });

            it('should return json', () => {
                const req = httpMock.expectOne({ method: 'GET' });
                expect(req.request.responseType).toBe('json');
                expect(req.request.body).toBe(null);
            });
        });
    });
});
