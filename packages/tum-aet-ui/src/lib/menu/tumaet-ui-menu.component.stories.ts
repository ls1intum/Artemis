import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, userEvent, waitFor, within } from 'storybook/test';
import { TumAetUiButtonDirective } from '../button/tumaet-ui-button.directive';
import { TumAetUiMenuComponent } from './tumaet-ui-menu.component';
import { TumAetUiMenuGroupComponent } from './tumaet-ui-menu-group.component';
import { TumAetUiMenuItemDirective } from './tumaet-ui-menu-item.directive';
import { TumAetUiMenuSeparatorComponent } from './tumaet-ui-menu-separator.component';
import { TumAetUiMenuTriggerDirective } from './tumaet-ui-menu-trigger.directive';

const meta = {
    title: 'Navigation/Menu',
    component: TumAetUiMenuComponent,
    subcomponents: { TumAetUiMenuGroupComponent, TumAetUiMenuSeparatorComponent },
    decorators: [
        moduleMetadata({
            imports: [TumAetUiButtonDirective, TumAetUiMenuGroupComponent, TumAetUiMenuItemDirective, TumAetUiMenuSeparatorComponent, TumAetUiMenuTriggerDirective],
        }),
    ],
    render: () => ({
        template: `
            <button tumAetUiButton [tumAetUiMenuTrigger]="actions">Course actions</button>
            <ng-template #actions>
                <tumaet-ui-menu>
                    <button tumAetUiMenuItem>Add students</button>
                    <button tumAetUiMenuItem>Add tutors</button>
                    <button tumAetUiMenuItem [disabled]="true">Add editors</button>
                    <a tumAetUiMenuItem href="https://docs.artemis.cit.tum.de" target="_blank" rel="noreferrer">Open documentation</a>
                </tumaet-ui-menu>
            </ng-template>
        `,
    }),
} satisfies Meta<TumAetUiMenuComponent>;

export default meta;

type Story = StoryObj<TumAetUiMenuComponent>;

export const Default: Story = {};

/** Opening the menu moves focus onto the first entry, so it can be driven from the keyboard alone. */
export const Opened: Story = {
    play: async ({ canvasElement }) => {
        const canvas = within(canvasElement);
        await userEvent.click(canvas.getByRole('button', { name: 'Course actions' }));

        const menu = within(document.body).getByRole('menu');
        await expect(menu).toBeInTheDocument();
        await expect(within(menu).getByRole('menuitem', { name: 'Add students' })).toHaveFocus();
    },
};

/**
 * Separators divide a longer menu and a group puts related entries under a visible label. Neither is an entry: the arrow
 * keys move from "Settings" straight to "English".
 */
export const SeparatorsAndGroup: Story = {
    render: () => ({
        template: `
            <button tumAetUiButton [tumAetUiMenuTrigger]="account">Account</button>
            <ng-template #account>
                <tumaet-ui-menu>
                    <button tumAetUiMenuItem>Settings</button>
                    <tumaet-ui-menu-separator />
                    <tumaet-ui-menu-group label="Language">
                        <button tumAetUiMenuItem>English</button>
                        <button tumAetUiMenuItem>Deutsch</button>
                    </tumaet-ui-menu-group>
                    <tumaet-ui-menu-separator />
                    <button tumAetUiMenuItem>Sign out</button>
                </tumaet-ui-menu>
            </ng-template>
        `,
    }),
    play: async ({ canvasElement }) => {
        await userEvent.click(within(canvasElement).getByRole('button', { name: 'Account' }));

        const menu = within(document.body).getByRole('menu');
        await expect(within(menu).getAllByRole('separator')).toHaveLength(2);
        const group = within(menu).getByRole('group', { name: 'Language' });
        await waitFor(() => expect(within(menu).getByRole('menuitem', { name: 'Settings' })).toHaveFocus());
        await userEvent.keyboard('{ArrowDown}');
        await expect(within(group).getByRole('menuitem', { name: 'English' })).toHaveFocus();
    },
};

/**
 * The arrow keys move through every entry, including a disabled one, which is announced as unavailable, and Escape
 * returns focus to the trigger.
 */
export const KeyboardNavigation: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ canvasElement }) => {
        const trigger = within(canvasElement).getByRole('button', { name: 'Course actions' });
        trigger.focus();
        await userEvent.keyboard('{ArrowDown}');

        const menu = within(document.body).getByRole('menu');
        await waitFor(() => expect(within(menu).getByRole('menuitem', { name: 'Add students' })).toHaveFocus());
        await userEvent.keyboard('{ArrowDown}{ArrowDown}');
        const disabled = within(menu).getByRole('menuitem', { name: 'Add editors' });
        await expect(disabled).toHaveFocus();
        await expect(disabled).toHaveAttribute('aria-disabled', 'true');

        await userEvent.keyboard('{End}');
        await expect(within(menu).getByRole('menuitem', { name: 'Open documentation' })).toHaveFocus();

        await userEvent.keyboard('{Escape}');
        await waitFor(() => expect(within(document.body).queryByRole('menu')).not.toBeInTheDocument());
        await expect(trigger).toHaveFocus();
        await expect(trigger).toHaveAttribute('aria-expanded', 'false');
    },
};

/**
 * A menu followed by another control. A click outside closes the menu, and Tab closes it and moves on from the trigger
 * to the next control, as for a native select. Both need real pointer and key events, so
 * `.storybook/tests/menu-dismissal.spec.ts` checks them on this story in the browser.
 */
export const Dismissal: Story = {
    tags: ['!dev', '!autodocs'],
    render: () => ({
        template: `
            <div style="display: flex; gap: 0.5rem">
                <button tumAetUiButton [tumAetUiMenuTrigger]="actions">Course actions</button>
                <button tumAetUiButton variant="outlined">Next control</button>
            </div>
            <ng-template #actions>
                <tumaet-ui-menu>
                    <button tumAetUiMenuItem>Add students</button>
                    <button tumAetUiMenuItem>Add tutors</button>
                </tumaet-ui-menu>
            </ng-template>
        `,
    }),
};
