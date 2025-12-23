import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { WebsocketAdminComponent } from 'app/core/admin/websocket/websocket-admin.component';
import { WebsocketAdminService } from 'app/core/admin/websocket/websocket-admin.service';
import { WebsocketNode } from 'app/core/admin/websocket/websocket-node.model';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService, TranslateStore } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { of, throwError } from 'rxjs';

class MockWebsocketAdminService {
    getNodes = jest.fn();
    triggerAction = jest.fn();
}

describe('WebsocketAdminComponent', () => {
    let component: WebsocketAdminComponent;
    let mockService: MockWebsocketAdminService;
    let alertService: AlertService;

    const coreNode = { memberId: 'core-1', liteMember: false } as WebsocketNode;
    const liteNode = { memberId: 'lite-1', liteMember: true } as WebsocketNode;

    beforeEach(async () => {
        mockService = new MockWebsocketAdminService();

        await TestBed.configureTestingModule({
            imports: [WebsocketAdminComponent],
            providers: [
                { provide: WebsocketAdminService, useValue: mockService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: TranslateStore, useValue: {} },
                { provide: AlertService, useValue: { success: jest.fn(), error: jest.fn() } as any },
            ],
        }).compileComponents();

        alertService = TestBed.inject(AlertService);
        const fixture = TestBed.createComponent(WebsocketAdminComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should load nodes successfully', () => {
        mockService.getNodes.mockReturnValue(of([coreNode, liteNode]));

        component.loadNodes();

        expect(mockService.getNodes).toHaveBeenCalledOnce();
        expect(component.nodes()).toEqual([coreNode, liteNode]);
        expect(component.loading()).toBeFalse();
        expect(component.lastUpdateFailed()).toBeFalse();
        expect(component.lastUpdated()).toBeInstanceOf(Date);
    });

    it('should flag failed load', () => {
        mockService.getNodes.mockReturnValue(throwError(() => new Error('failure')));

        component.loadNodes();

        expect(component.loading()).toBeFalse();
        expect(component.lastUpdateFailed()).toBeTrue();
    });

    it('should auto refresh every 5 seconds when idle', fakeAsync(() => {
        mockService.getNodes.mockReturnValue(of([]));

        component.ngOnInit();
        expect(mockService.getNodes).toHaveBeenCalledOnce();

        tick(5000);
        expect(mockService.getNodes).toHaveBeenCalledTimes(2);
        component.ngOnDestroy();
    }));

    it('should show error when reconnecting without core nodes', () => {
        mockService.getNodes.mockReturnValue(of([]));
        component.nodes.set([liteNode]);

        component.reconnect();

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.websocketAdmin.noCoreNodes');
    });

    it('should trigger reconnect for specific core node', () => {
        component.nodes.set([coreNode]);
        mockService.triggerAction.mockReturnValue(of(void 0));

        component.reconnect(coreNode.memberId);

        expect(mockService.triggerAction).toHaveBeenCalledWith('RECONNECT', coreNode.memberId);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.websocketAdmin.reconnectRequested');
        expect(component.reconnecting()).toBeFalse();
    });

    it('should trigger disconnect for all core nodes and handle failure', () => {
        component.nodes.set([coreNode]);
        mockService.triggerAction.mockReturnValue(throwError(() => new Error('fail')));

        component.disconnect();

        expect(mockService.triggerAction).toHaveBeenCalledWith('DISCONNECT', coreNode.memberId);
        expect(alertService.error).toHaveBeenCalledWith('artemisApp.websocketAdmin.disconnectFailed');
        expect(component.reconnecting()).toBeFalse();
    });

    it('should trigger connect for all core nodes', () => {
        const otherCore = { memberId: 'core-2', liteMember: false } as WebsocketNode;
        component.nodes.set([coreNode, otherCore, liteNode]);
        mockService.triggerAction.mockReturnValue(of(void 0));

        component.connect();

        expect(mockService.triggerAction).toHaveBeenCalledTimes(2);
        expect(mockService.triggerAction).toHaveBeenCalledWith('CONNECT', coreNode.memberId);
        expect(mockService.triggerAction).toHaveBeenCalledWith('CONNECT', otherCore.memberId);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.websocketAdmin.connectRequested');
    });
});
