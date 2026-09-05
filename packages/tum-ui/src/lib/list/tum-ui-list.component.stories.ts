import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { TumUiListComponent } from './tum-ui-list.component';
import { TumUiListItemDirective } from './tum-ui-list-item.directive';
import { TumUiListItemActionDirective } from './tum-ui-list-item-action.directive';
import { TumUiToggleSwitchComponent } from '../toggle-switch/tum-ui-toggle-switch.component';

const meta = {
    title: 'Data Display/List',
    component: TumUiListComponent,
    decorators: [moduleMetadata({ imports: [TumUiListItemDirective, TumUiListItemActionDirective, TumUiToggleSwitchComponent] })],
    render: () => ({
        template: `
            <tum-ui-list ariaLabel="Account information" style="width: min(28rem, 100%);">
                <li tumUiListItem>
                    <span style="font-weight: 600;">Full name</span>
                    <span>Ada Lovelace</span>
                </li>
                <li tumUiListItem>
                    <span style="font-weight: 600;">Login</span>
                    <span>ab12cde</span>
                </li>
                <li tumUiListItem>
                    <span style="font-weight: 600;">Joined Artemis</span>
                    <span>17 May 2021</span>
                </li>
            </tum-ui-list>
        `,
    }),
} satisfies Meta<TumUiListComponent>;

export default meta;

type Story = StoryObj<TumUiListComponent>;

export const Default: Story = {};

/** A row whose action fills it: the whole row is the link, and the current one is marked `aria-current`. */
export const Navigation: Story = {
    render: () => ({
        template: `
            <tum-ui-list ariaLabel="User settings" style="width: min(20rem, 100%);">
                <li tumUiListItem><a tumUiListItemAction href="#account" [active]="true">Account information</a></li>
                <li tumUiListItem><a tumUiListItemAction href="#ssh">SSH keys</a></li>
                <li tumUiListItem><a tumUiListItemAction href="#passkeys">Passkeys</a></li>
            </tum-ui-list>
        `,
    }),
};

/** Rows compose with any control; the list only supplies the frame and the dividers. */
export const WithControls: Story = {
    render: () => ({
        template: `
            <tum-ui-list ariaLabel="Notification settings" style="width: min(28rem, 100%);">
                <li tumUiListItem inline>
                    <span>Exercise released</span>
                    <tum-ui-toggle-switch ariaLabel="Exercise released" />
                </li>
                <li tumUiListItem inline>
                    <span>New reply in a thread you follow</span>
                    <tum-ui-toggle-switch ariaLabel="New reply in a thread you follow" />
                </li>
            </tum-ui-list>
        `,
    }),
};
