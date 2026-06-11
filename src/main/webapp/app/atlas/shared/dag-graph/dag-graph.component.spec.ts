import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { DagGraphComponent } from 'app/atlas/shared/dag-graph/dag-graph.component';
import { DagGraphEdge, DagGraphNode } from 'app/atlas/shared/dag-graph/dag-graph.model';

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
    setupTestBed({ zoneless: true });
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
            expect(backEdge.textPath).not.toBe(backEdge.path);
        });

        it('should keep parallel edges between the same nodes', () => {
            host.edges.set([
                { id: 'edge-1', source: 'a', target: 'b' },
                { id: 'edge-1b', source: 'a', target: 'b' },
            ]);
            fixture.detectChanges();

            expect(component.layout().edges.map((layoutEdge) => layoutEdge.edge.id)).toEqual(['edge-1', 'edge-1b']);
        });

        it('should skip edges with missing endpoints', () => {
            host.edges.set([...measuredEdges(), { id: 'edge-ghost', source: 'a', target: 'does-not-exist' }]);
            fixture.detectChanges();

            expect(component.layout().edges).toHaveLength(2);
            expect(component.layout().nodes).toHaveLength(3);
        });

        it('should handle an empty graph without errors', () => {
            host.nodes.set([]);
            host.edges.set([]);
            fixture.detectChanges();

            expect(component.layout().nodes).toHaveLength(0);
            expect(component.layout().edges).toHaveLength(0);
            expect(fixture.debugElement.query(By.css('.minimap'))).toBeNull();
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
});
