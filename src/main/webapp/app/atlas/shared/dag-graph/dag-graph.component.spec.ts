import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { curveBundle, line } from 'd3-shape';
import { layout as dagreLayout } from '@dagrejs/dagre';
import { DagGraphComponent } from 'app/atlas/shared/dag-graph/dag-graph.component';
import { DagGraphEdge, DagGraphNode, GraphPosition } from 'app/atlas/shared/dag-graph/dag-graph.model';

// Wrap the real dagre layout so a single test can force it to throw and exercise the empty-graph
// fallback; by default it delegates to the real implementation, so all other tests are unaffected.
vi.mock('@dagrejs/dagre', async (importOriginal) => {
    const actual = (await importOriginal()) as typeof import('@dagrejs/dagre');
    return { ...actual, layout: vi.fn(actual.layout) };
});

@Component({
    imports: [DagGraphComponent],
    template: `
        <jhi-dag-graph [nodes]="nodes()" [edges]="edges()" [enablePan]="enablePan()" [enableZoom]="enableZoom()" [showMiniMap]="showMiniMap()">
            <ng-template #defsTemplate>
                <svg:marker id="arrow" />
            </ng-template>
            <ng-template #nodeTemplate let-node>
                <svg:g class="test-node" [attr.data-node-id]="node.node.id" />
            </ng-template>
            <ng-template #linkTemplate let-link>
                <svg:path class="test-edge" [attr.data-edge-id]="link.edge.id" [attr.d]="link.path" />
            </ng-template>
        </jhi-dag-graph>
    `,
})
class TestHostComponent {
    readonly nodes = signal<DagGraphNode[]>([]);
    readonly edges = signal<DagGraphEdge[]>([]);
    readonly enablePan = signal(true);
    readonly enableZoom = signal(true);
    readonly showMiniMap = signal(true);
}

@Component({
    imports: [DagGraphComponent],
    template: `
        <jhi-dag-graph [nodes]="nodes()" [edges]="edges()">
            <ng-template #nodeTemplate let-node>
                <svg:g class="test-node" />
            </ng-template>
        </jhi-dag-graph>
    `,
})
class MinimalTestHostComponent {
    readonly nodes = signal<DagGraphNode[]>([]);
    readonly edges = signal<DagGraphEdge[]>([]);
}

describe('DagGraphComponent', () => {
    let fixture: ComponentFixture<TestHostComponent>;
    let host: TestHostComponent;
    let component: DagGraphComponent;

    const measuredNodes = (): DagGraphNode[] => [
        { id: 'a', label: 'A', dimension: { width: 80, height: 45.59 } },
        { id: 'b', label: 'B', dimension: { width: 120, height: 45.59 } },
        { id: 'c', label: 'C', dimension: { width: 100, height: 45.59 } },
    ];
    const measuredEdges = (): DagGraphEdge[] => [
        { id: 'edge-1', source: 'a', target: 'b' },
        { id: 'edge-2', source: 'b', target: 'c' },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TestHostComponent, MinimalTestHostComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TestHostComponent);
        host = fixture.componentInstance;
        component = fixture.debugElement.query(By.directive(DagGraphComponent)).componentInstance;
    });

    function svgElement(): Element {
        return fixture.debugElement.query(By.css('svg.dag-graph')).nativeElement;
    }

    function contentTransform(): string {
        return fixture.debugElement.query(By.css('.graph-content')).nativeElement.getAttribute('transform');
    }

    function dispatchPointerEvent(target: Element, type: string, clientX: number, clientY: number): void {
        // jsdom has no PointerEvent constructor; MouseEvent carries everything the component reads
        target.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true, clientX, clientY, button: 0 }));
    }

    describe('layout', () => {
        beforeEach(() => {
            host.nodes.set(measuredNodes());
            host.edges.set(measuredEdges());
            fixture.detectChanges();
        });

        it('should lay out connected nodes left-to-right with 20px margins', () => {
            const layout = component.layout();
            const byId = new Map(layout.nodes.map((layoutNode) => [layoutNode.node.id, layoutNode]));
            expect(byId.get('b')!.position.x).toBeGreaterThan(byId.get('a')!.position.x);
            expect(byId.get('c')!.position.x).toBeGreaterThan(byId.get('b')!.position.x);

            const minLeft = Math.min(...layout.nodes.map((layoutNode) => layoutNode.position.x - layoutNode.dimension.width / 2));
            const minTop = Math.min(...layout.nodes.map((layoutNode) => layoutNode.position.y - layoutNode.dimension.height / 2));
            expect(minLeft).toBe(20);
            expect(minTop).toBe(20);
        });

        it('should place each node via a top-left corner transform', () => {
            for (const layoutNode of component.layout().nodes) {
                const expected = `translate(${layoutNode.position.x - layoutNode.dimension.width / 2}, ${layoutNode.position.y - layoutNode.dimension.height / 2})`;
                expect(layoutNode.transform).toBe(expected);
            }
            expect(fixture.debugElement.queryAll(By.css('.test-node'))).toHaveLength(3);
        });

        it('should generate curved svg paths through the dagre points', () => {
            const layout = component.layout();
            expect(layout.edges).toHaveLength(2);
            for (const layoutEdge of layout.edges) {
                expect(layoutEdge.points.length).toBeGreaterThanOrEqual(2);
                expect(layoutEdge.path).toMatch(/^M/);
                expect(layoutEdge.dominantBaseline).toBe('text-after-edge');
                expect(layoutEdge.textPath).toBe(layoutEdge.path);
            }
            const renderedEdges = fixture.debugElement.queryAll(By.css('.test-edge'));
            expect(renderedEdges).toHaveLength(2);
            expect(renderedEdges[0].nativeElement.getAttribute('d')).toMatch(/^M/);
        });

        it('should provide a reversed text path for right-to-left edges', () => {
            host.edges.set([...measuredEdges(), { id: 'edge-back', source: 'c', target: 'a' }]);
            fixture.detectChanges();

            const backEdge = component.layout().edges.find((layoutEdge) => layoutEdge.edge.id === 'edge-back')!;
            expect(backEdge.points[0].x).toBeGreaterThan(backEdge.points[backEdge.points.length - 1].x);
            expect(backEdge.dominantBaseline).toBe('text-before-edge');
            // the text path is exactly the curve through the reversed point list, so labels read left-to-right
            const expectedReversed = line<GraphPosition>()
                .x((point) => point.x)
                .y((point) => point.y)
                .curve(curveBundle.beta(1))([...backEdge.points].reverse());
            expect(backEdge.textPath).toBe(expectedReversed);
            expect(backEdge.textPath).not.toBe(backEdge.path);
        });

        it('should pass node and edge data/labels through to the layout untouched', () => {
            host.nodes.set([
                { id: 'a', label: 'A', dimension: { width: 80, height: 45.59 } },
                { id: 'b', label: 'B', dimension: { width: 80, height: 45.59 } },
            ]);
            host.edges.set([{ id: 'edge-1', source: 'a', target: 'b', label: 'EXTENDS', data: { id: 7 } }]);
            fixture.detectChanges();

            const layoutEdge = component.layout().edges[0];
            expect(layoutEdge.edge.label).toBe('EXTENDS');
            expect(layoutEdge.edge.data).toEqual({ id: 7 });
            expect(component.layout().nodes.find((layoutNode) => layoutNode.node.id === 'a')!.node.label).toBe('A');
        });

        it('should lay out a cyclic graph without throwing', () => {
            host.nodes.set(measuredNodes());
            host.edges.set([
                { id: 'edge-1', source: 'a', target: 'b' },
                { id: 'edge-2', source: 'b', target: 'c' },
                { id: 'edge-cycle', source: 'c', target: 'a' },
            ]);
            expect(() => fixture.detectChanges()).not.toThrow();

            expect(component.layout().nodes).toHaveLength(3);
            expect(component.layout().edges).toHaveLength(3);
            for (const layoutEdge of component.layout().edges) {
                expect(layoutEdge.points.length).toBeGreaterThanOrEqual(2);
                expect(layoutEdge.path).toMatch(/^M/);
            }
        });

        it('should keep and lay out a self-loop edge', () => {
            host.nodes.set(measuredNodes());
            host.edges.set([{ id: 'edge-self', source: 'a', target: 'a' }]);
            expect(() => fixture.detectChanges()).not.toThrow();

            const selfEdge = component.layout().edges.find((layoutEdge) => layoutEdge.edge.id === 'edge-self')!;
            expect(selfEdge).toBeDefined();
            expect(selfEdge.points.length).toBeGreaterThanOrEqual(2);
            expect(selfEdge.path).toMatch(/^M/);
        });

        it('should keep parallel edges between the same nodes', () => {
            host.edges.set([
                { id: 'edge-1', source: 'a', target: 'b' },
                { id: 'edge-1b', source: 'a', target: 'b' },
            ]);
            fixture.detectChanges();

            expect(component.layout().edges.map((layoutEdge) => layoutEdge.edge.id)).toEqual(['edge-1', 'edge-1b']);
        });

        it('should skip edges with a missing source or target', () => {
            host.edges.set([
                ...measuredEdges(),
                { id: 'edge-ghost-target', source: 'a', target: 'does-not-exist' },
                { id: 'edge-ghost-source', source: 'does-not-exist', target: 'b' },
            ]);
            fixture.detectChanges();

            expect(component.layout().edges.map((layoutEdge) => layoutEdge.edge.id)).toEqual(['edge-1', 'edge-2']);
            expect(component.layout().nodes).toHaveLength(3);
        });

        it('should handle an empty graph without errors', () => {
            host.nodes.set([]);
            host.edges.set([]);
            fixture.detectChanges();

            expect(component.layout().nodes).toHaveLength(0);
            expect(component.layout().edges).toHaveLength(0);
            // dagre reports undefined/NaN dimensions for an empty graph; the finite-guard maps them to 0
            expect(component.layout().width).toBe(0);
            expect(component.layout().height).toBe(0);
            expect(fixture.debugElement.query(By.css('.minimap'))).toBeNull();
        });

        it('should fall back to an empty graph and log when the layout engine throws', () => {
            const consoleSpy = vi.spyOn(globalThis.console, 'error').mockImplementation(() => undefined);
            vi.mocked(dagreLayout).mockImplementationOnce(() => {
                throw new Error('layout failure');
            });
            host.nodes.set(measuredNodes());
            host.edges.set(measuredEdges());
            fixture.detectChanges();

            // the try/catch around the dagre call must degrade gracefully instead of breaking change detection
            expect(component.layout()).toEqual({ nodes: [], edges: [], width: 0, height: 0 });
            expect(consoleSpy).toHaveBeenCalled();
            consoleSpy.mockRestore();
        });
    });

    describe('measurement', () => {
        it('should hide the graph and use default sizes until all nodes are measured', () => {
            host.nodes.set([{ id: 'a' }, { id: 'b', dimension: { width: 120, height: 45.59 } }]);
            host.edges.set([{ id: 'edge-1', source: 'a', target: 'b' }]);
            fixture.detectChanges();

            expect(component.allMeasured()).toBeFalsy();
            expect(fixture.debugElement.query(By.css('.graph-content')).nativeElement.style.visibility).toBe('hidden');
            const unmeasured = component.layout().nodes.find((layoutNode) => layoutNode.node.id === 'a')!;
            expect(unmeasured.dimension).toEqual({ width: 100, height: 45.59 });
        });

        it('should re-layout and become visible once dimensions arrive', () => {
            host.nodes.set([{ id: 'a' }, { id: 'b', dimension: { width: 120, height: 45.59 } }]);
            host.edges.set([{ id: 'edge-1', source: 'a', target: 'b' }]);
            fixture.detectChanges();
            const widthBefore = component.layout().width;

            host.nodes.set([
                { id: 'a', dimension: { width: 300, height: 45.59 } },
                { id: 'b', dimension: { width: 120, height: 45.59 } },
            ]);
            fixture.detectChanges();

            expect(component.allMeasured()).toBeTruthy();
            expect(fixture.debugElement.query(By.css('.graph-content')).nativeElement.style.visibility).toBe('visible');
            expect(component.layout().width).toBeGreaterThan(widthBefore);
        });
    });

    describe('zoom', () => {
        beforeEach(() => {
            host.nodes.set(measuredNodes());
            host.edges.set(measuredEdges());
            fixture.detectChanges();
        });

        it('should zoom in on wheel up when zoom is enabled', () => {
            svgElement().dispatchEvent(new WheelEvent('wheel', { deltaY: -100, bubbles: true, cancelable: true }));
            fixture.detectChanges();

            const scale = Number(contentTransform().match(/scale\(([\d.]+)\)/)![1]);
            expect(scale).toBeGreaterThan(1);
        });

        it('should not zoom when zoom is disabled', () => {
            host.enableZoom.set(false);
            fixture.detectChanges();

            const wheelEvent = new WheelEvent('wheel', { deltaY: -100, bubbles: true, cancelable: true });
            svgElement().dispatchEvent(wheelEvent);
            fixture.detectChanges();

            expect(contentTransform()).toBe('translate(0, 0) scale(1)');
            // the page must keep scrolling normally when zoom is off
            expect(wheelEvent.defaultPrevented).toBeFalsy();
        });

        it('should anchor the zoom at the cursor position', () => {
            // jsdom getBoundingClientRect() returns left/top 0, so the cursor in host coords equals clientX/Y
            svgElement().dispatchEvent(new WheelEvent('wheel', { deltaY: -100, clientX: 100, clientY: 100, bubbles: true, cancelable: true }));
            fixture.detectChanges();

            const transform = contentTransform();
            const panX = Number(transform.match(/translate\(([-\d.]+), ([-\d.]+)\)/)![1]);
            const panY = Number(transform.match(/translate\(([-\d.]+), ([-\d.]+)\)/)![2]);
            const scale = Number(transform.match(/scale\(([-\d.]+)\)/)![1]);
            // the graph point under the cursor (100,100) must stay fixed: (cursor - pan) / scale === 100
            expect((100 - panX) / scale).toBeCloseTo(100, 5);
            expect((100 - panY) / scale).toBeCloseTo(100, 5);
        });

        it('should clamp zoom to the maximum on repeated zoom-in', () => {
            for (let i = 0; i < 8; i++) {
                svgElement().dispatchEvent(new WheelEvent('wheel', { deltaY: -200, bubbles: true, cancelable: true }));
            }
            fixture.detectChanges();

            const scale = Number(contentTransform().match(/scale\(([-\d.]+)\)/)![1]);
            expect(scale).toBe(4);
        });

        it('should clamp zoom to the minimum on repeated zoom-out', () => {
            for (let i = 0; i < 8; i++) {
                svgElement().dispatchEvent(new WheelEvent('wheel', { deltaY: 200, bubbles: true, cancelable: true }));
            }
            fixture.detectChanges();

            const scale = Number(contentTransform().match(/scale\(([-\d.]+)\)/)![1]);
            expect(scale).toBeCloseTo(0.1, 5);
        });
    });

    describe('pan', () => {
        beforeEach(() => {
            host.nodes.set(measuredNodes());
            host.edges.set(measuredEdges());
            fixture.detectChanges();
        });

        it('should pan when dragging the background rect', () => {
            const panningRect = fixture.debugElement.query(By.css('.panning-rect')).nativeElement;
            dispatchPointerEvent(panningRect, 'pointerdown', 50, 50);
            dispatchPointerEvent(svgElement(), 'pointermove', 80, 95);
            fixture.detectChanges();

            expect(contentTransform()).toBe('translate(30, 45) scale(1)');

            dispatchPointerEvent(svgElement(), 'pointerup', 80, 95);
            dispatchPointerEvent(svgElement(), 'pointermove', 200, 200);
            fixture.detectChanges();

            expect(contentTransform()).toBe('translate(30, 45) scale(1)');
        });

        it('should not pan when panning is disabled', () => {
            host.enablePan.set(false);
            fixture.detectChanges();

            const panningRect = fixture.debugElement.query(By.css('.panning-rect')).nativeElement;
            dispatchPointerEvent(panningRect, 'pointerdown', 50, 50);
            dispatchPointerEvent(svgElement(), 'pointermove', 80, 95);
            fixture.detectChanges();

            expect(contentTransform()).toBe('translate(0, 0) scale(1)');
        });

        it('should not start panning from a node', () => {
            const node = fixture.debugElement.query(By.css('.test-node')).nativeElement;
            dispatchPointerEvent(node, 'pointerdown', 50, 50);
            dispatchPointerEvent(svgElement(), 'pointermove', 80, 95);
            fixture.detectChanges();

            expect(contentTransform()).toBe('translate(0, 0) scale(1)');
        });
    });

    describe('minimap and templates', () => {
        it('should render the minimap with one rect per node and a viewport indicator', () => {
            host.nodes.set(measuredNodes());
            host.edges.set(measuredEdges());
            fixture.detectChanges();

            expect(fixture.debugElement.queryAll(By.css('.minimap-nodes rect'))).toHaveLength(3);
            expect(fixture.debugElement.query(By.css('.minimap-viewport'))).not.toBeNull();
        });

        it('should hide the minimap when disabled or while unmeasured', () => {
            host.nodes.set(measuredNodes());
            host.edges.set(measuredEdges());
            host.showMiniMap.set(false);
            fixture.detectChanges();
            expect(fixture.debugElement.query(By.css('.minimap'))).toBeNull();

            host.showMiniMap.set(true);
            host.nodes.set([{ id: 'a' }]);
            fixture.detectChanges();
            expect(fixture.debugElement.query(By.css('.minimap'))).toBeNull();
        });

        it('should render the projected defs template', () => {
            host.nodes.set(measuredNodes());
            host.edges.set(measuredEdges());
            fixture.detectChanges();

            expect(fixture.debugElement.query(By.css('defs marker#arrow'))).not.toBeNull();
        });

        it('should fall back to the default edge template when no link template is projected', () => {
            const minimalFixture = TestBed.createComponent(MinimalTestHostComponent);
            minimalFixture.componentInstance.nodes.set(measuredNodes());
            minimalFixture.componentInstance.edges.set(measuredEdges());
            minimalFixture.detectChanges();

            const defaultEdges = minimalFixture.debugElement.queryAll(By.css('path.line'));
            expect(defaultEdges).toHaveLength(2);
            expect(defaultEdges[0].nativeElement.getAttribute('d')).toMatch(/^M/);
            expect(defaultEdges[0].nativeElement.getAttribute('marker-end')).toBe('url(#arrow)');
        });
    });

    describe('minimap math', () => {
        // The default test ResizeObserver is a no-op, so hostSize stays 0; install one that reports a
        // known size synchronously on observe() so the minimap transform and viewport math are exercised.
        const HOST_WIDTH = 400;
        const HOST_HEIGHT = 300;
        const originalResizeObserver = globalThis.ResizeObserver;

        beforeEach(() => {
            globalThis.ResizeObserver = class {
                constructor(private readonly callback: ResizeObserverCallback) {}
                observe(): void {
                    this.callback([{ contentRect: { width: HOST_WIDTH, height: HOST_HEIGHT } } as ResizeObserverEntry], this as unknown as ResizeObserver);
                }
                unobserve(): void {}
                disconnect(): void {}
            };
        });

        afterEach(() => {
            globalThis.ResizeObserver = originalResizeObserver;
        });

        it('should scale the minimap and size its background to the graph extent', () => {
            const sizedFixture = TestBed.createComponent(TestHostComponent);
            sizedFixture.componentInstance.nodes.set(measuredNodes());
            sizedFixture.componentInstance.edges.set(measuredEdges());
            sizedFixture.detectChanges();
            const graph: DagGraphComponent = sizedFixture.debugElement.query(By.directive(DagGraphComponent)).componentInstance;
            const { width, height } = graph.layout();

            const background = sizedFixture.debugElement.query(By.css('.minimap-background')).nativeElement;
            expect(Number(background.getAttribute('width'))).toBeCloseTo(width, 5);
            expect(Number(background.getAttribute('height'))).toBeCloseTo(height, 5);

            // minimapScale fits the graph into the 200x150 default box (floored at 1); transform applies 1/scale
            const expectedScale = Math.max(width / 200, height / 150, 1);
            const minimapTransform = sizedFixture.debugElement.query(By.css('.minimap')).nativeElement.getAttribute('transform');
            expect(Number(minimapTransform.match(/scale\(([-\d.]+)\)/)![1])).toBeCloseTo(1 / expectedScale, 5);
        });

        it('should size the viewport indicator to the visible region, clamped to the graph', () => {
            const sizedFixture = TestBed.createComponent(TestHostComponent);
            sizedFixture.componentInstance.nodes.set(measuredNodes());
            sizedFixture.componentInstance.edges.set(measuredEdges());
            sizedFixture.detectChanges();
            const graph: DagGraphComponent = sizedFixture.debugElement.query(By.directive(DagGraphComponent)).componentInstance;
            const { width, height } = graph.layout();

            // at pan 0 / zoom 1 the visible region starts at the origin and spans the host size, clamped to the graph extent
            const viewport = sizedFixture.debugElement.query(By.css('.minimap-viewport')).nativeElement;
            expect(Number(viewport.getAttribute('x'))).toBe(0);
            expect(Number(viewport.getAttribute('y'))).toBe(0);
            expect(Number(viewport.getAttribute('width'))).toBeCloseTo(Math.min(HOST_WIDTH, width), 5);
            expect(Number(viewport.getAttribute('height'))).toBeCloseTo(Math.min(HOST_HEIGHT, height), 5);
        });
    });
});
