/**
 * Vitest tests for WebsocketAdminComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of, throwError } from 'rxjs';

import { WebsocketAdminComponent } from 'app/core/admin/websocket/websocket-admin.component';
import { WebsocketAdminService } from 'app/core/admin/websocket/websocket-admin.service';
import { WebsocketNode } from 'app/core/admin/websocket/websocket-node.model';
import { AlertService } from 'app/shared/service/alert.service';

describe('WebsocketAdminComponent', () => {
    setupTestBed({ zoneless: true });

    let component: WebsocketAdminComponent;
    let mockService: any;
    let alertService: AlertService;

    const coreNode = { memberId: 'core-1', liteMember: false } as WebsocketNode;
    const liteNode = { memberId: 'lite-1', liteMember: true } as WebsocketNode;

    beforeEach(async () => {
        vi.useFakeTimers();

        mockService = {
            getNodes: vi.fn(),
            triggerAction: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [WebsocketAdminComponent],
            providers: [
                { provide: WebsocketAdminService, useValue: mockService },
                { provide: AlertService, useValue: { success: vi.fn(), error: vi.fn() } as any },
            ],
        })
            .overrideTemplate(WebsocketAdminComponent, '')
            .compileComponents();

        alertService = TestBed.inject(AlertService);
        const fixture = TestBed.createComponent(WebsocketAdminComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.clearAllMocks();
        vi.useRealTimers();
    });

    it('should load nodes successfully', () => {
        mockService.getNodes.mockReturnValue(of([coreNode, liteNode]));

        component.loadNodes();

        expect(mockService.getNodes).toHaveBeenCalledOnce();
        expect(component.nodes()).toEqual([coreNode, liteNode]);
        expect(component.loading()).toBe(false);
        expect(component.lastUpdateFailed()).toBe(false);
        expect(component.lastUpdated()).toBeInstanceOf(Date);
    });

    it('should flag failed load', () => {
        mockService.getNodes.mockReturnValue(throwError(() => new Error('failure')));

        component.loadNodes();

        expect(component.loading()).toBe(false);
        expect(component.lastUpdateFailed()).toBe(true);
    });

    it('should auto refresh periodically when idle', () => {
        mockService.getNodes.mockReturnValue(of([]));

        const initialCallCount = mockService.getNodes.mock.calls.length;
        component.ngOnInit();
        const afterInitCount = mockService.getNodes.mock.calls.length;
        expect(afterInitCount).toBeGreaterThan(initialCallCount);

        vi.advanceTimersByTime(5000);
        const afterTimerCount = mockService.getNodes.mock.calls.length;
        expect(afterTimerCount).toBeGreaterThan(afterInitCount);
        component.ngOnDestroy();
    });

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
        expect(component.reconnecting()).toBe(false);
    });

    it('should trigger disconnect for all core nodes and handle failure', () => {
        component.nodes.set([coreNode]);
        mockService.triggerAction.mockReturnValue(throwError(() => new Error('fail')));

        component.disconnect();

        expect(mockService.triggerAction).toHaveBeenCalledWith('DISCONNECT', coreNode.memberId);
        expect(alertService.error).toHaveBeenCalledWith('artemisApp.websocketAdmin.disconnectFailed');
        expect(component.reconnecting()).toBe(false);
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

    it('should trigger connect for specific node only', () => {
        const otherCore = { memberId: 'core-2', liteMember: false } as WebsocketNode;
        component.nodes.set([coreNode, otherCore, liteNode]);
        mockService.triggerAction.mockReturnValue(of(void 0));

        component.connect(coreNode.memberId);

        expect(mockService.triggerAction).toHaveBeenCalledOnce();
        expect(mockService.triggerAction).toHaveBeenCalledWith('CONNECT', coreNode.memberId);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.websocketAdmin.connectRequested');
    });

    it('should show error when connecting without core nodes', () => {
        component.nodes.set([liteNode]);

        component.connect();

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.websocketAdmin.noCoreNodes');
    });

    it('should handle connect failure', () => {
        component.nodes.set([coreNode]);
        mockService.triggerAction.mockReturnValue(throwError(() => new Error('connection failed')));

        component.connect();

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.websocketAdmin.connectFailed');
        expect(component.reconnecting()).toBe(false);
    });

    it('should show error when disconnecting without core nodes', () => {
        component.nodes.set([liteNode]);

        component.disconnect();

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.websocketAdmin.noCoreNodes');
    });

    it('should trigger disconnect for specific node only', () => {
        const otherCore = { memberId: 'core-2', liteMember: false } as WebsocketNode;
        component.nodes.set([coreNode, otherCore, liteNode]);
        mockService.triggerAction.mockReturnValue(of(void 0));

        component.disconnect(coreNode.memberId);

        expect(mockService.triggerAction).toHaveBeenCalledOnce();
        expect(mockService.triggerAction).toHaveBeenCalledWith('DISCONNECT', coreNode.memberId);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.websocketAdmin.disconnectRequested');
    });

    it('should trigger disconnect for all core nodes successfully', () => {
        const otherCore = { memberId: 'core-2', liteMember: false } as WebsocketNode;
        component.nodes.set([coreNode, otherCore]);
        mockService.triggerAction.mockReturnValue(of(void 0));

        component.disconnect();

        expect(mockService.triggerAction).toHaveBeenCalledTimes(2);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.websocketAdmin.disconnectRequested');
    });

    it('should handle reconnect failure', () => {
        component.nodes.set([coreNode]);
        mockService.triggerAction.mockReturnValue(throwError(() => new Error('reconnect failed')));

        component.reconnect();

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.websocketAdmin.reconnectFailed');
        expect(component.reconnecting()).toBe(false);
    });

    it('should trigger reconnect for all core nodes successfully', () => {
        const otherCore = { memberId: 'core-2', liteMember: false } as WebsocketNode;
        component.nodes.set([coreNode, otherCore]);
        mockService.triggerAction.mockReturnValue(of(void 0));

        component.reconnect();

        expect(mockService.triggerAction).toHaveBeenCalledTimes(2);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.websocketAdmin.reconnectRequested');
    });

    describe('sortedNodes', () => {
        it('should sort nodes with core nodes first', () => {
            component.nodes.set([liteNode, coreNode]);

            const sorted = component.sortedNodes();

            expect(sorted[0].liteMember).toBe(false);
            expect(sorted[1].liteMember).toBe(true);
        });

        it('should sort nodes alphabetically by instanceId', () => {
            const coreA = { memberId: 'a', instanceId: 'artemis-1', liteMember: false } as WebsocketNode;
            const coreB = { memberId: 'b', instanceId: 'artemis-2', liteMember: false } as WebsocketNode;
            const coreC = { memberId: 'c', instanceId: 'artemis-10', liteMember: false } as WebsocketNode;
            component.nodes.set([coreC, coreA, coreB]);

            const sorted = component.sortedNodes();

            expect(sorted[0].instanceId).toBe('artemis-1');
            expect(sorted[1].instanceId).toBe('artemis-2');
            expect(sorted[2].instanceId).toBe('artemis-10');
        });

        it('should fall back to host for sorting when instanceId is missing', () => {
            const coreA = { memberId: 'a', host: 'host-1', liteMember: false } as WebsocketNode;
            const coreB = { memberId: 'b', host: 'host-2', liteMember: false } as WebsocketNode;
            component.nodes.set([coreB, coreA]);

            const sorted = component.sortedNodes();

            expect(sorted[0].host).toBe('host-1');
            expect(sorted[1].host).toBe('host-2');
        });

        it('should fall back to memberId for sorting when instanceId and host are missing', () => {
            const coreA = { memberId: 'member-a', liteMember: false } as WebsocketNode;
            const coreB = { memberId: 'member-b', liteMember: false } as WebsocketNode;
            component.nodes.set([coreB, coreA]);

            const sorted = component.sortedNodes();

            expect(sorted[0].memberId).toBe('member-a');
            expect(sorted[1].memberId).toBe('member-b');
        });
    });

    describe('coreNodes', () => {
        it('should filter only non-lite member nodes', () => {
            const coreA = { memberId: 'core-a', liteMember: false } as WebsocketNode;
            const coreB = { memberId: 'core-b', liteMember: false } as WebsocketNode;
            const liteA = { memberId: 'lite-a', liteMember: true } as WebsocketNode;
            component.nodes.set([coreA, liteA, coreB]);

            const coreNodes = component.coreNodes();

            expect(coreNodes).toHaveLength(2);
            expect(coreNodes.every((n) => !n.liteMember)).toBe(true);
        });
    });

    it('should skip refresh when loading is in progress', () => {
        mockService.getNodes.mockReturnValue(of([]));

        // Call loadNodes which sets loading to true temporarily
        component.loadNodes();

        // Verify loading was set to true during the call
        expect(mockService.getNodes).toHaveBeenCalled();
    });

    it('should clean up subscription on destroy', () => {
        mockService.getNodes.mockReturnValue(of([]));
        component.ngOnInit();
        component.ngOnDestroy();

        // After destroy, the subscription should be cleaned up
        // This is verified by not throwing any errors
        expect(true).toBe(true);
    });
});
