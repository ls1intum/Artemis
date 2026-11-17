import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TumAetUiSkeletonComponent, type TumAetUiSkeletonShape } from './tumaet-ui-skeleton.component';

@Component({
    imports: [TumAetUiSkeletonComponent],
    template: `
        <div aria-busy="true" aria-label="Loading results">
            <tumaet-ui-skeleton [shape]="shape()" style="width: 40px; height: 12px" />
        </div>
    `,
})
class HostComponent {
    readonly shape = signal<TumAetUiSkeletonShape>('rectangle');
}

describe('TumAetUiSkeletonComponent', () => {
    let fixture: ComponentFixture<HostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
    });

    function skeleton(): HTMLElement {
        return (fixture.nativeElement as HTMLElement).querySelector('tumaet-ui-skeleton')!;
    }

    it('is hidden from assistive technology and renders no content', () => {
        expect(skeleton().getAttribute('aria-hidden')).toBe('true');
        expect(skeleton().textContent).toBe('');
        expect(skeleton().childElementCount).toBe(0);
    });

    it('keeps the size the host gives it', () => {
        expect(skeleton().style.width).toBe('40px');
        expect(skeleton().style.height).toBe('12px');
    });

    it('distinguishes the circle shape from the default rectangle', () => {
        expect(skeleton().classList.contains('tumaet-ui-skeleton-circle')).toBe(false);

        fixture.componentInstance.shape.set('circle');
        fixture.detectChanges();

        expect(skeleton().classList.contains('tumaet-ui-skeleton-circle')).toBe(true);
    });
});
