import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { vi } from 'vitest';
import { TumUiDialogComponent } from './tum-ui-dialog.component';

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

    it('opens on visible=true: portals a modal dialog and emits onShow', () => {
        const showSpy = vi.spyOn(dialog.onShow, 'emit');
        host.open.set(true);
        fixture.detectChanges();

        expect(panel()).not.toBeNull();
        expect(container()?.getAttribute('role')).toBe('dialog');
        expect(container()?.getAttribute('aria-modal')).toBe('true');
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
