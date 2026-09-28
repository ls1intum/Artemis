import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { Component, signal } from '@angular/core';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TumAetUiConfirmDialogComponent } from './tumaet-ui-confirm-dialog.component';
import { TumAetUiConfirmationService } from './tumaet-ui-confirmation.service';

@Component({
    template: `<tumaet-ui-confirm-dialog [key]="key()" />`,
    imports: [TumAetUiConfirmDialogComponent],
    providers: [TumAetUiConfirmationService],
})
class HostComponent {
    readonly key = signal<string | undefined>(undefined);
}

describe('TumAetUiConfirmDialogComponent', () => {
    let fixture: ComponentFixture<HostComponent>;
    let service: TumAetUiConfirmationService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent, FontAwesomeTestingModule] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        service = fixture.debugElement.injector.get(TumAetUiConfirmationService);
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        vi.restoreAllMocks();
    });

    function overlayText(): string {
        return document.querySelector('.tumaet-ui-dialog')?.textContent ?? '';
    }
    function button(label: string): HTMLButtonElement | undefined {
        return Array.from(document.querySelectorAll('.tumaet-ui-dialog .tumaet-ui-btn')).find((b) => b.textContent?.trim() === label) as HTMLButtonElement | undefined;
    }

    it('shows nothing until confirm() is called', () => {
        expect(document.querySelector('.tumaet-ui-dialog')).toBeNull();
    });

    it('renders the header, message and labelled buttons from the request', () => {
        service.confirm({ header: 'Delete?', message: 'Really delete this?', acceptLabel: 'Delete', rejectLabel: 'Cancel', accept: () => {} });
        fixture.detectChanges();
        expect(overlayText()).toContain('Really delete this?');
        expect(button('Delete')).toBeTruthy();
        expect(button('Cancel')).toBeTruthy();
        const dialog = document.querySelector('.cdk-dialog-container[role="alertdialog"]')!;
        const describedBy = dialog.getAttribute('aria-describedby');
        expect(describedBy).toBeTruthy();
        expect(document.getElementById(describedBy!)?.textContent).toContain('Really delete this?');
    });

    it('runs accept and closes when the confirm button is clicked', () => {
        const accept = vi.fn();
        const reject = vi.fn();
        service.confirm({ header: 'h', message: 'm', acceptLabel: 'Yes', rejectLabel: 'No', accept, reject });
        fixture.detectChanges();
        button('Yes')!.click();
        fixture.detectChanges();
        expect(accept).toHaveBeenCalledOnce();
        expect(reject).not.toHaveBeenCalled();
        expect(document.querySelector('.tumaet-ui-dialog')).toBeNull();
    });

    it('runs reject and closes when the cancel button is clicked', () => {
        const accept = vi.fn();
        const reject = vi.fn();
        service.confirm({ header: 'h', message: 'm', acceptLabel: 'Yes', rejectLabel: 'No', accept, reject });
        fixture.detectChanges();
        button('No')!.click();
        fixture.detectChanges();
        expect(reject).toHaveBeenCalledOnce();
        expect(accept).not.toHaveBeenCalled();
        expect(document.querySelector('.tumaet-ui-dialog')).toBeNull();
    });

    it('runs reject exactly once and closes when dismissed via the × button', () => {
        const accept = vi.fn();
        const reject = vi.fn();
        service.confirm({ header: 'h', message: 'm', acceptLabel: 'Yes', rejectLabel: 'No', accept, reject });
        fixture.detectChanges();
        (document.querySelector('.tumaet-ui-dialog-close') as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(reject).toHaveBeenCalledOnce();
        expect(accept).not.toHaveBeenCalled();
        expect(document.querySelector('.tumaet-ui-dialog')).toBeNull();
    });

    it('ignores a request whose key does not match the dialog key', () => {
        fixture.componentInstance.key.set('group-a');
        fixture.detectChanges();
        service.confirm({ header: 'h', message: 'other-key', acceptLabel: 'Yes', rejectLabel: 'No', accept: () => {}, key: 'group-b' });
        fixture.detectChanges();
        expect(document.querySelector('.tumaet-ui-dialog')).toBeNull();
    });

    it('renders a request whose key matches the dialog key', () => {
        fixture.componentInstance.key.set('group-a');
        fixture.detectChanges();
        service.confirm({ header: 'h', message: 'matching-key', acceptLabel: 'Yes', rejectLabel: 'No', accept: () => {}, key: 'group-a' });
        fixture.detectChanges();
        expect(overlayText()).toContain('matching-key');
    });
});

@Component({
    template: `<tumaet-ui-confirm-dialog key="a" /><tumaet-ui-confirm-dialog key="b" />`,
    imports: [TumAetUiConfirmDialogComponent],
    providers: [TumAetUiConfirmationService],
})
class MultiKeyHostComponent {}

describe('TumAetUiConfirmDialogComponent (independent keyed dialogs)', () => {
    let fixture: ComponentFixture<MultiKeyHostComponent>;
    let service: TumAetUiConfirmationService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [MultiKeyHostComponent, FontAwesomeTestingModule] }).compileComponents();
        fixture = TestBed.createComponent(MultiKeyHostComponent);
        service = fixture.debugElement.injector.get(TumAetUiConfirmationService);
        fixture.detectChanges();
    });

    afterEach(() => fixture.destroy());

    it('keeps keyed requests independent — opening one does not dismiss (or reject) another', () => {
        const rejectA = vi.fn();
        service.confirm({ header: 'A', message: 'request-a', acceptLabel: 'Y', rejectLabel: 'N', accept: () => {}, reject: rejectA, key: 'a' });
        fixture.detectChanges();
        service.confirm({ header: 'B', message: 'request-b', acceptLabel: 'Y', rejectLabel: 'N', accept: () => {}, key: 'b' });
        fixture.detectChanges();
        const open = Array.from(document.querySelectorAll('.tumaet-ui-dialog')).map((d) => d.textContent ?? '');
        expect(open.some((t) => t.includes('request-a'))).toBe(true);
        expect(open.some((t) => t.includes('request-b'))).toBe(true);
        expect(rejectA).not.toHaveBeenCalled();
    });
});
