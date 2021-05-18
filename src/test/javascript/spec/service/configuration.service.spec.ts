import { TestBed, tick, fakeAsync } from '@angular/core/testing';

import { JhiConfigurationService } from 'app/admin/configuration/configuration.service';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

describe('Logs Service', () => {
    let service: JhiConfigurationService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });

        service = TestBed.inject(JhiConfigurationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it('should call correct URL', () => {
            service.get().subscribe(() => {});

            const req = httpMock.expectOne({ method: 'GET' });
            const resourceUrl = SERVER_API_URL + 'management/configprops';
            expect(req.request.url).toEqual(resourceUrl);
        });

        it('should get the config', fakeAsync(() => {
            const angularConfig = {
                contexts: {
                    angular: {
                        beans: ['test2'],
                    },
                },
            };

            const expected = angularConfig['contexts']['angular']['beans'];

            service.get().subscribe((received) => {
                expect(received).toEqual(expected);
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(angularConfig);
            tick();
        }));

        it('should get the env', fakeAsync(() => {
            const propertySources = {
                propertySources: [
                    { name: 'test1', properties: { testA: { value: 'AAA' } } },
                    { name: 'test2', properties: { testB: { value: 'BBB' } } },
                ],
            };

            const expectedResult = {
                test1: [{ key: 'testA', val: 'AAA' }],
                test2: [{ key: 'testB', val: 'BBB' }],
            };

            service.getEnv().subscribe((received) => {
                expect(received).toEqual(expectedResult);
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(propertySources);
            tick();
        }));
    });
});
