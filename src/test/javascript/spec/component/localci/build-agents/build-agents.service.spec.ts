import { TestBed } from '@angular/core/testing';

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { BuildJob } from 'app/entities/programming/build-job.model';
import dayjs from 'dayjs/esm';
import { lastValueFrom } from 'rxjs';
import { BuildAgentsService } from 'app/localci/build-agents/build-agents.service';
import { BuildAgentInformation } from '../../../../../../main/webapp/app/entities/programming/build-agent-information.model';
import { RepositoryInfo, TriggeredByPushTo } from 'app/entities/programming/repository-info.model';
import { JobTimingInfo } from 'app/entities/job-timing-info.model';
import { BuildConfig } from 'app/entities/programming/build-config.model';

describe('BuildAgentsService', () => {
    let service: BuildAgentsService;
    let httpMock: HttpTestingController;
    let element: BuildAgentInformation;

    const repositoryInfo: RepositoryInfo = {
        repositoryName: 'repo2',
        repositoryType: 'USER',
        triggeredByPushTo: TriggeredByPushTo.USER,
        assignmentRepositoryUri: 'https://some.uri',
        testRepositoryUri: 'https://some.uri',
        solutionRepositoryUri: 'https://some.uri',
        auxiliaryRepositoryUris: [],
        auxiliaryRepositoryCheckoutDirectories: [],
    };

    const jobTimingInfo: JobTimingInfo = {
        submissionDate: dayjs('2023-01-01'),
        buildStartDate: dayjs('2023-01-01'),
    };

    const buildConfig: BuildConfig = {
        dockerImage: 'someImage',
        commitHashToBuild: 'abc124',
        branch: 'main',
        programmingLanguage: 'Java',
        projectType: 'Maven',
        scaEnabled: false,
        sequentialTestRunsEnabled: false,
        testwiseCoverageEnabled: false,
        resultPaths: [],
    };

    const mockRunningJobs: BuildJob[] = [
        {
            id: '2',
            name: 'Build Job 2',
            buildAgent: { name: 'agent2', memberAddress: 'localhost:8080', displayName: 'Agent 2' },
            participationId: 102,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 3,
            repositoryInfo: repositoryInfo,
            jobTimingInfo: jobTimingInfo,
            buildConfig: buildConfig,
        },
        {
            id: '4',
            name: 'Build Job 4',
            buildAgent: { name: 'agent4', memberAddress: 'localhost:8080', displayName: 'Agent 4' },
            participationId: 104,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 2,
            repositoryInfo: repositoryInfo,
            jobTimingInfo: jobTimingInfo,
            buildConfig: buildConfig,
        },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useClass: MockTranslateService }],
        });
        service = TestBed.inject(BuildAgentsService);
        httpMock = TestBed.inject(HttpTestingController);
        element = new BuildAgentInformation();
        element.id = 1;
        element.buildAgent = { name: 'buildAgent1', memberAddress: 'localhost:8080', displayName: 'Build Agent 1' };
        element.maxNumberOfConcurrentBuildJobs = 3;
        element.numberOfCurrentBuildJobs = 1;
        element.runningBuildJobs = mockRunningJobs;
    });

    it('should return build agents', () => {
        const expectedResponse = [element]; // Expecting an array

        service.getBuildAgentSummary().subscribe((data) => {
            expect(data).toEqual(expectedResponse); // Check if the response matches expected
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/build-agents`);
        expect(req.request.method).toBe('GET');
        req.flush(expectedResponse); // Flush an array of elements
    });

    it('should return build agent details', () => {
        const expectedResponse = element;

        service.getBuildAgentDetails('buildAgent1').subscribe((data) => {
            expect(data).toEqual(expectedResponse);
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/build-agent?agentName=buildAgent1`);
        expect(req.request.method).toBe('GET');
        req.flush(expectedResponse);
    });

    it('should handle get build agent details error', async () => {
        const errorMessage = 'Failed to fetch build agent details buildAgent1';

        const observable = lastValueFrom(service.getBuildAgentDetails('buildAgent1'));

        const req = httpMock.expectOne(`${service.adminResourceUrl}/build-agent?agentName=buildAgent1`);
        expect(req.request.method).toBe('GET');
        req.flush({ message: errorMessage }, { status: 500, statusText: 'Internal Server Error' });

        try {
            await observable;
            throw new Error('expected an error, but got a success');
        } catch (error) {
            expect(error.message).toContain(errorMessage);
        }
    });

    it('should pause build agent', () => {
        service.pauseBuildAgent('buildAgent1').subscribe();

        const req = httpMock.expectOne(`${service.adminResourceUrl}/agent/buildAgent1/pause`);
        expect(req.request.method).toBe('PUT');
        req.flush({});
    });

    it('should resume build agent', () => {
        service.resumeBuildAgent('buildAgent1').subscribe();

        const req = httpMock.expectOne(`${service.adminResourceUrl}/agent/buildAgent1/resume`);
        expect(req.request.method).toBe('PUT');
        req.flush({});
    });

    it('should handle pause build agent error', async () => {
        const errorMessage = 'Failed to pause build agent buildAgent1';

        const observable = lastValueFrom(service.pauseBuildAgent('buildAgent1'));

        const req = httpMock.expectOne(`${service.adminResourceUrl}/agent/buildAgent1/pause`);
        expect(req.request.method).toBe('PUT');
        req.flush({ message: errorMessage }, { status: 500, statusText: 'Internal Server Error' });

        try {
            await observable;
            throw new Error('expected an error, but got a success');
        } catch (error) {
            expect(error.message).toContain(errorMessage);
        }
    });

    it('should handle resume build agent error', async () => {
        const errorMessage = 'Failed to resume build agent buildAgent1';

        const observable = lastValueFrom(service.resumeBuildAgent('buildAgent1'));

        // Set up the expected HTTP request and flush the response with an error.
        const req = httpMock.expectOne(`${service.adminResourceUrl}/agent/buildAgent1/resume`);
        expect(req.request.method).toBe('PUT');
        req.flush({ message: errorMessage }, { status: 500, statusText: 'Internal Server Error' });

        try {
            await observable;
            throw new Error('expected an error, but got a success');
        } catch (error) {
            expect(error.message).toContain(errorMessage);
        }
    });

    afterEach(() => {
        httpMock.verify(); // Verify that there are no outstanding requests.
    });
});
