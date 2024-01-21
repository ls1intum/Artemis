import { TestBed } from '@angular/core/testing';

import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { BuildJob } from 'app/entities/build-job.model';
import dayjs from 'dayjs/esm';
import { BuildAgentsService } from 'app/localci/build-agents/build-agents.service';
import { BuildAgent } from 'app/entities/build-agent.model';

describe('BuildAgentsService', () => {
    let service: BuildAgentsService;
    let httpMock: HttpTestingController;
    let element: BuildAgent;

    const mockRunningJobs: BuildJob[] = [
        {
            id: '2',
            name: 'Build Job 2',
            participationId: 102,
            repositoryTypeOrUserName: 'repo2',
            commitHash: 'abc124',
            submissionDate: dayjs('2023-01-01'),
            retryCount: 2,
            buildStartDate: dayjs('2023-01-01'),
            priority: 5,
            courseId: 10,
            isPushToTestRepository: false,
        },
        {
            id: '4',
            name: 'Build Job 4',
            participationId: 104,
            repositoryTypeOrUserName: 'repo4',
            commitHash: 'abc126',
            submissionDate: dayjs('2023-01-04'),
            retryCount: 3,
            buildStartDate: dayjs('2023-01-04'),
            priority: 2,
            courseId: 10,
            isPushToTestRepository: false,
        },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        service = TestBed.inject(BuildAgentsService);
        httpMock = TestBed.inject(HttpTestingController);
        element = new BuildAgent();
        element.id = 1;
        element.name = 'BuildAgent1';
        element.maxNumberOfConcurrentBuildJobs = 3;
        element.numberOfCurrentBuildJobs = 1;
        element.runningBuildJobs = mockRunningJobs;
    });

    it('should return build agents', () => {
        const expectedResponse = [element]; // Expecting an array

        service.getBuildAgents().subscribe((data) => {
            expect(data).toEqual(expectedResponse); // Check if the response matches expected
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/build-agents`);
        expect(req.request.method).toBe('GET');
        req.flush(expectedResponse); // Flush an array of elements
    });

    afterEach(() => {
        httpMock.verify(); // Verify that there are no outstanding requests.
    });
});
