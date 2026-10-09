import { Component, inject, input } from '@angular/core';
import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, screen, waitForElementToBeRemoved, within } from 'storybook/test';
import { TumAetUiButtonDirective } from '../button/tumaet-ui-button.directive';
import { TumAetUiConfirmDialogComponent } from './tumaet-ui-confirm-dialog.component';
import { TumAetUiConfirmationService } from './tumaet-ui-confirmation.service';

@Component({
    selector: 'tumaet-ui-confirm-dialog-story',
    imports: [TumAetUiButtonDirective, TumAetUiConfirmDialogComponent],
    providers: [TumAetUiConfirmationService],
    template: `
        <button tumAetUiButton (click)="open()">Delete tutorial group</button>
        <tumaet-ui-confirm-dialog [key]="key()" />
    `,
})
class ConfirmDialogStoryComponent {
    private readonly confirmations = inject(TumAetUiConfirmationService);

    readonly key = input<string>();

    protected open(): void {
        this.confirmations.confirm({
            key: this.key(),
            header: 'Delete tutorial group?',
            message: 'This action cannot be undone.',
            acceptLabel: 'Delete',
            rejectLabel: 'Cancel',
            acceptSeverity: 'danger',
            accept: () => {},
        });
    }
}

const meta = {
    title: 'Overlays/Confirm Dialog',
    component: TumAetUiConfirmDialogComponent,
    decorators: [
        moduleMetadata({
            imports: [ConfirmDialogStoryComponent],
        }),
    ],
    render: (args) => ({
        props: args,
        template: '<tumaet-ui-confirm-dialog-story [key]="key" />',
    }),
} satisfies Meta<TumAetUiConfirmDialogComponent>;

export default meta;

type Story = StoryObj<TumAetUiConfirmDialogComponent>;

export const Default: Story = {};

export const AcceptsDecision: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ canvas, userEvent }) => {
        await userEvent.click(canvas.getByRole('button', { name: 'Delete tutorial group' }));
        const dialog = await screen.findByRole('alertdialog', { name: 'Delete tutorial group?' });
        await expect(dialog).toHaveAccessibleDescription('This action cannot be undone.');
        const removed = waitForElementToBeRemoved(dialog);
        await userEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));
        await removed;
    },
};
