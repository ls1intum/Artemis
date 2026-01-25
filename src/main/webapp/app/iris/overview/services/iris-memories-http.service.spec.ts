import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { IrisMemoriesHttpService } from 'app/iris/overview/services/iris-memories-http.service';
import { MemirisLearningDTO, MemirisMemory, MemirisMemoryConnectionDTO, MemirisMemoryDataDTO, MemirisMemoryWithRelationsDTO } from 'app/iris/shared/entities/memiris.model';

describe('IrisMemoriesHttpService', () => {
    setupTestBed({ zoneless: true });

    let service: IrisMemoriesHttpService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), IrisMemoriesHttpService],
        });
        service = TestBed.inject(IrisMemoriesHttpService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should load aggregated user memory data', async () => {
        const memories: MemirisMemory[] = [
            { id: '1', title: 'First', content: 'Content A', learnings: ['L1'], connections: ['C1'], slept_on: false, deleted: false },
            { id: '2', title: 'Second', content: 'Content B', learnings: [], connections: [], slept_on: true, deleted: false },
        ];
        const learnings: MemirisLearningDTO[] = [{ id: 'L1', title: 'Learning 1', content: 'L content', reference: 'ref', memories: ['1'] }];
        const connections: MemirisMemoryConnectionDTO[] = [{ id: 'C1', connectionType: 'related', memories: ['1', '2'], description: 'desc', weight: 0.5 }];
        const returnedFromService: MemirisMemoryDataDTO = { memories, learnings, connections };

        let result: MemirisMemoryDataDTO | undefined;
        service.getUserMemoryData().subscribe((data) => (result = data));

        const req = httpMock.expectOne('api/iris/user/memoryData');
        expect(req.request.method).toBe('GET');
        req.flush(returnedFromService);
        await vi.waitFor(() => expect(result).toBeDefined());

        expect(result).toEqual(returnedFromService);
    });

    it('should get a specific user memory with relations and URL-encode the id', async () => {
        const rawId = 'a/b?c=d e';
        const expectedUrl = `api/iris/user/memory/${encodeURIComponent(rawId)}`;
        const returnedFromService: MemirisMemoryWithRelationsDTO = {
            id: rawId,
            title: 'Title',
            content: 'Detailed content',
            sleptOn: true,
            deleted: false,
            learnings: [{ id: 'L1', title: 'Learning 1', content: 'L content', reference: 'ref', memories: ['1', '2'] }],
            connections: [{ id: 'C1', connectionType: 'related', memories: ['1', '2'], description: 'desc', weight: 0.5 }],
        };

        let result: MemirisMemoryWithRelationsDTO | undefined;
        service.getUserMemory(rawId).subscribe((memory) => (result = memory));

        const req = httpMock.expectOne(expectedUrl);
        expect(req.request.method).toBe('GET');
        req.flush(returnedFromService);
        await vi.waitFor(() => expect(result).toBeDefined());

        expect(result).toEqual(returnedFromService);
    });

    it('should delete a specific user memory', async () => {
        const memoryId = '123';

        let completed = false;
        service.deleteUserMemory(memoryId).subscribe(() => (completed = true));

        const req = httpMock.expectOne(`api/iris/user/memory/${memoryId}`);
        expect(req.request.method).toBe('DELETE');
        req.flush(null, { status: 200, statusText: 'OK' });
        await vi.waitFor(() => expect(completed).toBe(true));

        expect(completed).toBe(true);
    });
});
