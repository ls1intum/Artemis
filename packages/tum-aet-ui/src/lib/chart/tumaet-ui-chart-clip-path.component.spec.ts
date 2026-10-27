import { TestBed } from '@angular/core/testing';
import { TumAetUiChartClipPathComponent } from './tumaet-ui-chart-clip-path.component';

describe('TumAetUiChartClipPathComponent', () => {
    it('updates the clip dimensions independently of the plot offset', () => {
        const fixture = TestBed.createComponent(TumAetUiChartClipPathComponent);
        fixture.componentRef.setInput('clipId', 'chart-a');
        fixture.componentRef.setInput('plot', { left: 40, top: 20, width: 320, height: 160 });
        fixture.detectChanges();
        const clip = fixture.nativeElement.querySelector('clipPath') as SVGClipPathElement;
        const rect = clip.querySelector('rect')!;
        expect(clip.id).toBe('chart-a');
        expect(rect.getAttribute('width')).toBe('320');
        expect(rect.getAttribute('height')).toBe('160');
        expect(rect.hasAttribute('x')).toBe(false);
        expect(rect.hasAttribute('y')).toBe(false);
        fixture.componentRef.setInput('clipId', 'chart-b');
        fixture.componentRef.setInput('plot', { left: 10, top: 10, width: 0, height: 0 });
        fixture.detectChanges();
        expect(clip.id).toBe('chart-b');
        expect(rect.getAttribute('width')).toBe('0');
        expect(rect.getAttribute('height')).toBe('0');
    });
});
