import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumAetUiSkeletonComponent, type TumAetUiSkeletonShape } from './tumaet-ui-skeleton.component';

const shapes: TumAetUiSkeletonShape[] = ['rectangle', 'circle'];

const meta = {
    title: 'Feedback/Skeleton',
    component: TumAetUiSkeletonComponent,
    args: {
        shape: 'rectangle',
    },
    argTypes: {
        shape: {
            control: 'select',
            options: shapes,
        },
    },
    render: (args) => ({
        props: args,
        template: `<tumaet-ui-skeleton [shape]="shape" style="width: 12rem; height: 3rem" />`,
    }),
} satisfies Meta<TumAetUiSkeletonComponent>;

export default meta;

type Story = StoryObj<TumAetUiSkeletonComponent>;

export const Default: Story = {};

/** Several skeletons sketch the row that is loading; the container announces the busy state. */
export const ListRow: Story = {
    render: () => ({
        template: `
            <div role="status" aria-busy="true" aria-label="Loading results" style="display: flex; gap: 1rem; align-items: flex-start; width: 24rem">
                <tumaet-ui-skeleton shape="circle" style="width: 2.5rem; height: 2.5rem; flex-shrink: 0" />
                <div style="display: flex; flex-direction: column; gap: 0.5rem; flex-grow: 1">
                    <tumaet-ui-skeleton style="width: 45%; height: 1rem" />
                    <tumaet-ui-skeleton style="width: 65%; height: 0.75rem" />
                    <tumaet-ui-skeleton style="width: 80%; height: 0.75rem" />
                </div>
            </div>
        `,
    }),
};
