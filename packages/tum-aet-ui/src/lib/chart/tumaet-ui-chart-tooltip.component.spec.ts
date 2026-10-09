import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TumAetUiChartTooltipComponent } from './tumaet-ui-chart-tooltip.component';

describe('TumAetUiChartTooltipComponent', () => {
    let fixture: ComponentFixture<TumAetUiChartTooltipComponent>;
    let host: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumAetUiChartTooltipComponent] }).compileComponents();
        fixture = TestBed.createComponent(TumAetUiChartTooltipComponent);
        host = fixture.nativeElement as HTMLElement;
        // The host has to be in the document for the component's own styles to apply.
        document.body.appendChild(host);
        fixture.detectChanges();
    });

    afterEach(() => {
        host.remove();
    });

    it.each([false, true])('never captures the pointer, whichever side it renders on (below=%s)', (below) => {
        fixture.componentRef.setInput('below', below);
        fixture.detectChanges();

        // A tooltip that accepts pointer events steals the hover from the datum underneath it, so the datum
        // fires mouseleave, the tooltip hides, the datum is hovered again, and the tooltip flickers.
        expect(getComputedStyle(host).pointerEvents).toBe('none');
    });

    it.each([false, true])('caps its width so a long tooltip cannot run past the chart (below=%s)', (below) => {
        fixture.componentRef.setInput('below', below);
        fixture.detectChanges();

        expect(getComputedStyle(host).maxWidth).not.toBe('none');
    });

    it('renders the title and one line per entry', () => {
        fixture.componentRef.setInput('title', 'Quiz 1');
        fixture.componentRef.setInput('lines', ['Your score: 50%', 'Average score: 40%']);
        fixture.detectChanges();

        expect(host.querySelector('.tumaet-ui-chart-tooltip-title')?.textContent?.trim()).toBe('Quiz 1');
        expect(host.textContent).toContain('Your score: 50%');
        expect(host.textContent).toContain('Average score: 40%');
    });
});
