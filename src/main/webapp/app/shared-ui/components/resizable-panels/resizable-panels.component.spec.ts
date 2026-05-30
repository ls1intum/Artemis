import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
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
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
}

@Component({
    template: `
        <jhi-resizable-panels>
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
}

describe('ResizablePanelsComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ResizablePanelsTestComponent>;

    beforeEach(async () => {
        vi.stubGlobal('ResizeObserver', ResizeObserverMock);
        await TestBed.configureTestingModule({
            imports: [ResizablePanelsTestComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideNoopAnimations()],
        }).compileComponents();
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    const createFixture = (irisStartsCollapsed = false) => {
        fixture = TestBed.createComponent(ResizablePanelsTestComponent);
        fixture.componentInstance.irisStartsCollapsed = irisStartsCollapsed;
        fixture.detectChanges();
        return fixture.debugElement.query(By.directive(ResizablePanelsComponent)).componentInstance as ResizablePanelsComponent;
    };

    it('should render the right panel expanded by default', () => {
        const component = createFixture();

        expect(component.isRightPanelCollapsed()).toBe(false);
        expect(fixture.nativeElement.querySelector('.collapsed-right-panel')).toBeNull();
        expect(fixture.nativeElement.textContent).toContain('Right Content');
    });

    it('should start collapsed with the marked right panel active', () => {
        const component = createFixture(true);

        expect(component.isRightPanelCollapsed()).toBe(true);
        expect(component.activeRightIndex()).toBe(1);
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
});
