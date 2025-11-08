import { TestBed } from '@angular/core/testing';

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { lastValueFrom } from 'rxjs';
import { BuildAgentInformation } from 'app/buildagent/shared/entities/build-agent-information.model';
import { BuildAgentsService } from 'app/buildagent/build-agents.service';

describe('BuildAgentsService', () => {
    let service: BuildAgentsService;
    let httpMock: HttpTestingController;
    let element: BuildAgentInformation;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useClass: MockTranslateService }],
        });
        service = TestBed.inject(BuildAgentsService);
        httpMock = TestBed.inject(HttpTestingController);
        element = new BuildAgentInformation();
        element.id = 1;
        element.buildAgent = { name: 'buildAgent1', memberAddress: 'localhost:8080', displayName: 'Build Agent 1' };
        element.maxNumberOfConcurrentBuildJobs = 3;
        element.numberOfCurrentBuildJobs = 1;
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

        const req = httpMock.expectOne(`${service.adminResourceUrl}/agents/buildAgent1/pause`);
        expect(req.request.method).toBe('PUT');
        req.flush({});
    });

    it('should resume build agent', () => {
        service.resumeBuildAgent('buildAgent1').subscribe();

        const req = httpMock.expectOne(`${service.adminResourceUrl}/agents/buildAgent1/resume`);
        expect(req.request.method).toBe('PUT');
        req.flush({});
    });

    it('should pause all build agents', () => {
        service.pauseAllBuildAgents().subscribe();

        const req = httpMock.expectOne(`${service.adminResourceUrl}/agents/pause-all`);
        expect(req.request.method).toBe('PUT');
        req.flush({});
    });

    it('should resume all build agents', () => {
        service.resumeAllBuildAgents().subscribe();

        const req = httpMock.expectOne(`${service.adminResourceUrl}/agents/resume-all`);
        expect(req.request.method).toBe('PUT');
        req.flush({});
    });

    it('should handle pause build agent error', async () => {
        const errorMessage = 'Failed to pause build agent buildAgent1';

        const observable = lastValueFrom(service.pauseBuildAgent('buildAgent1'));

        const req = httpMock.expectOne(`${service.adminResourceUrl}/agents/buildAgent1/pause`);
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
        const req = httpMock.expectOne(`${service.adminResourceUrl}/agents/buildAgent1/resume`);
        expect(req.request.method).toBe('PUT');
        req.flush({ message: errorMessage }, { status: 500, statusText: 'Internal Server Error' });

        try {
            await observable;
            throw new Error('expected an error, but got a success');
        } catch (error) {
            expect(error.message).toContain(errorMessage);
        }
    });

    it('should handle pause all build agents error', async () => {
        const errorMessage = 'Failed to pause build agents';

        const observable = lastValueFrom(service.pauseAllBuildAgents());

        const req = httpMock.expectOne(`${service.adminResourceUrl}/agents/pause-all`);
        expect(req.request.method).toBe('PUT');
        req.flush({ message: errorMessage }, { status: 500, statusText: 'Internal Server Error' });

        try {
            await observable;
            throw new Error('expected an error, but got a success');
        } catch (error) {
            expect(error.message).toContain(errorMessage);
        }
    });

    it('should handle resume all build agents error', async () => {
        const errorMessage = 'Failed to resume build agents';

        const observable = lastValueFrom(service.resumeAllBuildAgents());

        // Set up the expected HTTP request and flush the response with an error.
        const req = httpMock.expectOne(`${service.adminResourceUrl}/agents/resume-all`);
        expect(req.request.method).toBe('PUT');
        req.flush({ message: errorMessage }, { status: 500, statusText: 'Internal Server Error' });

        try {
            await observable;
            throw new Error('expected an error, but got a success');
        } catch (error) {
            expect(error.message).toContain(errorMessage);
        }
    });

    it('should clear distributed data', () => {
        service.clearDistributedData().subscribe();

        const req = httpMock.expectOne(`${service.adminResourceUrl}/clear-distributed-data`);
        expect(req.request.method).toBe('DELETE');
        req.flush({});
    });

    it('should handle clear distributed data error', async () => {
        const errorMessage = 'Failed to clear distributed data';

        const observable = lastValueFrom(service.clearDistributedData());

        // Set up the expected HTTP request and flush the response with an error.
        const req = httpMock.expectOne(`${service.adminResourceUrl}/clear-distributed-data`);
        expect(req.request.method).toBe('DELETE');
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
