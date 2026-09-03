import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AssessmentWorkspaceComponent } from 'app/assessment/manage/assessment-workspace/assessment-workspace.component';
import { provideTranslateService } from '@ngx-translate/core';

@Component({
    template: `
        <jhi-assessment-workspace storageKey="test-assessment-workspace" [showDetails]="showDetails()">
            <div assessmentWorkspaceCanvas data-testid="canvas">Canvas</div>
            <div assessmentWorkspaceInstructions data-testid="instructions">Instructions content</div>
            <div assessmentWorkspaceDetails data-testid="details">Feedback and notes</div>
        </jhi-assessment-workspace>
    `,
    imports: [AssessmentWorkspaceComponent],
})
class AssessmentWorkspaceTestHostComponent {
    readonly showDetails = signal(true);
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
        vi.unstubAllGlobals();
    });

    it('renders independent instructions and details panes beside the canvas', () => {
        const canvas = fixture.nativeElement.querySelector('[data-testid="canvas"]') as HTMLElement;
        const instructions = fixture.nativeElement.querySelector('[data-testid="instructions"]') as HTMLElement;
        const details = fixture.nativeElement.querySelector('[data-testid="details"]') as HTMLElement;

        expect(canvas.textContent).toContain('Canvas');
        expect(instructions.textContent).toContain('Instructions content');
        expect(details.textContent).toContain('Feedback and notes');
        expect(instructions.closest('.assessment-workspace__section')).not.toBe(details.closest('.assessment-workspace__section'));
    });

    it('stacks the canvas while keeping both support panes visible at narrow widths', () => {
        ResizeObserverMock.instance!.emit(700);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('.assessment-workspace__primary-split').classList.contains('assessment-workspace__split--vertical')).toBe(true);
        expect(fixture.nativeElement.querySelector('.assessment-workspace__support-split').classList.contains('assessment-workspace__split--horizontal')).toBe(true);

        ResizeObserverMock.instance!.emit(1000);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('.assessment-workspace__primary-split').classList.contains('assessment-workspace__split--vertical')).toBe(false);
        expect(fixture.nativeElement.querySelector('.assessment-workspace__support-split').classList.contains('assessment-workspace__split--horizontal')).toBe(false);
    });

    it('uses the full support pane for instructions when details are disabled', () => {
        fixture.componentInstance.showDetails.set(false);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="instructions"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="details"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('.assessment-workspace__support-split')).toBeNull();
    });
});
