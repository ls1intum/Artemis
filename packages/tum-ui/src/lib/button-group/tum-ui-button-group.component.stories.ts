import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiButtonComponent } from '../button/tum-ui-button.component';
import { TumUiButtonDirective } from '../button/tum-ui-button.directive';
import { TumUiMenuComponent } from '../menu/tum-ui-menu.component';
import { TumUiMenuItemDirective } from '../menu/tum-ui-menu-item.directive';
import { TumUiMenuTriggerDirective } from '../menu/tum-ui-menu-trigger.directive';
import { TumUiButtonGroupComponent } from './tum-ui-button-group.component';

interface ButtonGroupStoryArgs {
    firstLabel: string;
    secondLabel: string;
    thirdLabel: string;
}

const meta = {
    title: 'Actions/Button Group',
    component: TumUiButtonGroupComponent,
    subcomponents: {
        Button: TumUiButtonComponent,
    },
    decorators: [
        moduleMetadata({
            imports: [TumUiButtonComponent, TumUiButtonDirective, TumUiMenuComponent, TumUiMenuItemDirective, TumUiMenuTriggerDirective],
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
            <tum-ui-button-group aria-label="Date navigation">
                <tum-ui-button severity="secondary">{{ firstLabel }}</tum-ui-button>
                <tum-ui-button severity="secondary">{{ secondLabel }}</tum-ui-button>
                <tum-ui-button severity="secondary">{{ thirdLabel }}</tum-ui-button>
            </tum-ui-button-group>
        `,
    }),
} satisfies Meta<ButtonGroupStoryArgs>;

export default meta;

type Story = StoryObj<ButtonGroupStoryArgs>;

export const Default: Story = {};

/**
 * A split button: the primary action next to a menu of the secondary ones. The component form and the directive form
 * may be mixed inside one group, which is what lets the trigger carry `tumUiMenuTrigger` on a real `<button>`.
 */
export const SplitButton: Story = {
    render: () => ({
        template: `
            <tum-ui-button-group aria-label="Exercise actions">
                <tum-ui-button severity="primary" variant="outlined" size="small" disabledReason="Save your changes first">Adapt exercise</tum-ui-button>
                <button tumUiButton severity="primary" variant="outlined" size="small" [tumUiMenuTrigger]="more" aria-label="More actions">▾</button>
            </tum-ui-button-group>
            <ng-template #more>
                <tum-ui-menu>
                    <button tumUiMenuItem>Refine problem statement</button>
                    <button tumUiMenuItem>Check consistency</button>
                </tum-ui-menu>
            </ng-template>
        `,
    }),
};
