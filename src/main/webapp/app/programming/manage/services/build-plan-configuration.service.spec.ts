import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { BuildPlanConfigurationService, UpdateBuildPlanConfiguration } from 'app/programming/manage/services/build-plan-configuration.service';

describe('BuildPlanConfigurationService', () => {
    let service: BuildPlanConfigurationService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [BuildPlanConfigurationService, provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(BuildPlanConfigurationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should PUT the build plan configuration to the build-config endpoint', () => {
        const configuration: UpdateBuildPlanConfiguration = {
            buildPlan: { phases: [{ name: 'compile', script: 'echo compile', condition: 'ALWAYS', forceRun: false, resultPaths: [] }], dockerImage: 'some-image' },
            timeoutSeconds: 120,
            dockerFlags: '{"network":"none"}',
        };

        service.updateBuildPlanConfiguration(42, configuration).subscribe();

        const req = httpMock.expectOne({ method: 'PUT', url: 'api/programming/programming-exercises/42/build-config' });
        expect(req.request.body).toEqual(configuration);
        req.flush({});
    });
});
