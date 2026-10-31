import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, RouterLink, provideRouter } from '@angular/router';
import { TumAetUiButtonDirective } from './tumaet-ui-button.directive';

@Component({
    selector: 'test-button-directive-host',
    imports: [TumAetUiButtonDirective, RouterLink],
    template: `
        <button tumAetUiButton [disabled]="disabled()" (click)="activated.set(activated() + 1)">Button</button>
        <a id="native" tumAetUiButton href="#target" tabindex="0" [disabled]="disabled()" (click)="activated.set(activated() + 1)">Link</a>
        <a id="router" tumAetUiButton routerLink="/target" [disabled]="disabled()">Router link</a>
        <a id="legacy" tumAetUiButton href="#legacy" aria-disabled="true" tabindex="0">Existing aria state</a>
    `,
})
class ButtonDirectiveHost {
    readonly disabled = signal<boolean | undefined>(false);
    readonly activated = signal(0);
}

describe('TumAetUiButtonDirective disabled state', () => {
    function setup() {
        TestBed.configureTestingModule({ imports: [ButtonDirectiveHost], providers: [provideRouter([])] });
        const fixture = TestBed.createComponent(ButtonDirectiveHost);
        const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
        fixture.detectChanges();
        return { fixture, host: fixture.componentInstance, navigate };
    }

    it.each([false, undefined])('disables native buttons and restores links when enabled again with %s', (enabledValue) => {
        const { fixture, host } = setup();
        const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
        const link: HTMLAnchorElement = fixture.nativeElement.querySelector('#native');
        host.disabled.set(true);
        fixture.detectChanges();
        expect(button.disabled).toBe(true);
        expect(link.getAttribute('aria-disabled')).toBe('true');
        expect(link.tabIndex).toBe(-1);
        expect(link.classList.contains('tumaet:opacity-60')).toBe(true);
        button.click();
        expect(host.activated()).toBe(0);
        host.disabled.set(enabledValue);
        fixture.detectChanges();
        expect(button.disabled).toBe(false);
        expect(link.hasAttribute('aria-disabled')).toBe(false);
        expect(link.tabIndex).toBe(0);
        expect(link.classList.contains('tumaet:opacity-60')).toBe(false);
        link.click();
        button.click();
        expect(host.activated()).toBe(2);
    });

    it.each(['click', 'auxclick', 'contextmenu'])('blocks disabled link activation through %s', (type) => {
        const { fixture, host, navigate } = setup();
        host.disabled.set(true);
        fixture.detectChanges();
        for (const id of ['native', 'router']) {
            const link: HTMLAnchorElement = fixture.nativeElement.querySelector('#' + id);
            const event = new MouseEvent(type, { bubbles: true, cancelable: true });
            link.dispatchEvent(event);
            expect(event.defaultPrevented).toBe(true);
        }
        expect(host.activated()).toBe(0);
        expect(navigate).not.toHaveBeenCalled();
    });

    it('allows enabled router navigation', () => {
        const { fixture, navigate } = setup();
        fixture.nativeElement.querySelector('#router').click();
        expect(navigate).toHaveBeenCalledOnce();
    });

    it('leaves existing aria-only state intact', () => {
        const { fixture } = setup();
        const legacy: HTMLAnchorElement = fixture.nativeElement.querySelector('#legacy');
        expect(legacy.getAttribute('aria-disabled')).toBe('true');
        expect(legacy.tabIndex).toBe(0);
    });
});
