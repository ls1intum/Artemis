import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AssessmentWorkspaceComponent } from 'app/assessment/manage/assessment-workspace/assessment-workspace.component';
import { By } from '@angular/platform-browser';
import { CollapsedPanelRailItem } from 'app/shared-ui/components/collapsed-panel-rail/collapsed-panel-rail.component';
import { faCommentDots, faLock } from '@fortawesome/free-solid-svg-icons';
import { provideTranslateService } from '@ngx-translate/core';

@Component({
    template: `
        <jhi-assessment-workspace
            storageKey="test-assessment-workspace"
            [showDetails]="showDetails()"
            [detailsRailItems]="detailsRailItems()"
            (detailsItemSelected)="selected.push($event)"
        >
            <div assessmentWorkspaceCanvas data-testid="canvas">Canvas</div>
            <div assessmentWorkspaceInstructions data-testid="instructions">Instructions content</div>
            <div assessmentWorkspaceDetails data-testid="details">Feedback and notes</div>
        </jhi-assessment-workspace>
    `,
    imports: [AssessmentWorkspaceComponent],
})
class AssessmentWorkspaceTestHostComponent {
    readonly showDetails = signal(true);
    readonly detailsRailItems = signal<CollapsedPanelRailItem[] | undefined>(undefined);
    readonly selected: string[] = [];
}

class ResizeObserverMock {
    static instance: ResizeObserverMock | undefined;
    readonly observe = vi.fn();
    readonly disconnect = vi.fn();

    constructor(private readonly callback: ResizeObserverCallback) {
        ResizeObserverMock.instance = this;
    }

    emit(width: number): void {
        this.callback([{ contentRect: { width } } as ResizeObserverEntry], this as unknown as ResizeObserver);
    }
}

describe('AssessmentWorkspaceComponent', () => {
    let fixture: ComponentFixture<AssessmentWorkspaceTestHostComponent>;

    const primary = () => fixture.debugElement.query(By.css('.assessment-workspace__primary-split'));
    const dragInstructions = (sizes: number[]) => primary().triggerEventHandler('onResizeEnd', { originalEvent: new MouseEvent('mouseup'), sizes });
    const instructionsRail = () => fixture.nativeElement.querySelector('[data-testid="collapsed-assessment-instructions"]') as HTMLButtonElement | null;
    const detailsPanel = () => fixture.nativeElement.querySelector('[data-testid="assessment-workspace-details"]') as HTMLElement | null;
    const detailsIcons = () => fixture.nativeElement.querySelectorAll('[data-testid^="feedback-bottom-panel-toggle"]') as NodeListOf<HTMLButtonElement>;

    beforeEach(() => {
        vi.stubGlobal('ResizeObserver', ResizeObserverMock);
        TestBed.configureTestingModule({ imports: [AssessmentWorkspaceTestHostComponent], providers: [provideTranslateService()] });
        fixture = TestBed.createComponent(AssessmentWorkspaceTestHostComponent);
        fixture.detectChanges();
    });

    afterEach(() => {
        const observer = ResizeObserverMock.instance;
        fixture.destroy();
        expect(observer?.disconnect).toHaveBeenCalledOnce();
        ResizeObserverMock.instance = undefined;
        localStorage.removeItem('test-assessment-workspace');
        vi.unstubAllGlobals();
    });

    it('puts the details panel under the canvas, and the instructions beside them', () => {
        const canvas = fixture.nativeElement.querySelector('[data-testid="canvas"]') as HTMLElement;
        const instructions = fixture.nativeElement.querySelector('[data-testid="instructions"]') as HTMLElement;
        const details = fixture.nativeElement.querySelector('[data-testid="details"]') as HTMLElement;
        const area = fixture.nativeElement.querySelector('.assessment-workspace__area') as HTMLElement;

        expect(canvas.textContent).toContain('Canvas');
        expect(instructions.textContent).toContain('Instructions content');
        expect(details.textContent).toContain('Feedback and notes');
        // The canvas and the details share one area; the instructions are outside it
        expect(area.contains(canvas)).toBe(true);
        expect(area.contains(details)).toBe(true);
        expect(area.contains(instructions)).toBe(false);
    });

    it('stacks the area over the instructions at narrow widths', () => {
        ResizeObserverMock.instance!.emit(700);
        fixture.detectChanges();
        expect(primary().nativeElement.classList.contains('assessment-workspace__split--vertical')).toBe(true);

        ResizeObserverMock.instance!.emit(1000);
        fixture.detectChanges();
        expect(primary().nativeElement.classList.contains('assessment-workspace__split--vertical')).toBe(false);
    });

    it('shows no details panel when details are disabled', () => {
        fixture.componentInstance.showDetails.set(false);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="instructions"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="details"]')).toBeNull();
        expect(detailsPanel()).toBeNull();
    });

    describe('collapsing the instructions', () => {
        it('collapses them to their rail when dragged to a sliver, and opens them again from the rail', () => {
            primary().triggerEventHandler('onResizeStart', { originalEvent: new MouseEvent('mousedown'), sizes: [70, 30] });
            dragInstructions([95, 5]);
            fixture.detectChanges();

            expect(instructionsRail()).not.toBeNull();
            expect(primary().nativeElement.classList).toContain('assessment-workspace__primary-split--collapsed');
            expect(JSON.parse(localStorage.getItem('test-assessment-workspace')!)).toEqual([70, 30]);

            instructionsRail()!.click();
            fixture.detectChanges();

            expect(instructionsRail()).toBeNull();
            expect(primary().componentInstance.panelSizes).toEqual([70, 30]);
        });

        it('keeps them while they are dragged to a usable size', () => {
            dragInstructions([75, 25]);
            fixture.detectChanges();

            expect(instructionsRail()).toBeNull();
            expect(primary().componentInstance.panelSizes).toEqual([75, 25]);
        });

        it('only limits them in height, and never collapses them, while they are stacked under the area', () => {
            ResizeObserverMock.instance!.emit(400);
            fixture.detectChanges();

            expect(primary().componentInstance.minSizes).toEqual([36, 24]);
            dragInstructions([95, 5]);
            fixture.detectChanges();

            expect(instructionsRail()).toBeNull();
        });
    });

    describe('the details panel', () => {
        beforeEach(() => {
            fixture.componentInstance.detailsRailItems.set([
                { id: 'feedback', icon: faCommentDots, labelKey: 'artemisApp.assessment.generalFeedback' },
                { id: 'notes', icon: faLock, labelKey: 'artemisApp.assessment.detail.notesTab' },
            ]);
            fixture.detectChanges();
        });

        it('collapses from its button to one icon for each tab, and opens at the tab that was clicked', () => {
            (fixture.nativeElement.querySelector('[data-testid="feedback-bottom-panel-toggle"]') as HTMLButtonElement).click();
            fixture.detectChanges();

            expect(detailsIcons()).toHaveLength(2);

            detailsIcons()[1].click();
            fixture.detectChanges();

            expect(fixture.componentInstance.selected).toEqual(['notes']);
            // Open again: the panel has its button once more, and no rail
            expect(detailsIcons()).toHaveLength(1);
            expect(fixture.nativeElement.querySelector('.feedback-bottom-panel--collapsed')).toBeNull();
        });

        it('collapses to one icon for each tab when dragged down to a sliver', () => {
            const splitter = fixture.debugElement.query(By.css('.assessment-workspace__area p-splitter'));
            splitter.triggerEventHandler('onResizeEnd', { originalEvent: new MouseEvent('mouseup'), sizes: [92, 8] });
            fixture.detectChanges();

            expect(detailsIcons()).toHaveLength(2);
        });

        it('keeps the panel open while it is dragged to a usable height', () => {
            const splitter = fixture.debugElement.query(By.css('.assessment-workspace__area p-splitter'));
            splitter.triggerEventHandler('onResizeEnd', { originalEvent: new MouseEvent('mouseup'), sizes: [70, 30] });
            fixture.detectChanges();

            expect(detailsIcons()).toHaveLength(1);
            expect(fixture.nativeElement.querySelector('.feedback-bottom-panel--collapsed')).toBeNull();
        });
    });
});
