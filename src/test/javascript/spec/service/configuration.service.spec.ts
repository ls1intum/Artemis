import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ConfigurationService } from 'app/admin/configuration/configuration.service';
import { Bean, ConfigProps, Env, PropertySource } from 'app/admin/configuration/configuration.model';
import { provideHttpClient } from '@angular/common/http';

describe('Service Tests', () => {
    describe('Logs Service', () => {
        let service: ConfigurationService;
        let httpMock: HttpTestingController;
        let expectedResult: Bean[] | PropertySource[] | null;

        beforeEach(() => {
            TestBed.configureTestingModule({
                providers: [provideHttpClient(), provideHttpClientTesting()],
            });

            expectedResult = null;
            service = TestBed.inject(ConfigurationService);
            httpMock = TestBed.inject(HttpTestingController);
        });

        afterEach(() => {
            httpMock.verify();
        });

        describe('Service methods', () => {
            it('should get the config', () => {
                const bean: Bean = {
                    prefix: 'jhipster',
                    properties: {
                        clientApp: {
                            name: 'jhipsterApp',
                        },
                    },
                };
                const configProps: ConfigProps = {
                    contexts: {
                        jhipster: {
                            beans: {
                                'tech.jhipster.config.JHipsterProperties': bean,
                            },
                        },
                    },
                };
                service.getBeans().subscribe((received) => (expectedResult = received));

                const req = httpMock.expectOne({ method: 'GET' });
                req.flush(configProps);
                expect(expectedResult).toEqual([bean]);
            });

            it('should get the env', () => {
                const propertySources: PropertySource[] = [
                    {
                        name: 'server.ports',
                        properties: {
                            'local.server.port': {
                                value: '8080',
                            },
                        },
                    },
                ];
                const env: Env = { propertySources };
                service.getPropertySources().subscribe((received) => (expectedResult = received));

                const req = httpMock.expectOne({ method: 'GET' });
                req.flush(env);
                expect(expectedResult).toEqual(propertySources);
            });
        });
    });
});
