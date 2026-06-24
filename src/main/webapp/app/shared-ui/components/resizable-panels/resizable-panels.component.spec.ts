import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DOCUMENT } from '@angular/common';
import { By } from '@angular/platform-browser';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { faAlignLeft, faComment } from '@fortawesome/free-solid-svg-icons';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PanelDirective, ResizablePanelsComponent } from 'app/shared-ui/components/resizable-panels/resizable-panels.component';

class ResizeObserverMock {
    static instances: ResizeObserverMock[] = [];

    constructor() {
        ResizeObserverMock.instances.push(this);
    }

    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
}

@Component({
    template: `
        <jhi-resizable-panels [useViewportWidthForCollapse]="useViewportWidthForCollapse" [storageKey]="storageKey">
            <ng-template jhiPanel [label]="'left'" [icon]="faAlignLeft"><div id="left-marker">Left Content</div></ng-template>
            <ng-template jhiPanel [label]="'right'">Right Content</ng-template>
            <ng-template jhiPanel [label]="'iris'" [icon]="faComment" [startsCollapsed]="irisStartsCollapsed">Iris Content</ng-template>
        </jhi-resizable-panels>
    `,
    imports: [ResizablePanelsComponent, PanelDirective],
})
class ResizablePanelsTestComponent {
    protected readonly faAlignLeft = faAlignLeft;
    protected readonly faComment = faComment;
    irisStartsCollapsed = false;
    useViewportWidthForCollapse = false;
    storageKey: string | undefined = undefined;
}

describe('ResizablePanelsComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ResizablePanelsTestComponent>;

    beforeEach(async () => {
        vi.stubGlobal('ResizeObserver', ResizeObserverMock);
        await TestBed.configureTestingModule({
            imports: [ResizablePanelsTestComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
    });

    afterEach(() => {
        ResizeObserverMock.instances = [];
        vi.unstubAllGlobals();
        localStorage.clear();
    });

    const createFixture = (irisStartsCollapsed = false, useViewportWidthForCollapse = false) => {
        fixture = TestBed.createComponent(ResizablePanelsTestComponent);
        fixture.componentInstance.irisStartsCollapsed = irisStartsCollapsed;
        fixture.componentInstance.useViewportWidthForCollapse = useViewportWidthForCollapse;
        fixture.detectChanges();
        return fixture.debugElement.query(By.directive(ResizablePanelsComponent)).componentInstance as ResizablePanelsComponent;
    };

    it('reopens with a visible split (slider not jammed to the edge) after a drag-to-collapse', () => {
        const component = createFixture();
        const splitter = fixture.nativeElement.querySelector('p-splitter');
        const panels = () => Array.from(splitter.querySelectorAll('[data-pc-section="panel"]')) as HTMLElement[];

        // Drag-to-collapse: the splitter leaves the panels at the near-zero position they were dragged to.
        component['onResizeEnd']([100, 0]);
        const [left, right] = panels();
        left.style.flexBasis = 'calc(100% - 12px)';
        right.style.flexBasis = 'calc(0% - 12px)';
        fixture.detectChanges();
        expect(component.isRightPanelCollapsed()).toBe(true);

        // Reopen via the icon rail: the right panel must come back with a usable width, not 0%.
        component.expandRightPanel(0);
        fixture.detectChanges();

        expect(component.isRightPanelCollapsed()).toBe(false);
        expect(right.style.flexBasis).toBe('calc(35% - 12px)');
        expect(left.style.flexBasis).toBe('calc(65% - 12px)');
        expect(component.savedSizes()).toEqual([65, 35]);
    });

    it('should render the right panel expanded by default', () => {
        const component = createFixture();

        expect(component.isRightPanelCollapsed()).toBe(false);
        expect(fixture.nativeElement.querySelector('.collapsed-right-panel')).toBeNull();
        expect(fixture.nativeElement.textContent).toContain('Right Content');
    });

    it('should render the resizable splitter in wide expanded mode', () => {
        const component = createFixture();

        expect(component.isNarrow()).toBe(false);
        expect(component.isRightPanelCollapsed()).toBe(false);
        expect(fixture.nativeElement.querySelector('p-splitter')).not.toBeNull();
    });

    it('should keep the splitter mounted but show the icon rail when the right panel is collapsed', () => {
        const component = createFixture(true);

        expect(component.isRightPanelCollapsed()).toBe(true);
        // The splitter stays mounted (so the left panel is preserved) and switches to the icon rail via the class.
        const splitter = fixture.nativeElement.querySelector('p-splitter');
        expect(splitter).not.toBeNull();
        expect(splitter.classList.contains('right-collapsed')).toBe(true);
        expect(fixture.nativeElement.querySelector('.collapsed-right-panel')).not.toBeNull();
    });

    it('preserves the left panel element instance across a collapse/expand toggle (no editor recreation)', () => {
        const component = createFixture();
        const before = fixture.nativeElement.querySelector('#left-marker') as HTMLElement;
        expect(before).not.toBeNull();

        component.collapseRightPanel();
        fixture.detectChanges();
        const whileCollapsed = fixture.nativeElement.querySelector('#left-marker') as HTMLElement;
        // Same DOM node => the left panel (and anything it hosts, e.g. the modeling editor) was not destroyed.
        expect(whileCollapsed).toBe(before);

        component.expandRightPanel(0);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('#left-marker')).toBe(before);
    });

    it('snaps the right panel closed when it is dragged below the collapse threshold', () => {
        const component = createFixture();
        const splitter = fixture.debugElement.query(By.css('p-splitter'));

        // Dragged to a 6% right panel (below the default 12% threshold) => snap closed.
        splitter.componentInstance.onResizeEnd.emit({ originalEvent: new Event('mouseup'), sizes: [94, 6] });

        expect(component.isRightPanelCollapsed()).toBe(true);
        // The near-zero drag size is not remembered as the split; a sensible default is kept for reopening.
        expect(component.savedSizes()).toEqual([65, 35]);
    });

    it('keeps a usable split (in memory and in storage) when snapping closed after a previous resize', () => {
        fixture = TestBed.createComponent(ResizablePanelsTestComponent);
        fixture.componentInstance.storageKey = 'test-split';
        fixture.detectChanges();
        const component = fixture.debugElement.query(By.directive(ResizablePanelsComponent)).componentInstance as ResizablePanelsComponent;
        const splitter = fixture.debugElement.query(By.css('p-splitter'));

        // A usable resize is remembered as-is.
        splitter.componentInstance.onResizeEnd.emit({ originalEvent: new Event('mouseup'), sizes: [70, 30] });
        expect(component.savedSizes()).toEqual([70, 30]);

        // Dragging below the threshold snaps shut. The near-zero size must not survive: neither in savedSizes (used
        // when reopening) nor in the splitter's localStorage entry (used when reloading); both keep the usable split.
        splitter.componentInstance.onResizeEnd.emit({ originalEvent: new Event('mouseup'), sizes: [94, 6] });
        expect(component.isRightPanelCollapsed()).toBe(true);
        expect(component.savedSizes()).toEqual([70, 30]);
        expect(localStorage.getItem('test-split')).toBe(JSON.stringify([70, 30]));
    });

    it('seeds the saved split from localStorage on init so a reload + reopen keeps the persisted split', () => {
        // Simulate a prior session having persisted a custom split under the storage key.
        localStorage.setItem('test-split', JSON.stringify([72, 28]));

        fixture = TestBed.createComponent(ResizablePanelsTestComponent);
        fixture.componentInstance.storageKey = 'test-split';
        fixture.detectChanges();
        const component = fixture.debugElement.query(By.directive(ResizablePanelsComponent)).componentInstance as ResizablePanelsComponent;

        // Seeded from storage on init, not left undefined (which would later fall back to the default split).
        expect(component.savedSizes()).toEqual([72, 28]);

        // A collapse/expand cycle without a drag in between must preserve the persisted split, not reset to the default.
        component.collapseRightPanel();
        fixture.detectChanges();
        component.expandRightPanel(0);
        fixture.detectChanges();

        expect(component.savedSizes()).toEqual([72, 28]);
        const splitter = fixture.nativeElement.querySelector('p-splitter');
        const [, right] = Array.from(splitter.querySelectorAll('[data-pc-section="panel"]')) as HTMLElement[];
        expect(right.style.flexBasis).toBe('calc(28% - 12px)');
    });

    it('keeps the usable split when snap-collapsing after a reopen (splitter buffer is not aliased to savedSizes)', () => {
        fixture = TestBed.createComponent(ResizablePanelsTestComponent);
        fixture.componentInstance.storageKey = 'test-split';
        fixture.detectChanges();
        const component = fixture.debugElement.query(By.directive(ResizablePanelsComponent)).componentInstance as ResizablePanelsComponent;
        const splitter = fixture.debugElement.query(By.css('p-splitter'));

        // Establish a usable split, then collapse and reopen. Reopening feeds savedSizes into [panelSizes]; if that
        // array were the signal's own buffer, the next drag would mutate it in place and corrupt the remembered split.
        splitter.componentInstance.onResizeEnd.emit({ originalEvent: new Event('mouseup'), sizes: [70, 30] });
        component.collapseRightPanel();
        fixture.detectChanges();
        component.expandRightPanel(0);
        fixture.detectChanges();

        // Simulate p-splitter mutating its internal buffer in place during a drag down to a near-zero right panel,
        // then ending the drag below the snap threshold. The remembered usable split must survive untouched.
        const internalBuffer = splitter.componentInstance.panelSizes as number[];
        internalBuffer[0] = 94;
        internalBuffer[1] = 6;
        splitter.componentInstance.onResizeEnd.emit({ originalEvent: new Event('mouseup'), sizes: internalBuffer });

        expect(component.isRightPanelCollapsed()).toBe(true);
        expect(component.savedSizes()).toEqual([70, 30]);
        expect(localStorage.getItem('test-split')).toBe(JSON.stringify([70, 30]));
    });

    it('stores an independent copy of the sizes (p-splitter mutates its own array in place)', () => {
        const component = createFixture();
        const splitter = fixture.debugElement.query(By.css('p-splitter'));

        const sizes = [40, 60];
        splitter.componentInstance.onResizeEnd.emit({ originalEvent: new Event('mouseup'), sizes });
        // Simulate p-splitter reusing and mutating the same array on a later drag.
        sizes[0] = 99;
        sizes[1] = 1;

        expect(component.savedSizes()).toEqual([40, 60]);
    });

    it('should not render the splitter in narrow mode', () => {
        const component = createFixture();
        component['_isNarrow'].set(true);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('p-splitter')).toBeNull();
    });

    it('should default the split sizes and persist them from the splitter resize end event', () => {
        const component = createFixture();

        expect(component.savedSizes()).toBeUndefined();

        const splitter = fixture.debugElement.query(By.css('p-splitter'));
        splitter.componentInstance.onResizeEnd.emit({ originalEvent: new Event('mouseup'), sizes: [40, 60] });

        expect(component.savedSizes()).toEqual([40, 60]);
    });

    it('should start collapsed without changing the active right panel', () => {
        const component = createFixture(true);

        expect(component.isRightPanelCollapsed()).toBe(true);
        expect(component.activeRightIndex()).toBe(0);
        expect(fixture.nativeElement.querySelector('.collapsed-right-panel')).not.toBeNull();
        expect(fixture.nativeElement.textContent).not.toContain('Iris Content');
    });

    it('should expand the selected collapsed right panel on click', () => {
        const component = createFixture(true);
        const collapsedTabs = fixture.nativeElement.querySelectorAll('.collapsed-right-panel-tab') as NodeListOf<HTMLButtonElement>;

        collapsedTabs[1].click();
        fixture.detectChanges();

        expect(component.isRightPanelCollapsed()).toBe(false);
        expect(component.activeRightIndex()).toBe(1);
        expect(fixture.nativeElement.textContent).toContain('Iris Content');
    });

    it('should not collapse again after the user expands the right panel', () => {
        const component = createFixture(true);
        const collapsedTabs = fixture.nativeElement.querySelectorAll('.collapsed-right-panel-tab') as NodeListOf<HTMLButtonElement>;

        collapsedTabs[1].click();
        fixture.detectChanges();
        fixture.detectChanges();

        expect(component.isRightPanelCollapsed()).toBe(false);
        expect(fixture.nativeElement.textContent).toContain('Iris Content');
    });

    it('should observe the host element for the responsive breakpoint by default', () => {
        createFixture();
        const observedElement = fixture.debugElement.query(By.css('jhi-resizable-panels')).nativeElement;

        expect(ResizeObserverMock.instances.some((instance) => instance.observe.mock.calls.some(([target]) => target === observedElement))).toBe(true);
    });

    it('should observe the viewport for the responsive breakpoint when configured', () => {
        const document = TestBed.inject(DOCUMENT);

        createFixture(false, true);

        expect(ResizeObserverMock.instances.some((instance) => instance.observe.mock.calls.some(([target]) => target === document.documentElement))).toBe(true);
    });
});
