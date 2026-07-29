import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { vi } from 'vitest';
import { TumUiDialogComponent } from 'app/shared-ui/tum-ui/dialog/tum-ui-dialog.component';

/** Host exercising the string-header path plus the projected body and `#footer` template. */
@Component({
    selector: 'tum-ui-dialog-string-host',
    imports: [TumUiDialogComponent],
    template: `
        <tum-ui-dialog
            [(visible)]="open"
            [header]="header()"
            [closable]="closable()"
            [closeOnEscape]="closeOnEscape()"
            [dismissableMask]="dismissableMask()"
            [style]="{ width: '50dvw' }"
        >
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

/** Host exercising the projected `#header` template path. */
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

function panel(): HTMLElement | null {
    return document.querySelector('.tum-ui-dialog');
}

function backdrop(): HTMLElement | null {
    return document.querySelector('.tum-ui-overlay-backdrop');
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

    it('opens on visible=true: portals a modal dialog and emits onShow', () => {
        const showSpy = vi.spyOn(dialog.onShow, 'emit');
        host.open.set(true);
        fixture.detectChanges();

        const el = panel();
        expect(el).not.toBeNull();
        expect(el?.getAttribute('role')).toBe('dialog');
        expect(el?.getAttribute('aria-modal')).toBe('true');
        expect(showSpy).toHaveBeenCalledTimes(1);
    });

    it('closes on visible=false: disposes overlay, emits onHide, keeps [(visible)] in sync', () => {
        host.open.set(true);
        fixture.detectChanges();
        const hideSpy = vi.spyOn(dialog.onHide, 'emit');

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
        expect(panel()?.getAttribute('aria-labelledby')).toBe(title?.id);
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

        const closeBtn = document.querySelector<HTMLButtonElement>('[data-testid="tum-ui-dialog-close"]');
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
        expect(document.querySelector('[data-testid="tum-ui-dialog-close"]')).toBeNull();
    });

    it('applies the [style] width record to the panel', () => {
        host.open.set(true);
        fixture.detectChanges();
        expect(panel()?.style.width).toBe('50dvw');
    });

    it('renders the kit backdrop with the Aura light modal mask color', () => {
        host.open.set(true);
        fixture.detectChanges();
        const mask = backdrop();
        expect(mask).not.toBeNull();
        expect(mask?.style.backgroundColor).toBe('rgba(0, 0, 0, 0.4)');
    });

    it('closes on mask click only when dismissableMask is true', () => {
        host.open.set(true);
        fixture.detectChanges();
        // Default dismissableMask=false: a mask click must NOT close.
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

    it('renders in the overlay container, not the top layer, so nested overlays can stack above it', () => {
        host.open.set(true);
        fixture.detectChanges();

        // A native popover (CDK 22's default) sits in the browser's top layer and paints above everything
        // regardless of z-index, burying any panel a dialog's content opens outside the CDK container.
        const overlayHost = panel()?.closest('.cdk-overlay-pane')?.parentElement;
        expect(overlayHost?.hasAttribute('popover')).toBe(false);
        expect(overlayHost?.classList.contains('cdk-overlay-popover')).toBe(false);
        expect(document.querySelector('.cdk-overlay-container')?.contains(panel()!)).toBe(true);
    });

    it('locks page scroll without offsetting the root, and releases it on close', () => {
        const root = document.documentElement;

        host.open.set(true);
        fixture.detectChanges();

        expect(root.style.overflow).toBe('hidden');
        // The CDK's block() strategy would set these; they are exactly what displaces a body-appended,
        // absolutely-positioned overlay panel (e.g. a PrimeNG appendTo="body" datepicker) while the dialog is open.
        expect(root.classList.contains('cdk-global-scrollblock')).toBe(false);
        expect(root.style.position).not.toBe('fixed');
        expect(root.style.top).toBe('');

        host.open.set(false);
        fixture.detectChanges();

        expect(root.style.overflow).toBe('');
    });

    it('keeps the page locked until the last of two nested dialogs closes', () => {
        const root = document.documentElement;
        const nested = TestBed.createComponent(StringHeaderHostComponent);
        nested.detectChanges();

        host.open.set(true);
        fixture.detectChanges();
        nested.componentInstance.open.set(true);
        nested.detectChanges();
        expect(root.style.overflow).toBe('hidden');

        // Closing the inner dialog must NOT restore scrolling while the outer one is still open.
        nested.componentInstance.open.set(false);
        nested.detectChanges();
        expect(root.style.overflow).toBe('hidden');

        host.open.set(false);
        fixture.detectChanges();
        expect(root.style.overflow).toBe('');
    });

    it('releases the scroll lock when destroyed while still open', () => {
        const root = document.documentElement;

        host.open.set(true);
        fixture.detectChanges();
        expect(root.style.overflow).toBe('hidden');

        fixture.destroy();

        expect(root.style.overflow).toBe('');
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
        expect(panel()?.getAttribute('aria-labelledby')).toBe(title?.id);
    });
});
