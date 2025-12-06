import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { WebsocketAdminService } from 'app/core/admin/websocket/websocket-admin.service';

describe('WebsocketAdminService', () => {
    let service: WebsocketAdminService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting(), WebsocketAdminService],
        });

        service = TestBed.inject(WebsocketAdminService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should fetch websocket nodes', () => {
        const mockNodes = [{ memberId: 'node-1' }];

        service.getNodes().subscribe((nodes) => {
            expect(nodes).toEqual(mockNodes);
        });

        const req = httpMock.expectOne('api/core/admin/websocket/nodes');
        expect(req.request.method).toBe('GET');
        req.flush(mockNodes);
    });

    it('should trigger action with target node', () => {
        service.triggerAction('RECONNECT', 'abc').subscribe();

        const req = httpMock.expectOne((request) => {
            return request.url === 'api/core/admin/websocket/reconnect' && request.params.get('action') === 'RECONNECT' && request.params.get('targetNodeId') === 'abc';
        });

        expect(req.request.method).toBe('POST');
        expect(req.request.body).toBeNull();
        req.flush(null);
    });

    it('should trigger action without target node', () => {
        service.triggerAction('DISCONNECT').subscribe();

        const req = httpMock.expectOne((request) => {
            return request.url === 'api/core/admin/websocket/reconnect' && request.params.get('action') === 'DISCONNECT' && !request.params.has('targetNodeId');
        });

        expect(req.request.method).toBe('POST');
        req.flush(null);
    });
});
