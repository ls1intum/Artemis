import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiSearchFieldComponent } from './tum-ui-search-field.component';

const meta = {
    title: 'Forms/Search Field',
    component: TumUiSearchFieldComponent,
    args: {
        value: '',
        disabled: false,
    },
    argTypes: {
        size: {
            control: 'inline-radio',
            options: [undefined, 'small', 'large'],
        },
    },
    parameters: {
        layout: 'padded',
    },
} satisfies Meta<TumUiSearchFieldComponent>;

export default meta;

type Story = StoryObj<TumUiSearchFieldComponent>;

export const Default: Story = {};

export const WithTerm: Story = {
    args: {
        value: 'Tue-1-Mu-1',
    },
};

/** Matches the height of `size="small"` buttons, as in a page's title bar. */
export const Small: Story = {
    args: {
        size: 'small',
        value: 'Garching',
    },
};

export const Disabled: Story = {
    args: {
        value: 'Munich',
        disabled: true,
    },
};
