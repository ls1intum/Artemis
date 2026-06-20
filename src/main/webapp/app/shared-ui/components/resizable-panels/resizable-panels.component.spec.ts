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

vi.mock('split.js', () => ({
    default: vi.fn(() => ({ destroy: vi.fn() })),
}));

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
        <jhi-resizable-panels [useViewportWidthForCollapse]="useViewportWidthForCollapse">
            <ng-template jhiPanel [label]="'left'" [icon]="faAlignLeft">Left Content</ng-template>
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
    });

    const createFixture = (irisStartsCollapsed = false, useViewportWidthForCollapse = false) => {
        fixture = TestBed.createComponent(ResizablePanelsTestComponent);
        fixture.componentInstance.irisStartsCollapsed = irisStartsCollapsed;
        fixture.componentInstance.useViewportWidthForCollapse = useViewportWidthForCollapse;
        fixture.detectChanges();
        return fixture.debugElement.query(By.directive(ResizablePanelsComponent)).componentInstance as ResizablePanelsComponent;
    };

    it('should render the right panel expanded by default', () => {
        const component = createFixture();

        expect(component.isRightPanelCollapsed()).toBe(false);
        expect(fixture.nativeElement.querySelector('.collapsed-right-panel')).toBeNull();
        expect(fixture.nativeElement.textContent).toContain('Right Content');
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
