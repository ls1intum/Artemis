import { argsToTemplate, moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { useArgs } from 'storybook/preview-api';
import { expect, fn, screen, waitForElementToBeRemoved, within } from 'storybook/test';
import { TumAetUiButtonDirective } from '../button/tumaet-ui-button.directive';
import { TumAetUiDialogComponent, type TumAetUiDialogSize } from './tumaet-ui-dialog.component';

interface DialogStoryArgs {
    ariaDescribedBy: string;
    cancelLabel: string;
    closable: boolean;
    closeOnEscape: boolean;
    confirmLabel: string;
    description: string;
    header: string;
    hidden: () => void;
    shown: () => void;
    openLabel: string;
    size: TumAetUiDialogSize;
    visible: boolean;
}

const meta = {
    title: 'Overlays/Dialog',
    component: TumAetUiDialogComponent,
    args: {
        ariaDescribedBy: 'enrollment-dialog-description',
        cancelLabel: 'Cancel',
        closable: true,
        closeOnEscape: true,
        confirmLabel: 'Enroll',
        description: 'Enroll in Advanced Software Engineering?',
        header: 'Confirm enrollment',
        hidden: fn(),
        shown: fn(),
        openLabel: 'Open confirmation',
        size: 'medium',
        visible: false,
    },
    decorators: [
        moduleMetadata({
            imports: [TumAetUiButtonDirective],
        }),
    ],
    render: function Render(args) {
        const [{ visible }, updateArgs] = useArgs<DialogStoryArgs>();
        return {
            props: {
                ...args,
                visible,
                setVisible(this: DialogStoryArgs, nextVisible: boolean) {
                    this.visible = nextVisible;
                    updateArgs({ visible: nextVisible });
                },
            },
            template: `
                <button tumAetUiButton (click)="setVisible(true)">{{ openLabel }}</button>
                <tumaet-ui-dialog
                    [visible]="visible"
                    ${argsToTemplate(args, { exclude: ['cancelLabel', 'confirmLabel', 'description', 'openLabel', 'visible'] })}
                    (visibleChange)="setVisible($event)"
                >
                    <p [id]="ariaDescribedBy">{{ description }}</p>
                    <ng-template #footer>
                        <button tumAetUiButton severity="secondary" variant="outlined" (click)="setVisible(false)">{{ cancelLabel }}</button>
                        <button tumAetUiButton (click)="setVisible(false)">{{ confirmLabel }}</button>
                    </ng-template>
                </tumaet-ui-dialog>
            `,
        };
    },
} satisfies Meta<DialogStoryArgs>;

export default meta;

type Story = StoryObj<DialogStoryArgs>;

export const Default: Story = {};

export const Open: Story = {
    args: {
        visible: true,
    },
    tags: ['!autodocs'],
    play: async ({ args }) => {
        const dialog = await screen.findByRole('dialog', { name: 'Confirm enrollment' });
        await expect(dialog).toHaveAccessibleDescription('Enroll in Advanced Software Engineering?');
        await expect(within(dialog).getByRole('button', { name: 'Close' })).toHaveFocus();
        await expect(args.shown).toHaveBeenCalledOnce();
    },
};

export const ClosesDialog: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ args, canvas, userEvent }) => {
        const launcher = canvas.getByRole('button', { name: 'Open confirmation' });
        await userEvent.click(launcher);

        const dialog = await screen.findByRole('dialog', { name: 'Confirm enrollment' });
        const dialogRemoved = waitForElementToBeRemoved(dialog);
        await userEvent.click(within(dialog).getByRole('button', { name: 'Close' }));
        await dialogRemoved;
        await expect(args.hidden).toHaveBeenCalledOnce();
        await expect(canvas.queryByText('Enroll in Advanced Software Engineering?')).not.toBeInTheDocument();
        await expect(launcher).toHaveFocus();
    },
};

export const RequiredDecision: Story = {
    tags: ['!autodocs'],
    args: {
        ariaDescribedBy: 'assessment-dialog-description',
        closable: false,
        closeOnEscape: false,
        confirmLabel: 'Submit assessment',
        description: 'Submit this assessment and release the result?',
        header: 'Submit assessment',
        openLabel: 'Review submission',
        visible: true,
    },
    play: async ({ userEvent }) => {
        const dialog = await screen.findByRole('dialog', { name: 'Submit assessment' });
        await userEvent.keyboard('{Escape}');
        await expect(dialog).toBeVisible();
    },
};
