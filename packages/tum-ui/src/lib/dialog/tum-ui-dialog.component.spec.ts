import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { vi } from 'vitest';
import { TumUiDialogComponent, TumUiDialogSize } from './tum-ui-dialog.component';

@Component({
    selector: 'tum-ui-dialog-string-host',
    imports: [TumUiDialogComponent],
    template: `
        <tum-ui-dialog [(visible)]="open" [header]="header()" [closable]="closable()" [closeOnEscape]="closeOnEscape()" [dismissableMask]="dismissableMask()">
            <p class="body-content">Body text</p>
            @if (withFooter()) {
                <ng-template #footer>
                    <button class="footer-btn" type="button">OK</button>
                </ng-template>
            }
        </tum-ui-dialog>
    `,
})
class StringHeaderHostComponent {
    readonly open = signal(false);
    readonly header = signal<string | undefined>('My title');
    readonly closable = signal(true);
    readonly closeOnEscape = signal(true);
    readonly dismissableMask = signal(false);
    readonly withFooter = signal(true);
}

@Component({
    selector: 'tum-ui-dialog-template-host',
    imports: [TumUiDialogComponent],
    template: `
        <tum-ui-dialog [(visible)]="open">
            <ng-template #header>
                <h3 class="tpl-header">Template title</h3>
            </ng-template>
            <p class="body-content">Body</p>
        </tum-ui-dialog>
    `,
})
class TemplateHeaderHostComponent {
    readonly open = signal(false);
}

@Component({
    imports: [TumUiDialogComponent],
    template: `<tum-ui-dialog [visible]="true" header="Initially open">Body</tum-ui-dialog>`,
})
class InitiallyVisibleHostComponent {}

@Component({
    imports: [TumUiDialogComponent],
    template: `<tum-ui-dialog [visible]="true">Body</tum-ui-dialog>`,
})
class UnnamedHostComponent {}

@Component({
    imports: [TumUiDialogComponent],
    template: `<tum-ui-dialog [visible]="true" header="Sized" [size]="size()">Body</tum-ui-dialog>`,
})
class SizedHostComponent {
    readonly size = signal<TumUiDialogSize | undefined>(undefined);
}

function panel(): HTMLElement | null {
    return document.querySelector('.tum-ui-dialog');
}

function container(): HTMLElement | null {
    return document.querySelector('cdk-dialog-container');
}

function backdrop(): HTMLElement | null {
    return document.querySelector('.cdk-overlay-dark-backdrop');
}

describe('TumUiDialogComponent', () => {
    let fixture: ComponentFixture<StringHeaderHostComponent>;
    let host: StringHeaderHostComponent;
    let dialog: TumUiDialogComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [StringHeaderHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(StringHeaderHostComponent);
        host = fixture.componentInstance;
        dialog = fixture.debugElement.query(By.directive(TumUiDialogComponent)).componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        host.open.set(false);
        fixture.detectChanges();
        vi.restoreAllMocks();
    });

    it('renders nothing while not visible', () => {
        expect(panel()).toBeNull();
    });

    it('opens on visible=true, portals a modal dialog, and emits shown', () => {
        const showSpy = vi.spyOn(dialog.shown, 'emit');
        host.open.set(true);
        fixture.detectChanges();

        expect(panel()).not.toBeNull();
        expect(container()?.getAttribute('role')).toBe('dialog');
        expect(container()?.getAttribute('aria-modal')).toBe('true');
        expect(showSpy).toHaveBeenCalledTimes(1);
    });

    it('closes on visible=false, emits hidden, and keeps [(visible)] in sync', () => {
        host.open.set(true);
        fixture.detectChanges();
        const hideSpy = vi.spyOn(dialog.hidden, 'emit');

        host.open.set(false);
        fixture.detectChanges();

        expect(panel()).toBeNull();
        expect(hideSpy).toHaveBeenCalledTimes(1);
    });

    it('close() flips visible back to the parent (two-way binding)', () => {
        host.open.set(true);
        fixture.detectChanges();

        dialog.close();
        fixture.detectChanges();

        expect(host.open()).toBe(false);
        expect(panel()).toBeNull();
    });

    it('renders the string header and wires aria-labelledby to the title', () => {
        host.open.set(true);
        fixture.detectChanges();

        const title = document.querySelector('.tum-ui-dialog-title');
        expect(title?.textContent?.trim()).toBe('My title');
        expect(container()?.getAttribute('aria-labelledby')).toBe(title?.id);
        expect(title?.id).toBeTruthy();
    });

    it('projects the default content as the dialog body', () => {
        host.open.set(true);
        fixture.detectChanges();
        expect(document.querySelector('.tum-ui-dialog-content .body-content')?.textContent).toContain('Body text');
    });

    it('renders the projected #footer template in the footer region', () => {
        host.open.set(true);
        fixture.detectChanges();
        expect(document.querySelector('.tum-ui-dialog-footer .footer-btn')).not.toBeNull();
    });

    it('omits the footer region when no #footer template is projected', () => {
        host.withFooter.set(false);
        host.open.set(true);
        fixture.detectChanges();
        expect(document.querySelector('.tum-ui-dialog-footer')).toBeNull();
    });

    it('shows a working × close button when closable', () => {
        host.open.set(true);
        fixture.detectChanges();

        const closeBtn = document.querySelector<HTMLButtonElement>('cdk-dialog-container button[aria-label]');
        expect(closeBtn).not.toBeNull();
        closeBtn!.click();
        fixture.detectChanges();

        expect(host.open()).toBe(false);
        expect(panel()).toBeNull();
    });

    it('hides the × close button when closable=false', () => {
        host.closable.set(false);
        host.open.set(true);
        fixture.detectChanges();
        expect(document.querySelector('cdk-dialog-container button[aria-label]')).toBeNull();
    });

    it('uses the CDK modal backdrop', () => {
        host.open.set(true);
        fixture.detectChanges();
        expect(backdrop()).not.toBeNull();
    });

    it('closes on mask click only when dismissableMask is true', () => {
        host.open.set(true);
        fixture.detectChanges();
        backdrop()!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        fixture.detectChanges();
        expect(panel()).not.toBeNull();

        host.open.set(false);
        fixture.detectChanges();
        host.dismissableMask.set(true);
        host.open.set(true);
        fixture.detectChanges();
        backdrop()!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        fixture.detectChanges();
        expect(panel()).toBeNull();
    });

    it('closes on Escape only when closeOnEscape is true', () => {
        host.closeOnEscape.set(false);
        host.open.set(true);
        fixture.detectChanges();
        document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        fixture.detectChanges();
        expect(panel()).not.toBeNull();

        host.open.set(false);
        fixture.detectChanges();
        host.closeOnEscape.set(true);
        host.open.set(true);
        fixture.detectChanges();
        document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        fixture.detectChanges();
        expect(panel()).toBeNull();
    });
});

describe('TumUiDialogComponent projected #header template', () => {
    let fixture: ComponentFixture<TemplateHeaderHostComponent>;
    let host: TemplateHeaderHostComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TemplateHeaderHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(TemplateHeaderHostComponent);
        host = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        host.open.set(false);
        fixture.detectChanges();
    });

    it('renders the #header template and labels the dialog by it', () => {
        host.open.set(true);
        fixture.detectChanges();

        const tplHeader = document.querySelector('.tpl-header');
        expect(tplHeader?.textContent).toContain('Template title');

        const title = document.querySelector('.tum-ui-dialog-title');
        expect(container()?.getAttribute('aria-labelledby')).toBe(title?.id);
    });
});

describe('TumUiDialogComponent accessible name contract', () => {
    afterEach(() => {
        document.querySelectorAll('.cdk-overlay-container').forEach((element) => element.remove());
    });

    it('opens when initially visible and named', () => {
        TestBed.configureTestingModule({ imports: [InitiallyVisibleHostComponent] });
        const fixture = TestBed.createComponent(InitiallyVisibleHostComponent);
        fixture.detectChanges();

        expect(panel()).not.toBeNull();
        expect(container()?.getAttribute('aria-labelledby')).toBeTruthy();
        fixture.destroy();
    });

    it('rejects an unnamed dialog', () => {
        TestBed.configureTestingModule({ imports: [UnnamedHostComponent] });
        const fixture = TestBed.createComponent(UnnamedHostComponent);

        expect(() => fixture.detectChanges()).toThrow(/requires a visible header, a header template, or ariaLabel/);
        fixture.destroy();
    });
});

describe('TumUiDialogComponent size contract', () => {
    afterEach(() => {
        document.querySelectorAll('.cdk-overlay-container').forEach((element) => element.remove());
    });

    function renderWithSize(size: TumUiDialogSize | undefined): ComponentFixture<SizedHostComponent> {
        TestBed.configureTestingModule({ imports: [SizedHostComponent] });
        const fixture = TestBed.createComponent(SizedHostComponent);
        fixture.componentInstance.size.set(size);
        fixture.detectChanges();
        return fixture;
    }

    it.each([
        ['small', 'tum:w-[min(32rem,90dvw)]'],
        ['medium', 'tum:w-[min(48rem,90dvw)]'],
        ['large', 'tum:w-[min(72rem,90dvw)]'],
        ['full', 'tum:w-[90dvw]'],
    ] as const)('applies the %s width class to the overlay panel', (size, expected) => {
        const fixture = renderWithSize(size);

        expect(panel()?.className).toContain(expected);
        fixture.destroy();
    });

    it('leaves the panel content-sized when no size is given', () => {
        const fixture = renderWithSize(undefined);

        expect(panel()?.className).not.toMatch(/tum:w-\[/);
        fixture.destroy();
    });
});
