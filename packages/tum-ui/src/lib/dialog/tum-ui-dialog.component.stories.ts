import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn, userEvent, waitFor, within } from 'storybook/test';
import { TumUiButtonDirective } from '../button/tum-ui-button.directive';
import { TumUiDialogComponent } from './tum-ui-dialog.component';

const meta = {
    title: 'Overlays/Dialog',
    component: TumUiDialogComponent,
    args: {
        ariaDescribedBy: 'enrollment-dialog-description',
        header: 'Confirm enrollment',
        onHide: fn(),
        onShow: fn(),
        visible: true,
    },
    render: (args) => ({
        props: args,
        template: `
            <tum-ui-dialog ${argsToTemplate(args)}>
                <p id="enrollment-dialog-description">Enroll in Advanced Software Engineering?</p>
                <ng-template #footer>
                    <button tumUiButton severity="secondary" variant="outlined">Cancel</button>
                    <button tumUiButton>Enroll</button>
                </ng-template>
            </tum-ui-dialog>
        `,
        moduleMetadata: {
            imports: [TumUiButtonDirective],
        },
    }),
} satisfies Meta<TumUiDialogComponent>;

export default meta;

type Story = StoryObj<TumUiDialogComponent>;

export const Default: Story = {
    play: async ({ canvas }) => {
        const dialog = await within(document.body).findByRole('dialog', { name: 'Confirm enrollment' });
        await expect(dialog).toHaveAccessibleDescription('Enroll in Advanced Software Engineering?');
        await expect(dialog).toHaveFocus();

        await userEvent.click(within(dialog).getByRole('button', { name: 'Close' }));
        await waitFor(() => expect(dialog).not.toBeInTheDocument());
        await expect(canvas.queryByText('Enroll in Advanced Software Engineering?')).not.toBeInTheDocument();
    },
};

export const RequiredDecision: Story = {
    args: {
        closable: false,
        closeOnEscape: false,
        header: 'Submit assessment',
    },
};
