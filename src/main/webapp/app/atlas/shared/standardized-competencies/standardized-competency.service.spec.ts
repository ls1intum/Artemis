import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Observable } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { StandardizedCompetencyService } from './standardized-competency.service';
import { StandardizedCompetencyApi } from 'app/openapi/api/standardized-competency-api';
import { KnowledgeAreaResult } from 'app/openapi/model/knowledge-area-result';
import { StandardizedCompetencyResult } from 'app/openapi/model/standardized-competency-result';
import { Source } from 'app/openapi/model/source';

describe('Standardized competency generated transport', () => {
    let service: StandardizedCompetencyService;
    let api: StandardizedCompetencyApi;
    let http: HttpTestingController;
    const base = '/api/atlas/standardized-competencies';

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(StandardizedCompetencyService);
        api = TestBed.inject(StandardizedCompetencyApi);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('parses the detail JSON body directly through the generated API', () => {
        const expected: StandardizedCompetencyResult = { id: 12, title: 'Analyze', taxonomy: 'ANALYZE', knowledgeAreaId: 4, sourceId: 2 };
        let result: StandardizedCompetencyResult | undefined;
        api.getStandardizedCompetency(12).subscribe((body) => (result = body));
        const req = http.expectOne(`${base}/12`);
        expect(req.request.method).toBe('GET');
        expect(req.request.params.keys()).toEqual([]);
        expect(req.request.body).toBeNull();
        expect(req.request.responseType).toBe('json');
        req.flush(expected);
        expect(result).toEqual(expected);
    });

    it('delegates the tree read and parses nested bodies without HttpResponse wrappers', () => {
        const expected: KnowledgeAreaResult[] = [{ id: 1, children: [{ id: 11, parentId: 1, competencies: [{ id: 12, taxonomy: 'CREATE' }] }] }, { id: 2 }];
        let result: KnowledgeAreaResult[] | undefined;
        service.getAllForTreeView().subscribe((body) => (result = body));
        const req = http.expectOne(`${base}/for-tree-view`);
        expect(req.request.method).toBe('GET');
        expect(req.request.params.keys()).toEqual([]);
        expect(req.request.body).toBeNull();
        expect(req.request.responseType).toBe('json');
        req.flush(expected);
        expect(result).toEqual(expected);
    });

    it('delegates sources with exact citation fields', () => {
        const expected: Source[] = [{ id: 1, title: 'Title', author: 'Author', uri: 'https://example.org' }, { id: 2 }];
        let result: Source[] | undefined;
        service.getSources().subscribe((body) => (result = body));
        const req = http.expectOne(`${base}/sources`);
        expect(req.request.method).toBe('GET');
        expect(req.request.params.keys()).toEqual([]);
        expect(req.request.body).toBeNull();
        expect(req.request.responseType).toBe('json');
        req.flush(expected);
        expect(result).toEqual(expected);
    });

    it('preserves empty top-level lists', () => {
        let tree: KnowledgeAreaResult[] | undefined;
        let sources: Source[] | undefined;
        service.getAllForTreeView().subscribe((body) => (tree = body));
        service.getSources().subscribe((body) => (sources = body));
        http.expectOne(`${base}/for-tree-view`).flush([]);
        http.expectOne(`${base}/sources`).flush([]);
        expect(tree).toEqual([]);
        expect(sources).toEqual([]);
    });

    it.each([
        ['detail', 404],
        ['detail', 403],
        ['tree', 403],
        ['sources', 403],
    ] as const)('propagates %s HTTP %i', (read, status) => {
        let error: HttpErrorResponse | undefined;
        const request: Observable<unknown> = read === 'detail' ? api.getStandardizedCompetency(12) : read === 'tree' ? service.getAllForTreeView() : service.getSources();
        request.subscribe({ next: () => expect.fail('Expected an HTTP error'), error: (response: HttpErrorResponse) => (error = response) });
        http.expectOne(`${base}/${read === 'detail' ? '12' : read === 'tree' ? 'for-tree-view' : 'sources'}`).flush({ message: 'Denied' }, { status, statusText: 'Error' });
        expect(error).toBeInstanceOf(HttpErrorResponse);
        expect(error?.status).toBe(status);
    });
});
