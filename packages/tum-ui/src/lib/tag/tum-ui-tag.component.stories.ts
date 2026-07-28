import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiTagComponent, TumUiTagSeverity } from './tum-ui-tag.component';

const severities: TumUiTagSeverity[] = ['secondary', 'success', 'info', 'warn', 'danger', 'contrast'];
const examples: { severity: TumUiTagSeverity; value: string }[] = [
    { severity: 'secondary', value: 'Draft' },
    { severity: 'success', value: 'Published' },
    { severity: 'info', value: 'In review' },
    { severity: 'warn', value: 'Due soon' },
    { severity: 'danger', value: 'Overdue' },
    { severity: 'contrast', value: 'Archived' },
];

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
        props: { examples, rounded },
        template: `
            <div style="display: flex; flex-wrap: wrap; align-items: center; gap: 0.75rem;">
                @for (example of examples; track example.severity) {
                    <tum-ui-tag [severity]="example.severity" [rounded]="rounded" [value]="example.value" />
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
