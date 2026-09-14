import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, userEvent, within } from 'storybook/test';
import { TumUiButtonDirective } from '../button/tum-ui-button.directive';
import { TumUiMenuComponent } from './tum-ui-menu.component';
import { TumUiMenuItemDirective } from './tum-ui-menu-item.directive';
import { TumUiMenuTriggerDirective } from './tum-ui-menu-trigger.directive';

const meta = {
    title: 'Navigation/Menu',
    component: TumUiMenuComponent,
    decorators: [moduleMetadata({ imports: [TumUiButtonDirective, TumUiMenuItemDirective, TumUiMenuTriggerDirective] })],
    render: () => ({
        template: `
            <button tumUiButton [tumUiMenuTrigger]="actions">Course actions</button>
            <ng-template #actions>
                <tum-ui-menu>
                    <button tumUiMenuItem>Add students</button>
                    <button tumUiMenuItem>Add tutors</button>
                    <button tumUiMenuItem disabled>Add editors</button>
                    <a tumUiMenuItem href="https://docs.artemis.cit.tum.de" target="_blank" rel="noreferrer">Open documentation</a>
                </tum-ui-menu>
            </ng-template>
        `,
    }),
} satisfies Meta<TumUiMenuComponent>;

export default meta;

type Story = StoryObj<TumUiMenuComponent>;

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
