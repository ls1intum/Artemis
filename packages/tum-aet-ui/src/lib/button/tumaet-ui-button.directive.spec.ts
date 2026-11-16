import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TumAetUiButtonDirective } from './tumaet-ui-button.directive';

@Component({
    imports: [TumAetUiButtonDirective],
    template: `
        <button type="button" tumAetUiButton size="small" variant="text" [rounded]="rounded()" aria-label="Open session actions">⋮</button>
        <a href="#details" tumAetUiButton variant="text" [rounded]="rounded()" aria-label="Open exercise">→</a>
    `,
})
class RoundedHostComponent {
    readonly rounded = signal(false);
}

describe('TumAetUiButtonDirective', () => {
    let fixture: ComponentFixture<RoundedHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [RoundedHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(RoundedHostComponent);
        fixture.detectChanges();
    });

    function hosts(): HTMLElement[] {
        return Array.from(fixture.nativeElement.querySelectorAll('[tumAetUiButton]'));
    }

    it('keeps the default radius unless rounded is requested', () => {
        for (const host of hosts()) {
            expect(host.classList).toContain('tumaet-ui-btn');
            expect(host.classList).not.toContain('tumaet-ui-btn-rounded');
        }
    });

    it('adds the rounded class to native buttons and links without changing their names', () => {
        fixture.componentInstance.rounded.set(true);
        fixture.detectChanges();

        for (const host of hosts()) {
            expect(host.classList).toContain('tumaet-ui-btn');
            expect(host.classList).toContain('tumaet-ui-btn-rounded');
        }
        expect(hosts().map((host) => host.getAttribute('aria-label'))).toEqual(['Open session actions', 'Open exercise']);
        expect(hosts()[1].getAttribute('href')).toBe('#details');
    });
});
