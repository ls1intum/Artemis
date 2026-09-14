import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TumUiListComponent } from './tum-ui-list.component';
import { TumUiListItemDirective } from './tum-ui-list-item.directive';
import { TumUiListItemActionDirective } from './tum-ui-list-item-action.directive';

@Component({
    imports: [TumUiListComponent, TumUiListItemDirective],
    template: `
        <tum-ui-list [ariaLabel]="ariaLabel()" [ariaLabelledBy]="ariaLabelledBy()">
            <li tumUiListItem [inline]="inline()">Full name</li>
            <li tumUiListItem>Login</li>
        </tum-ui-list>
    `,
})
class StaticHostComponent {
    readonly ariaLabel = signal<string | undefined>(undefined);
    readonly inline = signal(false);
    readonly ariaLabelledBy = signal<string | undefined>(undefined);
}

@Component({
    imports: [TumUiListComponent, TumUiListItemDirective, TumUiListItemActionDirective],
    template: `
        <tum-ui-list>
            <li tumUiListItem>
                <a tumUiListItemAction href="#account" [active]="accountActive()">Account information</a>
            </li>
            <li tumUiListItem>
                <button tumUiListItemAction (click)="picked.set('ssh')">SSH keys</button>
            </li>
        </tum-ui-list>
    `,
})
class NavigationHostComponent {
    readonly accountActive = signal(false);
    readonly picked = signal<string | undefined>(undefined);
}

describe('TumUiListComponent', () => {
    describe('as a static list', () => {
        let fixture: ComponentFixture<StaticHostComponent>;
        let host: StaticHostComponent;

        beforeEach(async () => {
            await TestBed.configureTestingModule({ imports: [StaticHostComponent] }).compileComponents();
            fixture = TestBed.createComponent(StaticHostComponent);
            host = fixture.componentInstance;
            fixture.detectChanges();
        });

        const list = () => fixture.debugElement.query(By.css('ul')).nativeElement as HTMLElement;
        const entries = () => fixture.debugElement.queryAll(By.css('li')).map((el) => el.nativeElement as HTMLElement);

        it('renders the entries inside a real list, so they keep list semantics', () => {
            expect(list().getAttribute('role')).toBe('list');
            expect(entries()).toHaveLength(2);
            expect(entries()[0].parentElement).toBe(list());
            expect(entries()[0].textContent!.trim()).toBe('Full name');
        });

        it('names the list only when an aria label is given', () => {
            expect(list().getAttribute('aria-label')).toBeNull();

            host.ariaLabel.set('User settings');
            fixture.detectChanges();

            expect(list().getAttribute('aria-label')).toBe('User settings');
        });

        it('lets a visible heading name the list instead of an aria label', () => {
            expect(list().getAttribute('aria-labelledby')).toBeNull();

            host.ariaLabelledBy.set('settings-heading');
            fixture.detectChanges();

            expect(list().getAttribute('aria-labelledby')).toBe('settings-heading');
        });

        it('pads a static entry itself, because it owns no interactive child', () => {
            expect(entries()[0].className).toContain('tum:px-4');
        });

        it('stacks a row by default and lays an inline row out on one line', () => {
            expect(entries()[0].className).toContain('tum:flex-col');
            expect(entries()[0].className).not.toContain('tum:flex-row');

            host.inline.set(true);
            fixture.detectChanges();

            // The direction is a package style: an application utility class cannot override it, because the
            // package stylesheet is unlayered and loads after the application's.
            expect(entries()[0].className).toContain('tum:flex-row');
            expect(entries()[0].className).not.toContain('tum:flex-col');
        });
    });

    describe('as a navigation list', () => {
        let fixture: ComponentFixture<NavigationHostComponent>;
        let host: NavigationHostComponent;

        beforeEach(async () => {
            await TestBed.configureTestingModule({ imports: [NavigationHostComponent] }).compileComponents();
            fixture = TestBed.createComponent(NavigationHostComponent);
            host = fixture.componentInstance;
            fixture.detectChanges();
        });

        const entries = () => fixture.debugElement.queryAll(By.css('li')).map((el) => el.nativeElement as HTMLElement);
        const link = () => fixture.debugElement.query(By.css('a')).nativeElement as HTMLAnchorElement;
        const button = () => fixture.debugElement.query(By.css('button')).nativeElement as HTMLButtonElement;

        it('keeps the action a real link, so it stays a link for assistive technology', () => {
            expect(link().getAttribute('role')).toBeNull();
            expect(link().getAttribute('href')).toBe('#account');
        });

        it('hands the row padding to the action, so the whole row is clickable', () => {
            expect(entries()[0].className).not.toContain('tum:px-4');
            expect(link().className).toContain('tum:px-4');
        });

        it('gives the active action a background the base does not cancel out', () => {
            expect(link().className).toContain('tum:bg-transparent');
            expect(link().className).not.toContain('tum:bg-highlight-background');

            host.accountActive.set(true);
            fixture.detectChanges();

            // Both are plain utilities, so a base `bg-transparent` would win and the active row would look
            // exactly like an inactive one.
            expect(link().className).toContain('tum:bg-highlight-background');
            expect(link().className).not.toContain('tum:bg-transparent');
        });

        it('marks the active action as the current page', () => {
            expect(link().getAttribute('aria-current')).toBeNull();

            host.accountActive.set(true);
            fixture.detectChanges();

            expect(link().getAttribute('aria-current')).toBe('page');
        });

        it('leaves a button action to the host click handler', () => {
            button().click();

            expect(host.picked()).toBe('ssh');
        });
    });
});
