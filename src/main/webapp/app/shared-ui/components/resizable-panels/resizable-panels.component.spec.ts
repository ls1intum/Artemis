import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CdkScrollable, ScrollDispatcher } from '@angular/cdk/scrolling';
import { DOCUMENT } from '@angular/common';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiTabPanelComponent, TumAetUiTabPanelsComponent } from '@tumaet/ui-angular';
import { faAlignLeft, faComment } from '@fortawesome/free-solid-svg-icons';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PanelDirective, ResizablePanelsComponent } from 'app/shared-ui/components/resizable-panels/resizable-panels.component';

class ResizeObserverMock {
    static instances: ResizeObserverMock[] = [];

    constructor(private readonly callback: ResizeObserverCallback) {
        ResizeObserverMock.instances.push(this);
    }

    resize(width: number): void {
        this.callback([{ contentRect: { width } } as ResizeObserverEntry], this as unknown as ResizeObserver);
    }

    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
}

@Component({
    template: `
        <jhi-resizable-panels [useViewportWidthForCollapse]="useViewportWidthForCollapse" [storageKey]="storageKey" [flushLeftPanel]="flushLeftPanel">
            <ng-template jhiPanel [label]="'left'" [icon]="faAlignLeft"><div id="left-marker">Left Content</div></ng-template>
            <ng-template jhiPanel [label]="'right'">Right Content<input aria-label="Draft" value="Right Content" /></ng-template>
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
    flushLeftPanel = false;
    storageKey: string | undefined = undefined;
}

describe('ResizablePanelsComponent', () => {
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

    it('preserves unsaved right panel content through collapse and expansion', () => {
        const component = createFixture();
        const input = fixture.nativeElement.querySelector('input');
        input.value = 'Unsaved draft';
        component.collapseRightPanel();
        fixture.detectChanges();
        component.expandRightPanel();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('input')).toBe(input);
        expect(fixture.nativeElement.querySelector('input').value).toBe('Unsaved draft');
    });

    it('reopens with a visible split (slider not jammed to the edge) after a drag-to-collapse', () => {
        const component = createFixture();
        const splitter = fixture.nativeElement.querySelector('tumaet-ui-splitter');

        // Drag-to-collapse: the splitter leaves the panels at the near-zero position they were dragged to.
        component['onResizeEnd']([100, 0]);
        fixture.detectChanges();
        expect(component.isRightPanelCollapsed()).toBe(true);

        // Reopen via the icon rail: the right panel must come back with a usable width, not 0%.
        component.expandRightPanel(0);
        fixture.detectChanges();

        expect(component.isRightPanelCollapsed()).toBe(false);
        expect(splitter.querySelector('[role="separator"]').getAttribute('aria-valuenow')).toBe('65');
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
        expect(fixture.nativeElement.querySelector('tumaet-ui-splitter')).not.toBeNull();
        fixture.debugElement.queryAll(By.directive(TumAetUiTabPanelComponent)).forEach((panel) => {
            expect(panel.componentInstance.fill()).toBe(true);
        });
    });

    it('should remove only the primary panel gutter in full-bleed mode', () => {
        fixture = TestBed.createComponent(ResizablePanelsTestComponent);
        fixture.componentInstance.flushLeftPanel = true;
        fixture.detectChanges();

        const panelBodies = fixture.nativeElement.querySelectorAll('[data-testid="splitter-panel"] .overflow-y-auto');
        expect(panelBodies[0].classList.contains('p-2')).toBe(false);
        expect(panelBodies[1].style.padding).toBe('0.5rem');
    });

    it('removes the kit gutter from the first narrow flush panel and restores it for secondary tabs', async () => {
        fixture = TestBed.createComponent(ResizablePanelsTestComponent);
        fixture.componentInstance.flushLeftPanel = true;
        fixture.detectChanges();
        const host = fixture.debugElement.query(By.directive(ResizablePanelsComponent)).nativeElement;
        const observer = ResizeObserverMock.instances.find((instance) => instance.observe.mock.calls.some(([target]) => target === host))!;
        observer.resize(600);
        fixture.detectChanges();
        let container = fixture.debugElement.query(By.directive(TumAetUiTabPanelsComponent)).nativeElement as HTMLElement;
        expect(container.style.padding).toBe('0px');
        fixture.debugElement.queryAll(By.directive(TumAetUiTabPanelComponent)).forEach((panel) => {
            expect(panel.componentInstance.fill()).toBe(true);
        });

        const tabs = fixture.nativeElement.querySelectorAll('[data-testid="resizable-panel-tab"]') as NodeListOf<HTMLElement>;
        tabs[1].click();
        await fixture.whenStable();
        fixture.detectChanges();
        container = fixture.debugElement.query(By.directive(TumAetUiTabPanelsComponent)).nativeElement;
        expect(container.style.padding).toBe('0.5rem');
        tabs[0].click();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(container.style.padding).toBe('0px');
    });

    it('should keep the splitter mounted but show the icon rail when the right panel is collapsed', () => {
        const component = createFixture(true);

        expect(component.isRightPanelCollapsed()).toBe(true);
        // The splitter stays mounted (so the left panel is preserved) and switches to the icon rail via the class.
        const splitter = fixture.nativeElement.querySelector('tumaet-ui-splitter');
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

    it('moves keyboard focus from the collapse button to the rail and back to the reopened tab', async () => {
        createFixture();
        const collapse = fixture.nativeElement.querySelector('.right-panel-collapse-button');
        collapse.focus();
        collapse.click();
        await fixture.whenStable();
        fixture.detectChanges();
        const rail = fixture.nativeElement.querySelector('[data-testid="collapsed-panel-tab"]');
        expect(document.activeElement).toBe(rail);
        rail.click();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(document.activeElement).toBe(fixture.nativeElement.querySelector('[role="tab"][aria-selected="true"]'));
    });

    it('snaps the right panel closed when it is dragged below the collapse threshold', () => {
        const component = createFixture();
        const splitter = fixture.debugElement.query(By.css('tumaet-ui-splitter'));

        // Dragged to a 6% right panel (below the default 12% threshold) => snap closed.
        splitter.componentInstance.resizeEnd.emit([94, 6]);

        expect(component.isRightPanelCollapsed()).toBe(true);
        // The near-zero drag size is not remembered as the split; a sensible default is kept for reopening.
        expect(component.savedSizes()).toEqual([65, 35]);
    });

    it('keeps a usable split (in memory and in storage) when snapping closed after a previous resize', () => {
        fixture = TestBed.createComponent(ResizablePanelsTestComponent);
        fixture.componentInstance.storageKey = 'test-split';
        fixture.detectChanges();
        const component = fixture.debugElement.query(By.directive(ResizablePanelsComponent)).componentInstance as ResizablePanelsComponent;
        const splitter = fixture.debugElement.query(By.css('tumaet-ui-splitter'));

        // A usable resize is remembered as-is.
        splitter.componentInstance.resizeEnd.emit([70, 30]);
        expect(component.savedSizes()).toEqual([70, 30]);

        // Dragging below the threshold snaps shut. The near-zero size must not survive: neither in savedSizes (used
        // when reopening) nor in the splitter's localStorage entry (used when reloading); both keep the usable split.
        splitter.componentInstance.resizeEnd.emit([94, 6]);
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
        const splitter = fixture.nativeElement.querySelector('tumaet-ui-splitter');
        expect(splitter.querySelector('[role="separator"]').getAttribute('aria-valuenow')).toBe('72');
    });

    it('stores an independent copy of emitted sizes', () => {
        const component = createFixture();
        const splitter = fixture.debugElement.query(By.css('tumaet-ui-splitter'));

        const sizes = [40, 60];
        splitter.componentInstance.resizeEnd.emit(sizes);
        // A consumer retaining the event array cannot mutate the remembered split.
        sizes[0] = 99;
        sizes[1] = 1;

        expect(component.savedSizes()).toEqual([40, 60]);
    });

    it('should not render the splitter in narrow mode', () => {
        const component = createFixture();
        component['_isNarrow'].set(true);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('tumaet-ui-splitter')).toBeNull();
    });

    it('should default the split sizes and persist them from the splitter resize end event', () => {
        const component = createFixture();

        expect(component.savedSizes()).toBeUndefined();

        const splitter = fixture.debugElement.query(By.css('tumaet-ui-splitter'));
        splitter.componentInstance.resizeEnd.emit([40, 60]);

        expect(component.savedSizes()).toEqual([40, 60]);
    });

    it('persists keyboard resize output under the existing storage key', () => {
        fixture = TestBed.createComponent(ResizablePanelsTestComponent);
        fixture.componentInstance.storageKey = 'keyboard-split';
        fixture.detectChanges();
        const separator = fixture.nativeElement.querySelector('[role="separator"]');
        separator.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true, cancelable: true }));
        fixture.detectChanges();
        expect(localStorage.getItem('keyboard-split')).toBe('[67,33]');
    });

    it('should start collapsed without changing the active right panel', () => {
        const component = createFixture(true);

        expect(component.isRightPanelCollapsed()).toBe(true);
        expect(component.activeRightIndex()).toBe(0);
        expect(fixture.nativeElement.querySelector('.collapsed-right-panel')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('.panel-surface[hidden]').hasAttribute('inert')).toBe(true);
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

    // Regression coverage for #13187: CDK drag-and-drop only auto-scrolls containers registered with the ScrollDispatcher.
    // The panel content areas scroll inside plain overflow containers, so they must be registered as cdkScrollable.
    describe('drag-and-drop auto-scroll registration', () => {
        it('registers its scrollable panel content areas with the CDK ScrollDispatcher', () => {
            const scrollDispatcher = TestBed.inject(ScrollDispatcher);
            createFixture();

            const renderedScrollables = fixture.debugElement.queryAll(By.directive(CdkScrollable));
            expect(renderedScrollables.length).toBeGreaterThan(0);

            const registered = Array.from(scrollDispatcher.scrollContainers.keys());
            renderedScrollables.forEach((scrollable) => {
                expect(registered).toContain(scrollable.injector.get(CdkScrollable));
            });
        });

        it('wraps the projected panel content in a registered scroll container so a dragged item finds an ancestor to auto-scroll', () => {
            const scrollDispatcher = TestBed.inject(ScrollDispatcher);
            createFixture();

            const projectedContent = fixture.nativeElement.querySelector('#left-marker') as HTMLElement;
            expect(projectedContent).not.toBeNull();

            const registeredContainers = Array.from(scrollDispatcher.scrollContainers.keys()).map((scrollable) => scrollable.getElementRef().nativeElement as HTMLElement);
            const ancestorScrollContainer = registeredContainers.find((element) => element.contains(projectedContent));
            expect(ancestorScrollContainer).toBeTruthy();
        });
    });
});
