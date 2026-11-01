import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ConfirmAutofocusButtonComponent } from './confirm-autofocus-button.component';

@Component({
    imports: [ConfirmAutofocusButtonComponent],
    template: `<jhi-confirm-button
        title="Open"
        confirmationTitle="Confirm"
        [confirmationText]="text()"
        [textIsMarkdown]="markdown()"
        [disabled]="disabled()"
        [isLoading]="loading()"
        (onConfirm)="confirmed()"
        (onCancel)="cancelled()"
        ><p>Projected explanation</p></jhi-confirm-button
    >`,
})
class ConfirmationHost {
    readonly text = signal('Keep unsaved edits?');
    readonly markdown = signal(false);
    readonly disabled = signal(false);
    readonly loading = signal(false);
    readonly confirmed = vi.fn();
    readonly cancelled = vi.fn();
}

describe('ConfirmAutofocusButtonComponent', () => {
    async function create() {
        TestBed.configureTestingModule({
            imports: [ConfirmationHost],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        const fixture = TestBed.createComponent(ConfirmationHost);
        fixture.detectChanges();
        await fixture.whenStable();
        const open = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
        return { fixture, open, host: fixture.componentInstance };
    }

    it('projects the explanation and emits confirmation exactly once', async () => {
        const { fixture, open, host } = await create();
        open.click();
        await fixture.whenStable();
        const dialog = document.querySelector<HTMLElement>('[role="dialog"]')!;
        expect(dialog.textContent).toContain('Keep unsaved edits?');
        expect(dialog.textContent).toContain('Projected explanation');
        const confirm = dialog.querySelector<HTMLButtonElement>('[cdkFocusInitial]')!;
        confirm.click();
        await fixture.whenStable();
        expect(host.confirmed).toHaveBeenCalledOnce();
        expect(host.cancelled).not.toHaveBeenCalled();
        expect(document.querySelector('[role="dialog"]')).toBeNull();
    });

    it('emits cancellation once when dismissed', async () => {
        const { fixture, open, host } = await create();
        open.click();
        await fixture.whenStable();
        const cancel = document.querySelector<HTMLButtonElement>('[role="dialog"] button:not([cdkFocusInitial])')!;
        cancel.click();
        await fixture.whenStable();
        expect(host.cancelled).toHaveBeenCalledOnce();
        expect(host.confirmed).not.toHaveBeenCalled();
    });

    it('renders markdown as safe HTML', async () => {
        const { fixture, open, host } = await create();
        host.markdown.set(true);
        host.text.set('**Important**');
        open.click();
        await fixture.whenStable();
        expect(document.querySelector('[role="dialog"] strong')?.textContent).toBe('Important');
        fixture.destroy();
    });

    it.each(['disabled', 'loading'] as const)('does not open while %s', async (state) => {
        const { fixture, open, host } = await create();
        host[state].set(true);
        await fixture.whenStable();
        expect(open.disabled).toBe(true);
        open.click();
        await fixture.whenStable();
        expect(document.querySelector('[role="dialog"]')).toBeNull();
    });
});
