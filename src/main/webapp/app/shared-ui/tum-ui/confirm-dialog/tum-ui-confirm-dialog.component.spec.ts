import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { Component, signal } from '@angular/core';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TumUiConfirmDialogComponent } from 'app/shared-ui/tum-ui/confirm-dialog/tum-ui-confirm-dialog.component';
import { TumUiConfirmationService } from 'app/shared-ui/tum-ui/confirm-dialog/tum-ui-confirmation.service';

@Component({
    template: `<tum-ui-confirm-dialog [key]="key()" />`,
    imports: [TumUiConfirmDialogComponent],
    providers: [TumUiConfirmationService],
})
class HostComponent {
    readonly key = signal<string | undefined>(undefined);
}

describe('TumUiConfirmDialogComponent', () => {
    let fixture: ComponentFixture<HostComponent>;
    let service: TumUiConfirmationService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent, FontAwesomeTestingModule] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        service = fixture.debugElement.injector.get(TumUiConfirmationService);
        fixture.detectChanges();
    });

    afterEach(() => {
        // Dispose the component so its CDK overlay is removed from document.body and cannot leak into the next test.
        fixture.destroy();
        vi.restoreAllMocks();
    });

    // The dialog portals into the CDK overlay container (document.body), so query the document, not the fixture.
    function overlayText(): string {
        return document.querySelector('.tum-ui-dialog')?.textContent ?? '';
    }
    function button(label: string): HTMLButtonElement | undefined {
        return Array.from(document.querySelectorAll('.tum-ui-dialog .tum-ui-btn')).find((b) => b.textContent?.trim() === label) as HTMLButtonElement | undefined;
    }

    it('shows nothing until confirm() is called', () => {
        expect(document.querySelector('.tum-ui-dialog')).toBeNull();
    });

    it('renders the header, message and labelled buttons from the request', () => {
        service.confirm({ header: 'Delete?', message: 'Really delete this?', acceptLabel: 'Delete', rejectLabel: 'Cancel', accept: () => {} });
        fixture.detectChanges();
        expect(overlayText()).toContain('Really delete this?');
        expect(button('Delete')).toBeTruthy();
        expect(button('Cancel')).toBeTruthy();
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
        expect(document.querySelector('.tum-ui-dialog')).toBeNull();
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
        expect(document.querySelector('.tum-ui-dialog')).toBeNull();
    });

    it('runs reject exactly once and closes when dismissed via the × button', () => {
        const accept = vi.fn();
        const reject = vi.fn();
        service.confirm({ header: 'h', message: 'm', acceptLabel: 'Yes', rejectLabel: 'No', accept, reject });
        fixture.detectChanges();
        // The × triggers the dialog's own close → (onHide) → the confirm-dialog's onDialogHide → reject.
        (document.querySelector('.tum-ui-dialog [data-testid="tum-ui-dialog-close"]') as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(reject).toHaveBeenCalledOnce();
        expect(accept).not.toHaveBeenCalled();
        expect(document.querySelector('.tum-ui-dialog')).toBeNull();
    });

    it('ignores a request whose key does not match the dialog key', () => {
        fixture.componentInstance.key.set('group-a');
        fixture.detectChanges();
        service.confirm({ header: 'h', message: 'other-key', acceptLabel: 'Yes', rejectLabel: 'No', accept: () => {}, key: 'group-b' });
        fixture.detectChanges();
        expect(document.querySelector('.tum-ui-dialog')).toBeNull();
    });

    it('renders a request whose key matches the dialog key', () => {
        fixture.componentInstance.key.set('group-a');
        fixture.detectChanges();
        service.confirm({ header: 'h', message: 'matching-key', acceptLabel: 'Yes', rejectLabel: 'No', accept: () => {}, key: 'group-a' });
        fixture.detectChanges();
        expect(overlayText()).toContain('matching-key');
    });
});
