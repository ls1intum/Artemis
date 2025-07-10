// Mock D3 before importing anything else
jest.mock('d3', () => {
    // Create a single shared mockD3 object that will be used throughout the tests
    const mockD3 = {
        // Mock behaviors for drag
        dragBehavior: {
            on: jest.fn().mockImplementation((event, handler) => {
                // Store handlers so tests can call them manually
                if (event === 'start') mockD3.dragBehavior._onStart = handler;
                if (event === 'drag') mockD3.dragBehavior._onDrag = handler;
                if (event === 'end') mockD3.dragBehavior._onEnd = handler;
                return mockD3.dragBehavior;
            }),
            _onStart: null,
            _onDrag: null,
            _onEnd: null,
            // Methods to trigger the stored handlers with proper D3-like events
            triggerStart: function (d: any) {
                if (this._onStart) {
                    this._onStart({ active: false, x: d?.x || 0, y: d?.y || 0, subject: d }, d);
                }
            },
            triggerDrag: function (d: any, pos: any) {
                if (this._onDrag) {
                    this._onDrag({ active: true, x: pos?.x || 0, y: pos?.y || 0, subject: d }, d);
                }
            },
            triggerEnd: function (d: any) {
                if (this._onEnd) {
                    this._onEnd({ active: false, x: d?.x || 0, y: d?.y || 0, subject: d }, d);
                }
            },
        },

        // Mock behaviors for zoom
        zoomBehavior: {
            on: jest.fn().mockImplementation((event, handler) => {
                if (event === 'zoom') mockD3.zoomBehavior._onZoom = handler;
                return mockD3.zoomBehavior;
            }),
            scaleExtent: jest.fn().mockReturnThis(),
            transform: jest.fn(),
            translateBy: jest.fn().mockReturnThis(),
            scaleBy: jest.fn().mockReturnThis(),
            _onZoom: null,
            // Method to trigger the zoom handler with a proper D3-like event
            triggerZoom: function (transformObj: any) {
                if (this._onZoom) {
                    const transform = transformObj || {
                        k: 1,
                        x: 0,
                        y: 0,
                        toString: () => `translate(${transformObj?.x || 0},${transformObj?.y || 0}) scale(${transformObj?.k || 1})`,
                    };
                    this._onZoom({ transform });
                }
            },
            scale: jest.fn().mockReturnThis(),
        },

        // Mock behaviors for simulation
        simulation: {
            nodes: jest.fn().mockReturnThis(),
            force: jest.fn().mockReturnThis(),
            alpha: jest.fn().mockReturnThis(),
            alphaTarget: jest.fn().mockReturnThis(),
            alphaDecay: jest.fn().mockReturnThis(),
            velocityDecay: jest.fn().mockReturnThis(),
            restart: jest.fn(),
            links: jest.fn().mockReturnThis(),
            on: jest.fn().mockImplementation((event, callback) => {
                if (event === 'tick') {
                    mockD3.simulation.tickCallback = callback;
                }
                return mockD3.simulation;
            }),
            tickCallback: null,
            // Method to trigger the tick callback
            triggerTick: function () {
                if (this.tickCallback) {
                    this.tickCallback();
                }
            },
        },

        // Factory functions - these are the key exports that need to return our mock behaviors
        drag: jest.fn().mockImplementation(() => mockD3.dragBehavior),
        zoom: jest.fn().mockImplementation(() => mockD3.zoomBehavior),
        forceSimulation: jest.fn().mockImplementation(() => mockD3.simulation),

        // Other D3 functions
        select: jest.fn().mockImplementation(() => ({
            attr: jest.fn().mockReturnThis(),
            style: jest.fn().mockReturnThis(),
            append: jest.fn().mockReturnThis(),
            selectAll: jest.fn().mockReturnValue({
                data: jest.fn().mockReturnValue({
                    join: jest.fn().mockReturnThis(),
                    enter: jest.fn().mockReturnValue({
                        append: jest.fn().mockReturnValue({
                            attr: jest.fn().mockReturnThis(),
                            style: jest.fn().mockReturnThis(),
                            text: jest.fn().mockReturnThis(),
                        }),
                    }),
                    exit: jest.fn().mockReturnValue({
                        remove: jest.fn(),
                    }),
                }),
                remove: jest.fn(),
            }),
            transition: jest.fn().mockReturnValue({
                call: jest.fn(),
                duration: jest.fn().mockReturnThis(),
            }),
            call: jest.fn(),
            node: jest.fn().mockReturnValue({
                getBBox: jest.fn().mockReturnValue({ x: 0, y: 0, width: 100, height: 100 }),
            }),
            select: jest.fn().mockImplementation((selector) => {
                return {
                    selectAll: jest.fn().mockReturnValue({
                        remove: jest.fn(),
                        data: jest.fn().mockReturnValue({
                            enter: jest.fn().mockReturnValue({
                                append: jest.fn().mockReturnValue({
                                    attr: jest.fn().mockReturnThis(),
                                    call: jest.fn().mockReturnThis(),
                                    on: jest.fn().mockReturnThis(),
                                    text: jest.fn().mockReturnThis(),
                                }),
                            }),
                        }),
                    }),
                };
            }),
        })),
        selectAll: jest.fn().mockReturnValue({
            attr: jest.fn().mockReturnThis(),
            data: jest.fn().mockReturnValue({
                join: jest.fn().mockReturnThis(),
            }),
        }),
        forceLink: jest.fn().mockReturnValue({
            id: jest.fn().mockReturnThis(),
            distance: jest.fn().mockReturnThis(),
            strength: jest.fn().mockReturnThis(),
        }),
        forceManyBody: jest.fn().mockReturnValue({
            strength: jest.fn().mockReturnThis(),
            distanceMax: jest.fn().mockReturnThis(),
        }),
        forceCollide: jest.fn().mockReturnValue({
            radius: jest.fn().mockReturnThis(),
            iterations: jest.fn().mockReturnThis(),
        }),
        forceX: jest.fn().mockReturnValue({
            strength: jest.fn().mockReturnThis(),
        }),
        forceY: jest.fn().mockReturnValue({
            strength: jest.fn().mockReturnThis(),
        }),
        zoomIdentity: {
            translate: jest.fn().mockReturnValue({
                scale: jest.fn(),
            }),
        },
    };

    // Make mockD3 available both as a module export and as ES module exports
    // @ts-ignore
    mockD3.__esModule = true;
    // @ts-ignore
    mockD3.default = mockD3;

    // Export the mockD3 object globally for test access
    // @ts-ignore
    window.mockD3 = mockD3;

    return mockD3;
});

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
    // Access the mockD3 from the global mock defined at the top
    // @ts-ignore
    const mockD3 = window.mockD3;

    beforeEach(async () => {
        // No need to redefine mockD3 here, we'll use the global one

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

    it('should handle drag events on nodes by explicitly calling trigger methods', () => {
        // Setup component with mockD3
        const mockNode = { x: 100, y: 100, fx: undefined, fy: undefined } as MemirisSimulationNode;

        component['simulation'] = {
            alphaTarget: jest.fn().mockReturnThis(),
            restart: jest.fn(),
        } as any;

        // Now calling setupDrag will use our mocked drag function
        const dragBehavior = (component as any).setupDrag();

        // Directly call the trigger methods on the mockD3 instance
        dragBehavior.triggerStart(mockNode);
        expect(mockNode.fx).toBe(mockNode.x);
        expect(mockNode.fy).toBe(mockNode.y);
        expect(component['simulation']?.alphaTarget).toHaveBeenCalledWith(0.3);
        expect(component['simulation']?.restart).toHaveBeenCalled();

        dragBehavior.triggerDrag(mockNode, { x: 200, y: 300 });
        expect(mockNode.fx).toBe(200);
        expect(mockNode.fy).toBe(300);

        // Reset mocks for the end handler test
        jest.clearAllMocks();
        dragBehavior.triggerEnd(mockNode);
        expect(mockNode.fx).toBeUndefined();
        expect(mockNode.fy).toBeUndefined();
        expect(component['simulation']?.alphaTarget).toHaveBeenCalledWith(0);
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

    it('should properly update the graph with nodes and links', () => {
        // Create mock data
        const mockData = createMockGraphData();
        (component as any).updateGraphData(mockData);

        // Mock necessary D3 selections that updateGraph uses
        const mockNodesGroup = {
            selectAll: jest.fn().mockReturnValue({
                remove: jest.fn(), // Add the remove method
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
                remove: jest.fn(), // Add the remove method
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

        // Set up SVG mock with proper structure
        component['svg'] = {
            select: jest.fn().mockImplementation((selector) => {
                if (selector === '.nodes') return mockNodesGroup;
                if (selector === '.links') return mockLinksGroup;
                return null;
            }),
        } as any;

        // Set up simulation mock
        component['simulation'] = {
            nodes: jest.fn().mockReturnThis(),
            force: jest.fn().mockReturnValue({
                links: jest.fn(),
            }),
            alpha: jest.fn().mockReturnThis(),
            restart: jest.fn(),
        } as any;

        // Set up element references to capture them during updateGraph
        component['nodeElements'] = undefined;
        component['linkElements'] = undefined;
        component['textElements'] = undefined;
        component['linkLabelElements'] = undefined;

        // Mock setupDrag to return a mock drag behavior
        const mockDrag = jest.fn();
        jest.spyOn<any, any>(component, 'setupDrag').mockReturnValue(mockDrag);

        // Execute updateGraph
        (component as any).updateGraph();

        // Verify SVG selections were used
        expect(component['svg']!.select).toHaveBeenCalledWith('.nodes');
        expect(component['svg']!.select).toHaveBeenCalledWith('.links');

        // Verify remove was called on the selections
        expect(mockNodesGroup.selectAll().remove).toHaveBeenCalled();
        expect(mockLinksGroup.selectAll().remove).toHaveBeenCalled();

        // Verify simulation was updated
        expect(component['simulation']!.nodes).toHaveBeenCalledWith(component['nodes']);
        expect(component['simulation']!.alpha).toHaveBeenCalledWith(0.3);
        expect(component['simulation']!.restart).toHaveBeenCalled();
    });

    it('should fully render the graph with node and link elements', () => {
        const mockData = createMockGraphData();

        // Create a proper SVG structure for testing
        const svgElement = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        const nodesGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        nodesGroup.classList.add('nodes');
        const linksGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        linksGroup.classList.add('links');

        svgElement.appendChild(nodesGroup);
        svgElement.appendChild(linksGroup);

        // Use a mock object for svgContainer instead of spying on nativeElement
        component['svgContainer'] = (() => {
            return { nativeElement: svgElement };
        }) as any;

        // Update our mock to handle the svgElement case
        mockD3.selectImpl = jest.fn().mockImplementation((elem) => {
            if (elem === svgElement) {
                return {
                    attr: jest.fn().mockReturnThis(),
                    selectAll: jest.fn().mockImplementation((selector) => {
                        return {
                            remove: jest.fn(), // Ensure remove is available
                        };
                    }),
                    append: jest.fn().mockImplementation(() => {
                        return {
                            attr: jest.fn().mockReturnThis(),
                            append: jest.fn().mockReturnThis(),
                        };
                    }),
                    call: jest.fn(),
                    select: jest.fn().mockImplementation((selector) => {
                        if (selector === '.nodes') {
                            return {
                                selectAll: jest.fn().mockReturnValue({
                                    remove: jest.fn(),
                                    data: jest.fn().mockReturnValue({
                                        enter: jest.fn().mockReturnValue({
                                            append: jest.fn().mockReturnValue({
                                                attr: jest.fn().mockReturnThis(),
                                                call: jest.fn().mockReturnThis(),
                                                on: jest.fn().mockReturnThis(),
                                                text: jest.fn().mockReturnThis(),
                                            }),
                                        }),
                                    }),
                                }),
                            };
                        }
                        if (selector === '.links') {
                            return {
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
                        }
                        return null;
                    }),
                };
            }
            return mockD3.selectImpl(elem);
        });

        // Create proper jest spies for simulation methods
        const nodesSpy = jest.fn().mockReturnThis();
        const alphaSpy = jest.fn().mockReturnThis();
        const restartSpy = jest.fn();

        // Set up our simulation mock with proper jest spies
        component['simulation'] = {
            nodes: nodesSpy,
            force: jest.fn().mockReturnValue({
                links: jest.fn().mockReturnThis(),
            }),
            alpha: alphaSpy,
            restart: restartSpy,
            on: jest.fn().mockImplementation((event, callback) => {
                if (event === 'tick') {
                    // Call the tick function to increase coverage
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

        // Update graph data and trigger initialization
        (component as any).updateGraphData(mockData);

        // Directly call initializeGraph to set up SVG structure
        (component as any).initializeGraph();

        // Now call updateGraph which should use the structure
        (component as any).updateGraph();

        // Verify that we attempted to update the graph using the jest spies
        expect(nodesSpy).toHaveBeenCalled();
        expect(alphaSpy).toHaveBeenCalled();
        expect(restartSpy).toHaveBeenCalled();
    });
});
