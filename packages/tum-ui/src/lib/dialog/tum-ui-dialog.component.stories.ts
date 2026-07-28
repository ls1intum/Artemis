import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn, screen, waitForElementToBeRemoved, within } from 'storybook/test';
import { TumUiButtonDirective } from '../button/tum-ui-button.directive';
import { TumUiDialogComponent } from './tum-ui-dialog.component';

interface DialogStoryArgs {
    ariaDescribedBy: string;
    cancelLabel: string;
    closable: boolean;
    closeOnEscape: boolean;
    confirmLabel: string;
    description: string;
    header: string;
    onHide: () => void;
    onShow: () => void;
    openLabel: string;
    visible: boolean;
}

const meta = {
    title: 'Overlays/Dialog',
    component: TumUiDialogComponent,
    args: {
        ariaDescribedBy: 'enrollment-dialog-description',
        cancelLabel: 'Cancel',
        closable: true,
        closeOnEscape: true,
        confirmLabel: 'Enroll',
        description: 'Enroll in Advanced Software Engineering?',
        header: 'Confirm enrollment',
        onHide: fn(),
        onShow: fn(),
        openLabel: 'Open confirmation',
        visible: false,
    },
    render: (args) => ({
        props: args,
        template: `
            <button tumUiButton (click)="visible = true">{{ openLabel }}</button>
            <tum-ui-dialog
                [(visible)]="visible"
                ${argsToTemplate(args, { exclude: ['cancelLabel', 'confirmLabel', 'description', 'openLabel', 'visible'] })}
            >
                <p [id]="ariaDescribedBy">{{ description }}</p>
                <ng-template #footer>
                    <button tumUiButton severity="secondary" variant="outlined" (click)="visible = false">{{ cancelLabel }}</button>
                    <button tumUiButton (click)="visible = false">{{ confirmLabel }}</button>
                </ng-template>
            </tum-ui-dialog>
        `,
        moduleMetadata: {
            imports: [TumUiButtonDirective],
        },
    }),
} satisfies Meta<DialogStoryArgs>;

export default meta;

type Story = StoryObj<DialogStoryArgs>;

export const Default: Story = {
    play: async ({ args, canvas, userEvent }) => {
        const launcher = canvas.getByRole('button', { name: 'Open confirmation' });
        await userEvent.click(launcher);

        const dialog = await screen.findByRole('dialog', { name: 'Confirm enrollment' });
        await expect(dialog).toHaveAccessibleDescription('Enroll in Advanced Software Engineering?');
        await expect(dialog).toHaveFocus();
        await expect(args.onShow).toHaveBeenCalledOnce();

        const dialogRemoved = waitForElementToBeRemoved(dialog);
        await userEvent.click(within(dialog).getByRole('button', { name: 'Close' }));
        await dialogRemoved;
        await expect(args.onHide).toHaveBeenCalledOnce();
        await expect(canvas.queryByText('Enroll in Advanced Software Engineering?')).not.toBeInTheDocument();
        await expect(launcher).toHaveFocus();
    },
};

export const RequiredDecision: Story = {
    args: {
        ariaDescribedBy: 'assessment-dialog-description',
        closable: false,
        closeOnEscape: false,
        confirmLabel: 'Submit assessment',
        description: 'Submit this assessment and release the result?',
        header: 'Submit assessment',
        openLabel: 'Review submission',
    },
    play: async ({ canvas, userEvent }) => {
        await userEvent.click(canvas.getByRole('button', { name: 'Review submission' }));

        const dialog = await screen.findByRole('dialog', { name: 'Submit assessment' });
        await userEvent.keyboard('{Escape}');
        await expect(dialog).toBeVisible();

        const dialogRemoved = waitForElementToBeRemoved(dialog);
        await userEvent.click(within(dialog).getByRole('button', { name: 'Submit assessment' }));
        await dialogRemoved;
    },
};
