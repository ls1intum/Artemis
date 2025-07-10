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

    // Create shared mock drag behavior that tests can modify
    const dragBehavior = {
        _onStart: null as any,
        _onDrag: null as any,
        _onEnd: null as any,
        on: jest.fn().mockImplementation((event, handler) => {
            switch (event) {
                case 'start':
                    dragBehavior._onStart = handler;
                    break;
                case 'drag':
                    dragBehavior._onDrag = handler;
                    break;
                case 'end':
                    dragBehavior._onEnd = handler;
                    break;
            }
            return dragBehavior;
        }),
    };

    beforeEach(async () => {
        jest.useFakeTimers();

        jest.mock('d3', () => {
            return {
                select: jest.fn().mockImplementation(() => ({
                    attr: jest.fn().mockReturnThis(),
                    style: jest.fn().mockReturnThis(),
                    append: jest.fn().mockImplementation(() => ({
                        attr: jest.fn().mockReturnThis(),
                        style: jest.fn().mockReturnThis(),
                        append: jest.fn().mockReturnThis(),
                        selectAll: jest.fn().mockReturnThis(),
                        data: jest.fn().mockReturnThis(),
                        enter: jest.fn().mockReturnThis(),
                        text: jest.fn().mockReturnThis(),
                        call: jest.fn().mockReturnThis(),
                        remove: jest.fn().mockReturnThis(),
                        on: jest.fn().mockReturnThis(),
                        node: jest.fn().mockReturnValue({
                            getBBox: jest.fn().mockReturnValue({ x: 0, y: 0, width: 100, height: 100 }),
                        }),
                        transition: jest.fn().mockReturnThis(),
                    })),
                    selectAll: jest.fn().mockImplementation(() => ({
                        remove: jest.fn().mockReturnThis(),
                        data: jest.fn().mockImplementation(() => ({
                            enter: jest.fn().mockImplementation(() => ({
                                append: jest.fn().mockImplementation(() => ({
                                    attr: jest.fn().mockReturnThis(),
                                    style: jest.fn().mockReturnThis(),
                                    text: jest.fn().mockReturnThis(),
                                    call: jest.fn().mockReturnThis(),
                                    on: jest.fn().mockReturnThis(),
                                })),
                            })),
                            exit: jest.fn().mockReturnThis(),
                        })),
                        attr: jest.fn().mockReturnThis(),
                        classed: jest.fn().mockReturnThis(),
                        filter: jest.fn().mockReturnValue({
                            classed: jest.fn(),
                        }),
                    })),
                    transition: jest.fn().mockReturnThis(),
                    call: jest.fn().mockReturnThis(),
                })),
                forceSimulation: jest.fn().mockImplementation(() => ({
                    force: jest.fn().mockReturnThis(),
                    alpha: jest.fn().mockReturnThis(),
                    alphaTarget: jest.fn().mockReturnThis(),
                    alphaDecay: jest.fn().mockReturnThis(),
                    velocityDecay: jest.fn().mockReturnThis(),
                    on: jest.fn().mockReturnThis(),
                    nodes: jest.fn().mockReturnThis(),
                    restart: jest.fn(),
                    stop: jest.fn(),
                })),
                forceCollide: jest.fn().mockImplementation(() => ({
                    radius: jest.fn().mockReturnThis(),
                    iterations: jest.fn().mockReturnThis(),
                })),
                forceManyBody: jest.fn().mockImplementation(() => ({
                    strength: jest.fn().mockReturnThis(),
                    distanceMax: jest.fn().mockReturnThis(),
                })),
                forceLink: jest.fn().mockImplementation(() => ({
                    id: jest.fn().mockReturnThis(),
                    distance: jest.fn().mockReturnThis(),
                    strength: jest.fn().mockReturnThis(),
                    links: jest.fn().mockReturnThis(),
                })),
                forceX: jest.fn().mockImplementation(() => ({
                    strength: jest.fn().mockReturnThis(),
                })),
                forceY: jest.fn().mockImplementation(() => ({
                    strength: jest.fn().mockReturnThis(),
                })),
                drag: jest.fn().mockReturnValue(dragBehavior),
                zoom: jest.fn().mockImplementation(() => ({
                    scaleExtent: jest.fn().mockReturnThis(),
                    on: jest.fn().mockReturnThis(),
                    scaleBy: jest.fn(),
                    transform: jest.fn(),
                })),
                zoomIdentity: {
                    translate: jest.fn().mockImplementation(() => ({
                        scale: jest.fn(),
                    })),
                },
            };
        });

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
        component['linkElements'] = { attr: jest.fn().mockReturnThis() } as any;
        component['nodeElements'] = { attr: jest.fn().mockReturnThis() } as any;
        component['textElements'] = { attr: jest.fn().mockReturnThis() } as any;
        component['linkLabelElements'] = {
            attr: jest.fn().mockReturnThis(),
        } as any;

        const mockData = createMockGraphData();
        (component as any).updateGraphData(mockData);
        (component as any).updatePositions();

        expect(component['linkElements']!.attr).toHaveBeenCalled();
        expect(component['nodeElements']!.attr).toHaveBeenCalled();
        expect(component['textElements']!.attr).toHaveBeenCalled();
    });

    it('should handle drag events on nodes', () => {
        (component as any).setupDrag();

        const mockNode = { x: 100, y: 100 } as MemirisSimulationNode;
        const mockEvent = { active: false, x: 200, y: 200 };

        component['simulation'] = {
            alphaTarget: jest.fn().mockReturnThis(),
            restart: jest.fn(),
        } as any;

        // Test drag handlers
        if (dragBehavior._onStart) {
            dragBehavior._onStart(mockEvent, mockNode);
            expect(mockNode.fx).toBe(mockNode.x);
            expect(mockNode.fy).toBe(mockNode.y);
        }

        if (dragBehavior._onDrag) {
            dragBehavior._onDrag(mockEvent, mockNode);
            expect(mockNode.fx).toBe(mockEvent.x);
            expect(mockNode.fy).toBe(mockEvent.y);
        }

        if (dragBehavior._onEnd) {
            dragBehavior._onEnd(mockEvent, mockNode);
            expect(mockNode.fx).toBeUndefined();
            expect(mockNode.fy).toBeUndefined();
        }
    });

    it('should initialize the graph when updateGraph is called but svg is not yet initialized', fakeAsync(() => {
        // Make requestAnimationFrame execute immediately
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
});
