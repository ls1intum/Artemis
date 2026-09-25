import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumAetUiButtonDirective } from '../button/tumaet-ui-button.directive';
import { TumAetUiMenuComponent } from '../menu/tumaet-ui-menu.component';
import { TumAetUiMenuItemDirective } from '../menu/tumaet-ui-menu-item.directive';
import { TumAetUiMenuTriggerDirective } from '../menu/tumaet-ui-menu-trigger.directive';
import { TumAetUiButtonComponent } from '../button/tumaet-ui-button.component';
import { TumAetUiButtonGroupComponent } from './tumaet-ui-button-group.component';

interface ButtonGroupStoryArgs {
    firstLabel: string;
    secondLabel: string;
    thirdLabel: string;
}

const meta = {
    title: 'Actions/Button Group',
    component: TumAetUiButtonGroupComponent,
    subcomponents: {
        Button: TumAetUiButtonComponent,
    },
    decorators: [
        moduleMetadata({
            imports: [TumAetUiButtonComponent, TumAetUiButtonDirective, TumAetUiMenuComponent, TumAetUiMenuItemDirective, TumAetUiMenuTriggerDirective],
        }),
    ],
    args: {
        firstLabel: 'Previous',
        secondLabel: 'Today',
        thirdLabel: 'Next',
    },
    render: (args) => ({
        props: args,
        template: `
            <tumaet-ui-button-group aria-label="Date navigation">
                <tumaet-ui-button severity="secondary">{{ firstLabel }}</tumaet-ui-button>
                <tumaet-ui-button severity="secondary">{{ secondLabel }}</tumaet-ui-button>
                <tumaet-ui-button severity="secondary">{{ thirdLabel }}</tumaet-ui-button>
            </tumaet-ui-button-group>
        `,
    }),
} satisfies Meta<ButtonGroupStoryArgs>;

export default meta;

type Story = StoryObj<ButtonGroupStoryArgs>;

export const Default: Story = {};

/**
 * A split button: the primary action next to a menu of the secondary ones. The component form and the directive form
 * may be mixed inside one group, which is what lets the trigger carry `tumAetUiMenuTrigger` on a real `<button>`.
 */
export const SplitButton: Story = {
    render: () => ({
        template: `
            <tumaet-ui-button-group aria-label="Exercise actions">
                <tumaet-ui-button severity="primary" variant="outlined" size="small" disabledReason="Save your changes first">Adapt exercise</tumaet-ui-button>
                <button tumAetUiButton severity="primary" variant="outlined" size="small" [tumAetUiMenuTrigger]="more" aria-label="More actions">▾</button>
            </tumaet-ui-button-group>
            <ng-template #more>
                <tumaet-ui-menu>
                    <button tumAetUiMenuItem>Refine problem statement</button>
                    <button tumAetUiMenuItem>Check consistency</button>
                </tumaet-ui-menu>
            </ng-template>
        `,
    }),
};
