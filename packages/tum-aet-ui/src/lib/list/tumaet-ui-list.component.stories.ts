import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { TumAetUiListComponent } from './tumaet-ui-list.component';
import { TumAetUiListItemDirective } from './tumaet-ui-list-item.directive';
import { TumAetUiListItemActionDirective } from './tumaet-ui-list-item-action.directive';
import { TumAetUiToggleSwitchComponent } from '../toggle-switch/tumaet-ui-toggle-switch.component';

const meta = {
    title: 'Data Display/List',
    component: TumAetUiListComponent,
    decorators: [moduleMetadata({ imports: [TumAetUiListItemDirective, TumAetUiListItemActionDirective, TumAetUiToggleSwitchComponent] })],
    render: () => ({
        template: `
            <tumaet-ui-list ariaLabel="Account information" style="width: min(28rem, 100%);">
                <li tumAetUiListItem>
                    <span style="font-weight: 600;">Full name</span>
                    <span>Ada Lovelace</span>
                </li>
                <li tumAetUiListItem>
                    <span style="font-weight: 600;">Login</span>
                    <span>ab12cde</span>
                </li>
                <li tumAetUiListItem>
                    <span style="font-weight: 600;">Joined Artemis</span>
                    <span>17 May 2021</span>
                </li>
            </tumaet-ui-list>
        `,
    }),
} satisfies Meta<TumAetUiListComponent>;

export default meta;

type Story = StoryObj<TumAetUiListComponent>;

export const Default: Story = {};

/** A row whose action fills it: the whole row is the link, and the current one is marked `aria-current`. */
export const Navigation: Story = {
    render: () => ({
        template: `
            <tumaet-ui-list ariaLabel="User settings" style="width: min(20rem, 100%);">
                <li tumAetUiListItem><a tumAetUiListItemAction href="#account" [active]="true">Account information</a></li>
                <li tumAetUiListItem><a tumAetUiListItemAction href="#ssh">SSH keys</a></li>
                <li tumAetUiListItem><a tumAetUiListItemAction href="#passkeys">Passkeys</a></li>
            </tumaet-ui-list>
        `,
    }),
};

/** Rows compose with any control; the list only supplies the frame and the dividers. */
export const WithControls: Story = {
    render: () => ({
        template: `
            <tumaet-ui-list ariaLabel="Notification settings" style="width: min(28rem, 100%);">
                <li tumAetUiListItem inline>
                    <span>Exercise released</span>
                    <tumaet-ui-toggle-switch ariaLabel="Exercise released" />
                </li>
                <li tumAetUiListItem inline>
                    <span>New reply in a thread you follow</span>
                    <tumaet-ui-toggle-switch ariaLabel="New reply in a thread you follow" />
                </li>
            </tumaet-ui-list>
        `,
    }),
};
