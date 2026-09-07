import { Component, inject, input } from '@angular/core';
import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, screen, waitForElementToBeRemoved, within } from 'storybook/test';
import { TumUiButtonDirective } from '../button/tum-ui-button.directive';
import { TumUiConfirmDialogComponent } from './tum-ui-confirm-dialog.component';
import { TumUiConfirmationService } from './tum-ui-confirmation.service';

@Component({
    selector: 'tum-ui-confirm-dialog-story',
    imports: [TumUiButtonDirective, TumUiConfirmDialogComponent],
    providers: [TumUiConfirmationService],
    template: `
        <button tumUiButton (click)="open()">Delete tutorial group</button>
        <tum-ui-confirm-dialog [key]="key()" />
    `,
})
class ConfirmDialogStoryComponent {
    readonly key = input<string>();
    private readonly confirmations = inject(TumUiConfirmationService);

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
    component: TumUiConfirmDialogComponent,
    decorators: [
        moduleMetadata({
            imports: [ConfirmDialogStoryComponent],
        }),
    ],
    render: (args) => ({
        props: args,
        template: '<tum-ui-confirm-dialog-story [key]="key" />',
    }),
} satisfies Meta<TumUiConfirmDialogComponent>;

export default meta;

type Story = StoryObj<TumUiConfirmDialogComponent>;

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
