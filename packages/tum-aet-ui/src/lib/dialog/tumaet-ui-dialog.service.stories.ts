import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, screen, within } from 'storybook/test';
import { TumAetUiButtonDirective } from '../button/tumaet-ui-button.directive';
import { TumAetUiDialogService } from './tumaet-ui-dialog.service';
import { TumAetUiDialogRef } from './tumaet-ui-dialog.types';

@Component({
    selector: 'tumaet-ui-dialog-example-content',
    template: `<p>{{ description() }}</p>
        <div class="tumaet:mt-4 tumaet:flex tumaet:justify-end tumaet:gap-2">
            <button tumAetUiButton severity="secondary" (click)="ref.dismiss()">Cancel</button>
            <button tumAetUiButton (click)="ref.close('Confirmed')">Confirm</button>
        </div>`,
    imports: [TumAetUiButtonDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
class DialogExampleContentComponent {
    protected readonly ref = inject(TumAetUiDialogRef<string>);
    readonly description = input.required<string>();
}

@Component({
    selector: 'tumaet-ui-programmatic-dialog-example',
    template: `<button tumAetUiButton (click)="open()">Open dialog</button>
        <p role="status">{{ outcome() }}</p>`,
    imports: [TumAetUiButtonDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
class ProgrammaticDialogExampleComponent {
    private readonly dialogs = inject(TumAetUiDialogService);
    protected readonly outcome = signal('');

    protected open(): void {
        const ref = this.dialogs.open<DialogExampleContentComponent, string>(DialogExampleContentComponent, {
            header: 'Confirm selection',
            size: 'small',
            inputValues: { description: 'Use this selection for the next step?' },
        });
        ref.onClose.subscribe((result) => this.outcome.set(result ?? 'Cancelled'));
    }
}

const meta = {
    title: 'Overlays/Programmatic dialog',
    component: ProgrammaticDialogExampleComponent,
    parameters: { controls: { disable: true } },
} satisfies Meta<ProgrammaticDialogExampleComponent>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
    play: async ({ canvasElement, userEvent }) => {
        const canvas = within(canvasElement);
        const opener = canvas.getByRole('button', { name: 'Open dialog' });
        await userEvent.click(opener);
        const dialog = await screen.findByRole('dialog', { name: 'Confirm selection' });
        await expect(within(dialog).getByText('Use this selection for the next step?')).toBeVisible();
        await userEvent.click(within(dialog).getByRole('button', { name: /^Confirm$/ }));
        await expect(canvas.getByRole('status')).toHaveTextContent('Confirmed');
        await expect(opener).toHaveFocus();
    },
};
