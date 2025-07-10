import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { MemirisGraphViewComponent } from './memiris-graph-view.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { of } from 'rxjs';
import {
    MemirisConnectionType,
    MemirisGraphData,
    MemirisGraphSettings,
    MemirisLearning,
    MemirisLearningNode,
    MemirisMemory,
    MemirisMemoryConnection,
    MemirisMemoryNode,
    MemirisSimulationLink,
    MemirisSimulationLinkMemoryLearning,
    MemirisSimulationLinkMemoryMemory,
    MemirisSimulationNode,
} from '../entities/memiris.model';
import { ElementRef } from '@angular/core';

// Test data generators
function createMockMemory(id: string, title: string, deleted = false): MemirisMemory {
    return new MemirisMemory(id, title, 'Content for ' + title, [], [], false, deleted);
}

function createMockLearning(id: string, title: string): MemirisLearning {
    return new MemirisLearning(id, title, 'Content for ' + title, 'reference', []);
}

function createMockConnection(id: string, type: MemirisConnectionType, memories: MemirisMemory[]): MemirisMemoryConnection {
    return new MemirisMemoryConnection(id, type, memories, 'Description', 1);
}

function createMockGraphData(): MemirisGraphData {
    const memory1 = createMockMemory('memory1', 'Memory 1');
    const memory2 = createMockMemory('memory2', 'Memory 2');
    const memory3 = createMockMemory('memory3', 'Memory 3', true); // deleted memory
    const learning1 = createMockLearning('learning1', 'Learning 1');

    memory1.learnings = ['learning1'];
    memory2.learnings = ['learning1'];

    memory1.connections = ['conn1'];
    memory2.connections = ['conn1'];
    memory3.connections = ['conn2'];

    const connection1 = createMockConnection('conn1', MemirisConnectionType.RELATED, [memory1, memory2]);
    const connection2 = createMockConnection('conn2', MemirisConnectionType.CREATED_FROM, [memory1, memory3]);

    return new MemirisGraphData([memory1, memory2, memory3], [learning1], [connection1, connection2]);
}

// Mock TranslateService
class MockTranslateService {
    onLangChange = of({ lang: 'en' });
}

// Mock ArtemisTranslatePipe
class MockTranslatePipeClass {
    transform(key: string, params: any): string {
        return key;
    }
}

describe('MemirisGraphViewComponent', () => {
    let component: MemirisGraphViewComponent;
    let fixture: ComponentFixture<MemirisGraphViewComponent>;
    let translateService: TranslateService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MemirisGraphViewComponent, FontAwesomeModule],
            declarations: [MockComponent(LoadingIndicatorContainerComponent), MockComponent(ButtonComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ArtemisTranslatePipe, useClass: MockTranslatePipeClass },
            ],
        }).compileComponents();

        translateService = TestBed.inject(TranslateService);

        fixture = TestBed.createComponent(MemirisGraphViewComponent);
        component = fixture.componentInstance;

        // Mock SVG container element
        const mockSvgContainer = document.createElement('svg');
        mockSvgContainer.setAttribute('width', '800');
        mockSvgContainer.setAttribute('height', '600');
        Object.defineProperty(mockSvgContainer, 'clientWidth', { value: 800 });
        Object.defineProperty(mockSvgContainer, 'clientHeight', { value: 600 });

        // Set up the viewChild element
        component['svgContainer'] = (() => ({ nativeElement: mockSvgContainer }) as ElementRef) as any;

        // Properly mock the simulation with stop method
        component['simulation'] = {
            force: jest.fn().mockReturnThis(),
            alpha: jest.fn().mockReturnThis(),
            alphaTarget: jest.fn().mockReturnThis(),
            alphaDecay: jest.fn().mockReturnThis(),
            velocityDecay: jest.fn().mockReturnThis(),
            on: jest.fn().mockReturnThis(),
            nodes: jest.fn().mockReturnThis(),
            restart: jest.fn(),
            stop: jest.fn(),
        } as any;

        fixture.detectChanges();
    });

    afterEach(() => {
        // Ensure simulation is properly cleaned up
        if (component['simulation'] && !component['simulation'].stop) {
            component['simulation'].stop = jest.fn();
        }
        jest.clearAllMocks();
        jest.resetModules();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with default settings', () => {
        expect(component.settings()).toEqual(new MemirisGraphSettings());
    });

    it('should have loading state based on graphData', () => {
        expect(component.loading()).toBeTrue();

        const mockData = createMockGraphData();
        fixture.componentRef.setInput('graphData', mockData);
        fixture.detectChanges();

        expect(component.loading()).toBeFalse();
    });

    it('should subscribe to language changes on init', () => {
        const spy = jest.spyOn(translateService.onLangChange, 'subscribe');
        component.ngOnInit();
        expect(spy).toHaveBeenCalled();
    });

    it('should unsubscribe on destroy', () => {
        const subscription = { unsubscribe: jest.fn() };
        component['subscriptions'] = [subscription as any];

        if (component['simulation'] && !component['simulation'].stop) {
            component['simulation'].stop = jest.fn();
        }

        component.ngOnDestroy();

        expect(subscription.unsubscribe).toHaveBeenCalled();
        if (component['simulation']) {
            expect(component['simulation'].stop).toHaveBeenCalled();
        }
    });

    it('should update graph data when graphData input changes', () => {
        const updateGraphDataSpy = jest.spyOn<any, any>(component, 'updateGraphData');
        const mockData = createMockGraphData();

        component.ngOnChanges({
            graphData: {
                currentValue: mockData,
                previousValue: undefined,
                firstChange: true,
                isFirstChange: () => true,
            },
        } as any);

        expect(updateGraphDataSpy).toHaveBeenCalledWith(mockData);
    });

    it('should create nodes and links from graph data', () => {
        const mockData = createMockGraphData();

        // Call the private method directly
        (component as any).updateGraphData(mockData);

        expect(component['allNodes']).toHaveLength(mockData.memories.length + mockData.learnings.length);
        expect(component['allLinks'].length).toBeGreaterThan(0);

        const memoryNodes = component['allNodes'].filter((node) => node instanceof MemirisMemoryNode);
        expect(memoryNodes).toHaveLength(mockData.memories.length);

        const learningNodes = component['allNodes'].filter((node) => node instanceof MemirisLearningNode);
        expect(learningNodes).toHaveLength(mockData.learnings.length);
    });

    it('should apply settings to filter nodes and links', () => {
        const mockData = createMockGraphData();
        (component as any).updateGraphData(mockData);

        // Default settings
        (component as any).applySettings();
        expect(component['nodes']).toHaveLength(mockData.memories.length + mockData.learnings.length - 1);

        // Test hiding memories
        const hideMemoriesSettings = new MemirisGraphSettings();
        hideMemoriesSettings.showMemories = false;
        fixture.componentRef.setInput('settings', hideMemoriesSettings);
        (component as any).applySettings();

        expect(component['nodes'].filter((node) => node instanceof MemirisMemoryNode)).toHaveLength(0);
        expect(component['nodes'].filter((node) => node instanceof MemirisLearningNode)).toHaveLength(mockData.learnings.length);

        // Test hiding learnings
        const hideLearningsSettings = new MemirisGraphSettings();
        hideLearningsSettings.showLearnings = false;
        fixture.componentRef.setInput('settings', hideLearningsSettings);
        (component as any).applySettings();

        expect(component['nodes'].filter((node) => node instanceof MemirisLearningNode)).toHaveLength(0);
        expect(component['nodes'].filter((node) => node instanceof MemirisMemoryNode)).toHaveLength(mockData.memories.length - 1);

        // Test showing deleted memories
        const hideDeletedSettings = new MemirisGraphSettings();
        hideDeletedSettings.hideDeleted = false;
        fixture.componentRef.setInput('settings', hideDeletedSettings);
        (component as any).applySettings();

        expect(component['nodes']).toHaveLength(mockData.memories.length + mockData.learnings.length);
    });

    it('should handle node click and emit selected node', () => {
        const mockData = createMockGraphData();
        (component as any).updateGraphData(mockData);

        const nodeSelectedSpy = jest.spyOn(component.nodeSelected, 'emit');
        const node = component['nodes'][0];

        (component as any).handleNodeClick(node);

        expect(component['selectedNode']).toBe(node);
        expect(nodeSelectedSpy).toHaveBeenCalledWith(node);
    });

    it('should highlight the selected node', () => {
        const mockData = createMockGraphData();
        (component as any).updateGraphData(mockData);

        const classed1 = jest.fn();
        const classed2 = jest.fn();
        component['nodeElements'] = {
            classed: classed1,
            filter: jest.fn().mockReturnValue({
                classed: classed2,
            }),
        } as any;

        const node = component['nodes'][0];
        (component as any).highlightNode(node);

        expect(classed1).toHaveBeenCalledOnce();
        expect(classed2).toHaveBeenCalledOnce();

        (component as any).highlightNode(undefined);
        expect(classed1).toHaveBeenCalledTimes(2);
        expect(classed2).toHaveBeenCalledOnce();
    });

    it('should zoom in when zoomIn is called', () => {
        const transitionMock = {
            call: jest.fn(),
        };
        component['svg'] = {
            transition: jest.fn().mockReturnValue(transitionMock),
        } as any;

        component['zoom'] = {
            scaleBy: jest.fn(),
        } as any;

        component.zoomIn();

        expect(component['svg']!.transition).toHaveBeenCalled();
        expect(transitionMock.call).toHaveBeenCalled();
    });

    it('should zoom out when zoomOut is called', () => {
        const transitionMock = {
            call: jest.fn(),
        };
        component['svg'] = {
            transition: jest.fn().mockReturnValue(transitionMock),
        } as any;

        component['zoom'] = {
            scaleBy: jest.fn(),
        } as any;

        component.zoomOut();

        expect(component['svg']!.transition).toHaveBeenCalled();
        expect(transitionMock.call).toHaveBeenCalled();
    });

    it('should reset view when resetView is called', () => {
        const transitionMock = {
            call: jest.fn(),
        };
        component['svg'] = {
            transition: jest.fn().mockReturnValue(transitionMock),
        } as any;

        component['zoom'] = {
            transform: jest.fn(),
        } as any;

        component['graphGroup'] = {
            node: jest.fn().mockReturnValue({
                getBBox: jest.fn().mockReturnValue({ x: 0, y: 0, width: 100, height: 100 }),
            }),
        } as any;

        component.resetView();

        expect(component['svg']!.transition).toHaveBeenCalled();
        expect(transitionMock.call).toHaveBeenCalled();
    });

    it('should update positions of elements when simulation ticks', () => {
        const mockNode1 = { getId: () => 'node1', x: 100, y: 150 } as MemirisSimulationNode;
        const mockNode2 = { getId: () => 'node2', x: 200, y: 250 } as MemirisSimulationNode;
        const mockLink = {
            getId: () => 'link1',
            source: mockNode1,
            target: mockNode2,
        } as MemirisSimulationLink;

        component['nodes'] = [mockNode1, mockNode2];
        component['links'] = [mockLink];

        // Set up mock elements with spies
        const linkAttrSpy = jest.fn().mockReturnThis();
        const nodeAttrSpy = jest.fn().mockReturnThis();
        const textAttrSpy = jest.fn().mockReturnThis();
        const labelAttrSpy = jest.fn().mockReturnThis();

        component['linkElements'] = { attr: linkAttrSpy } as any;
        component['nodeElements'] = { attr: nodeAttrSpy } as any;
        component['textElements'] = { attr: textAttrSpy } as any;
        component['linkLabelElements'] = { attr: labelAttrSpy } as any;

        (component as any).updatePositions();

        // Test link position attributes
        expect(linkAttrSpy).toHaveBeenCalledWith('x1', expect.any(Function));
        expect(linkAttrSpy).toHaveBeenCalledWith('y1', expect.any(Function));
        expect(linkAttrSpy).toHaveBeenCalledWith('x2', expect.any(Function));
        expect(linkAttrSpy).toHaveBeenCalledWith('y2', expect.any(Function));

        const x1Fn = linkAttrSpy.mock.calls.find((call) => call[0] === 'x1')[1];
        const y1Fn = linkAttrSpy.mock.calls.find((call) => call[0] === 'y1')[1];
        const x2Fn = linkAttrSpy.mock.calls.find((call) => call[0] === 'x2')[1];
        const y2Fn = linkAttrSpy.mock.calls.find((call) => call[0] === 'y2')[1];

        expect(x1Fn(mockLink)).toBe(100);
        expect(y1Fn(mockLink)).toBe(150);
        expect(x2Fn(mockLink)).toBe(200);
        expect(y2Fn(mockLink)).toBe(250);

        // Test node position attributes
        expect(nodeAttrSpy).toHaveBeenCalledWith('cx', expect.any(Function));
        expect(nodeAttrSpy).toHaveBeenCalledWith('cy', expect.any(Function));

        const cxFn = nodeAttrSpy.mock.calls.find((call) => call[0] === 'cx')[1];
        const cyFn = nodeAttrSpy.mock.calls.find((call) => call[0] === 'cy')[1];
        expect(cxFn(mockNode1)).toBe(100);
        expect(cyFn(mockNode1)).toBe(150);

        // Test text element attributes
        expect(textAttrSpy).toHaveBeenCalledWith('x', expect.any(Function));
        expect(textAttrSpy).toHaveBeenCalledWith('y', expect.any(Function));

        const xFn = textAttrSpy.mock.calls.find((call) => call[0] === 'x')[1];
        const yFn = textAttrSpy.mock.calls.find((call) => call[0] === 'y')[1];
        expect(xFn(mockNode1)).toBe(100);
        expect(yFn(mockNode1)).toBe(150);

        // Test link label attributes
        expect(labelAttrSpy).toHaveBeenCalledWith('x', expect.any(Function));
        expect(labelAttrSpy).toHaveBeenCalledWith('y', expect.any(Function));
        expect(labelAttrSpy).toHaveBeenCalledWith('transform', expect.any(Function));

        const labelXFn = labelAttrSpy.mock.calls.find((call) => call[0] === 'x')[1];
        const labelYFn = labelAttrSpy.mock.calls.find((call) => call[0] === 'y')[1];
        const transformFn = labelAttrSpy.mock.calls.find((call) => call[0] === 'transform')[1];

        expect(labelXFn(mockLink)).toBe(150);
        expect(labelYFn(mockLink)).toBe(200);

        const transformValue = transformFn(mockLink);
        expect(transformValue).toContain('rotate');
        expect(transformValue).toContain('150, 200');
    });

    it('should handle undefined node coordinates gracefully in updatePositions', () => {
        const mockNode1 = { getId: () => 'node1', x: undefined, y: undefined } as unknown as MemirisSimulationNode;
        const mockNode2 = { getId: () => 'node2', x: undefined, y: undefined } as unknown as MemirisSimulationNode;
        const mockLink = {
            getId: () => 'link1',
            source: mockNode1,
            target: mockNode2,
        } as MemirisSimulationLink;

        component['nodes'] = [mockNode1, mockNode2];
        component['links'] = [mockLink];

        const attrMock = jest.fn().mockReturnThis();
        component['linkElements'] = { attr: attrMock } as any;
        component['nodeElements'] = { attr: attrMock } as any;
        component['textElements'] = { attr: attrMock } as any;
        component['linkLabelElements'] = { attr: attrMock } as any;

        expect(() => (component as any).updatePositions()).not.toThrow();

        const x1Fn = attrMock.mock.calls.find((call) => call[0] === 'x1')?.[1];
        expect(x1Fn?.(mockLink)).toBe(0);
    });

    it('should handle missing linkLabelElements gracefully', () => {
        component['linkElements'] = { attr: jest.fn().mockReturnThis() } as any;
        component['nodeElements'] = { attr: jest.fn().mockReturnThis() } as any;
        component['textElements'] = { attr: jest.fn().mockReturnThis() } as any;
        component['linkLabelElements'] = undefined;

        expect(() => (component as any).updatePositions()).not.toThrow();
    });

    it('should initialize the graph when updateGraph is called but svg is not yet initialized', fakeAsync(() => {
        const originalRequestAnimationFrame = window.requestAnimationFrame;
        window.requestAnimationFrame = (cb: any) => {
            cb();
            return 0;
        };

        const initializeGraphSpy = jest.spyOn<any, any>(component, 'initializeGraph').mockImplementation(() => {});
        component['svg'] = undefined;
        (component as any).updateGraph();

        expect(initializeGraphSpy).toHaveBeenCalled();

        window.requestAnimationFrame = originalRequestAnimationFrame;
    }));

    it('should properly update the graph with nodes and links', () => {
        const mockData = createMockGraphData();
        (component as any).updateGraphData(mockData);

        // Mock SVG groups and selections
        const mockNodesGroup = {
            selectAll: jest.fn().mockReturnValue({
                remove: jest.fn(),
                data: jest.fn().mockReturnValue({
                    enter: jest.fn().mockReturnValue({
                        append: jest.fn().mockReturnValue({
                            attr: jest.fn().mockReturnThis(),
                            text: jest.fn().mockReturnThis(),
                            call: jest.fn().mockReturnThis(),
                            on: jest.fn().mockReturnThis(),
                        }),
                    }),
                }),
            }),
        };

        const mockLinksGroup = {
            selectAll: jest.fn().mockReturnValue({
                remove: jest.fn(),
                data: jest.fn().mockReturnValue({
                    enter: jest.fn().mockReturnValue({
                        append: jest.fn().mockReturnValue({
                            attr: jest.fn().mockReturnThis(),
                            text: jest.fn().mockReturnThis(),
                        }),
                    }),
                }),
            }),
        };

        component['svg'] = {
            select: jest.fn().mockImplementation((selector) => {
                if (selector === '.nodes') return mockNodesGroup;
                if (selector === '.links') return mockLinksGroup;
                return null;
            }),
        } as any;

        component['simulation'] = {
            nodes: jest.fn().mockReturnThis(),
            force: jest.fn().mockReturnValue({
                links: jest.fn(),
            }),
            alpha: jest.fn().mockReturnThis(),
            restart: jest.fn(),
        } as any;

        component['nodeElements'] = undefined;
        component['linkElements'] = undefined;
        component['textElements'] = undefined;
        component['linkLabelElements'] = undefined;

        jest.spyOn<any, any>(component, 'setupDrag').mockReturnValue(jest.fn());

        (component as any).updateGraph();

        // Verify core graph update operations
        expect(component['svg']!.select).toHaveBeenCalledWith('.nodes');
        expect(component['svg']!.select).toHaveBeenCalledWith('.links');
        expect(mockNodesGroup.selectAll().remove).toHaveBeenCalled();
        expect(mockLinksGroup.selectAll().remove).toHaveBeenCalled();
        expect(component['simulation']!.nodes).toHaveBeenCalledWith(component['nodes']);
        expect(component['simulation']!.alpha).toHaveBeenCalledWith(0.3);
        expect(component['simulation']!.restart).toHaveBeenCalled();
    });

    it('should fully render the graph with node and link elements', () => {
        const mockData = createMockGraphData();

        // Create SVG DOM structure
        const svgElement = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        const nodesGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        nodesGroup.classList.add('nodes');
        const linksGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        linksGroup.classList.add('links');

        svgElement.appendChild(nodesGroup);
        svgElement.appendChild(linksGroup);

        component['svgContainer'] = (() => {
            return { nativeElement: svgElement };
        }) as any;

        // Set up simulation mock
        const nodesSpy = jest.fn().mockReturnThis();
        const alphaSpy = jest.fn().mockReturnThis();
        const restartSpy = jest.fn();

        component['simulation'] = {
            nodes: nodesSpy,
            force: jest.fn().mockReturnValue({
                links: jest.fn().mockReturnThis(),
            }),
            alpha: alphaSpy,
            restart: restartSpy,
            on: jest.fn().mockImplementation((event, callback) => {
                if (event === 'tick') {
                    callback();
                }
                return component['simulation'];
            }),
        } as any;

        // Mock helper functions
        jest.spyOn<any, any>(component, 'getNodeRadius').mockReturnValue(10);
        jest.spyOn<any, any>(component, 'setupDrag').mockReturnValue(jest.fn());
        jest.spyOn<any, any>(component, 'getNodeClasses').mockReturnValue('node-class');
        jest.spyOn<any, any>(component, 'getLinkClasses').mockReturnValue('link-class');

        (component as any).updateGraphData(mockData);
        (component as any).initializeGraph();
        (component as any).updateGraph();

        expect(nodesSpy).toHaveBeenCalled();
        expect(alphaSpy).toHaveBeenCalled();
        expect(restartSpy).toHaveBeenCalled();
    });

    it('should handle drag events on nodes by creating proper D3 event handlers', () => {
        const mockNode = { x: 100, y: 100, fx: undefined, fy: undefined } as MemirisSimulationNode;

        component['simulation'] = {
            alphaTarget: jest.fn().mockReturnThis(),
            restart: jest.fn(),
        } as any;

        const dragBehavior = (component as any).setupDrag();

        // Test start event
        const mockStartEvent = { active: 0, subject: mockNode, x: mockNode.x, y: mockNode.y };
        dragBehavior.on('start')(mockStartEvent, mockNode);
        expect(mockNode.fx).toBe(mockNode.x);
        expect(mockNode.fy).toBe(mockNode.y);
        expect(component['simulation']?.alphaTarget).toHaveBeenCalledWith(0.3);
        expect(component['simulation']?.restart).toHaveBeenCalled();

        // Test drag event
        const mockDragEvent = { active: 0, subject: mockNode, x: 200, y: 300 };
        dragBehavior.on('drag')(mockDragEvent, mockNode);
        expect(mockNode.fx).toBe(200);
        expect(mockNode.fy).toBe(300);

        // Test end event
        jest.clearAllMocks();
        const mockEndEvent = { active: 0, subject: mockNode };
        dragBehavior.on('end')(mockEndEvent, mockNode);
        expect(mockNode.fx).toBeUndefined();
        expect(mockNode.fy).toBeUndefined();
        expect(component['simulation']?.alphaTarget).toHaveBeenCalledWith(0);
    });

    it('should return correct node radius based on node type', () => {
        const memoryNode = new MemirisMemoryNode(createMockMemory('memory1', 'Memory 1'));
        const learningNode = new MemirisLearningNode(createMockLearning('learning1', 'Learning 1'));
        const unknownNode = { getId: () => 'unknown' } as MemirisSimulationNode;

        expect((component as any).getNodeRadius(memoryNode)).toBe(12);
        expect((component as any).getNodeRadius(learningNode)).toBe(8);
        expect((component as any).getNodeRadius(unknownNode)).toBe(4);
    });

    it('should return correct node classes based on node type and state', () => {
        // Create test nodes
        const regularMemoryNode = new MemirisMemoryNode(createMockMemory('memory1', 'Memory 1'));
        const deletedMemoryNode = new MemirisMemoryNode(createMockMemory('memory2', 'Memory 2', true));
        const learningNode = new MemirisLearningNode(createMockLearning('learning1', 'Learning 1'));

        // Test with no selected node
        component['selectedNode'] = undefined;
        expect((component as any).getNodeClasses(regularMemoryNode)).toBe('node memory');
        expect((component as any).getNodeClasses(deletedMemoryNode)).toBe('node memory deleted');
        expect((component as any).getNodeClasses(learningNode)).toBe('node learning');

        // Test with selected node
        component['selectedNode'] = regularMemoryNode;
        expect((component as any).getNodeClasses(regularMemoryNode)).toBe('node memory selected');
        expect((component as any).getNodeClasses(deletedMemoryNode)).toBe('node memory deleted');
    });

    it('should return correct link classes based on link type', () => {
        // Create test nodes and links
        const memoryNode1 = new MemirisMemoryNode(createMockMemory('memory1', 'Memory 1'));
        const memoryNode2 = new MemirisMemoryNode(createMockMemory('memory2', 'Memory 2'));
        const learningNode = new MemirisLearningNode(createMockLearning('learning1', 'Learning 1'));

        const connection = createMockConnection('conn1', MemirisConnectionType.RELATED, [memoryNode1.memory, memoryNode2.memory]);
        const memoryMemoryLink = new MemirisSimulationLinkMemoryMemory(connection, memoryNode1, memoryNode2);
        const memoryLearningLink = new MemirisSimulationLinkMemoryLearning(memoryNode1, learningNode);
        const unknownLink = { getId: () => 'unknown' } as MemirisSimulationLink;

        expect((component as any).getLinkClasses(memoryMemoryLink)).toBe(' memory-memory');
        expect((component as any).getLinkClasses(memoryLearningLink)).toBe(' memory-learning');
        expect((component as any).getLinkClasses(unknownLink)).toBe('');
    });

    it('should return correct link distances based on link type', () => {
        // Create test nodes and links
        const memoryNode1 = new MemirisMemoryNode(createMockMemory('memory1', 'Memory 1'));
        const memoryNode2 = new MemirisMemoryNode(createMockMemory('memory2', 'Memory 2'));
        const learningNode = new MemirisLearningNode(createMockLearning('learning1', 'Learning 1'));

        const connection = createMockConnection('conn1', MemirisConnectionType.RELATED, [memoryNode1.memory, memoryNode2.memory]);
        const memoryMemoryLink = new MemirisSimulationLinkMemoryMemory(connection, memoryNode1, memoryNode2);
        const memoryLearningLink = new MemirisSimulationLinkMemoryLearning(memoryNode1, learningNode);
        const unknownLink = { getId: () => 'unknown' } as MemirisSimulationLink;

        expect((component as any).getLinkDistance(memoryMemoryLink)).toBe(400);
        expect((component as any).getLinkDistance(memoryLearningLink)).toBe(120);
        expect((component as any).getLinkDistance(unknownLink)).toBe(100);
    });

    it('should return correct node charge strength based on node type', () => {
        const memoryNode = new MemirisMemoryNode(createMockMemory('memory1', 'Memory 1'));
        const learningNode = new MemirisLearningNode(createMockLearning('learning1', 'Learning 1'));
        const unknownNode = { getId: () => 'unknown' } as MemirisSimulationNode;

        expect((component as any).getNodeChargeStrength(memoryNode)).toBe(-350);
        expect((component as any).getNodeChargeStrength(learningNode)).toBe(-250);
        expect((component as any).getNodeChargeStrength(unknownNode)).toBe(-1000);
    });
});
