/**
 * Vitest tests for ConfigurationService.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { ConfigurationService } from 'app/core/admin/configuration/configuration.service';
import { Bean, ConfigProps, Env, PropertySource } from 'app/core/admin/configuration/configuration.model';

describe('ConfigurationService', () => {
    setupTestBed({ zoneless: true });

    let service: ConfigurationService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(ConfigurationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should get beans from config props', () => {
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

        let result: Bean[] | undefined;
        service.getBeans().subscribe((received) => (result = received));

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe('management/configprops');
        req.flush(configProps);

        expect(result).toEqual([bean]);
    });

    it('should get property sources from env', () => {
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

        let result: PropertySource[] | undefined;
        service.getPropertySources().subscribe((received) => (result = received));

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe('management/env');
        req.flush(env);

        expect(result).toEqual(propertySources);
    });
});
