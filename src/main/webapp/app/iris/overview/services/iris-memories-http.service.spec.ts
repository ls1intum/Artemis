import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { IrisMemoriesHttpService } from 'app/iris/overview/services/iris-memories-http.service';
import { MemirisMemory, MemirisMemoryWithRelationsDTO } from 'app/iris/shared/entities/memiris.model';

describe('IrisMemoriesHttpService', () => {
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
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should list user memories', fakeAsync(() => {
        const returnedFromService: MemirisMemory[] = [
            { id: '1', title: 'First', content: 'Content A', learnings: ['L1'], connections: ['C1'], slept_on: false, deleted: false },
            { id: '2', title: 'Second', content: 'Content B', learnings: [], connections: [], slept_on: true, deleted: false },
        ];

        let result: MemirisMemory[] | undefined;
        service.listUserMemories().subscribe((memories) => (result = memories));

        const req = httpMock.expectOne('api/iris/memories/user');
        expect(req.request.method).toBe('GET');
        req.flush(returnedFromService);
        tick();

        expect(result).toEqual(returnedFromService);
    }));

    it('should get a specific user memory with relations and URL-encode the id', fakeAsync(() => {
        const rawId = 'a/b?c=d e';
        const expectedUrl = `api/iris/memories/user/${encodeURIComponent(rawId)}`;
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
        tick();

        expect(result).toEqual(returnedFromService);
    }));

    it('should delete a specific user memory', fakeAsync(() => {
        const memoryId = '123';

        let completed = false;
        service.deleteUserMemory(memoryId).subscribe(() => (completed = true));

        const req = httpMock.expectOne(`api/iris/memories/user/${memoryId}`);
        expect(req.request.method).toBe('DELETE');
        req.flush(null, { status: 200, statusText: 'OK' });
        tick();

        expect(completed).toBeTrue();
    }));
});
