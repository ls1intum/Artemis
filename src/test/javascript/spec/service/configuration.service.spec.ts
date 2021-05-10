import { TestBed, tick, fakeAsync } from '@angular/core/testing';

import { JhiConfigurationService } from 'app/admin/configuration/configuration.service';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';

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
        //TODO!!! try to understand .get() method of services (YT-> or google)
        //TODO cont. -> try to maybe replace get with the other get... method but only if nothing works
        //TODO cont. -> better ask someone with more experience! (e.g. Nicolas Ruscher)
        it('should get the config', fakeAsync(() => {
            const angularConfig = {
                contexts: {
                    angular: {
                        beans: ['test2'],
                    },
                },
            };
            service.get().subscribe((received) => {
                expect(received).toEqual(angularConfig);
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(angularConfig);
            tick();
        }));

        it('should get the env', fakeAsync(() => {
            const propertySources = new HttpResponse({
                body: [
                    { name: 'test1', properties: 'test1' },
                    { name: 'test2', properties: 'test2' },
                ],
            });
            service.get().subscribe((received) => {
                expect(received.body[0]).toEqual(propertySources);
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(propertySources);
            tick();
        }));
    });
});
