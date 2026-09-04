import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TumUiSkeletonComponent } from './tum-ui-skeleton.component';

describe('TumUiSkeletonComponent', () => {
    let fixture: ComponentFixture<TumUiSkeletonComponent>;
    let host: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumUiSkeletonComponent] }).compileComponents();
        fixture = TestBed.createComponent(TumUiSkeletonComponent);
        host = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
    });

    function lines(): HTMLElement[] {
        return [...host.querySelectorAll<HTMLElement>('.tum-ui-skeleton-line')];
    }

    it('draws one line by default', () => {
        expect(lines()).toHaveLength(1);
        expect(host.getAttribute('data-slot')).toBe('skeleton');
        expect(host.getAttribute('data-lines')).toBe('1');
    });

    it('is hidden from assistive technology, because a placeholder is not a status', () => {
        // The announcement belongs on the container, which is the only thing that knows what is loading.
        expect(host.getAttribute('aria-hidden')).toBe('true');
        expect(host.getAttribute('role')).toBeNull();
        expect(host.getAttribute('aria-busy')).toBeNull();
    });

    it('stacks the requested number of lines', () => {
        fixture.componentRef.setInput('lines', 3);
        fixture.detectChanges();
        expect(lines()).toHaveLength(3);
        expect(host.getAttribute('data-lines')).toBe('3');
    });

    it('never renders fewer than one line, whatever it is given', () => {
        for (const value of [0, -4, Number.NaN, 1.5]) {
            fixture.componentRef.setInput('lines', value);
            fixture.detectChanges();
            expect(lines().length).toBeGreaterThanOrEqual(1);
        }
    });

    it('reserves the box the arriving content will occupy', () => {
        fixture.componentRef.setInput('width', '12rem');
        fixture.componentRef.setInput('height', '4rem');
        fixture.detectChanges();
        expect(host.style.width).toBe('12rem');
        expect(host.style.height).toBe('4rem');
    });

    it('has no shimmer to switch off', () => {
        // A shimmer is an infinite auto-starting animation running past five seconds alongside other content, and
        // it says nothing a still block does not. The stylesheet must therefore declare no animation at all.
        const stylesheet = [...document.querySelectorAll('style')].map((style) => style.textContent ?? '').find((text) => text.includes('.tum-ui-skeleton-line')) ?? '';
        expect(stylesheet).not.toBe('');
        expect(stylesheet).not.toContain('@keyframes');
        expect(stylesheet).not.toContain('animation');
    });
});
