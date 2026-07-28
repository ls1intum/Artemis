import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiTagComponent, TumUiTagSeverity } from './tum-ui-tag.component';

const severities: TumUiTagSeverity[] = ['secondary', 'success', 'info', 'warn', 'danger', 'contrast'];

const meta = {
    title: 'Data Display/Tag',
    component: TumUiTagComponent,
    args: {
        value: 'Published',
        severity: 'secondary',
        rounded: false,
    },
    argTypes: {
        severity: {
            control: 'select',
            options: severities,
        },
    },
} satisfies Meta<TumUiTagComponent>;

export default meta;

type Story = StoryObj<TumUiTagComponent>;

export const Default: Story = {};

export const Severities: Story = {
    render: ({ rounded }) => ({
        props: { rounded, severities },
        template: `
            <div style="display: flex; flex-wrap: wrap; align-items: center; gap: 0.75rem;">
                @for (severity of severities; track severity) {
                    <tum-ui-tag [severity]="severity" [rounded]="rounded" [value]="severity" />
                }
            </div>
        `,
    }),
    parameters: {
        controls: {
            include: ['rounded'],
        },
    },
};

export const DarkTheme: Story = {
    ...Severities,
    globals: {
        theme: 'dark',
    },
};
