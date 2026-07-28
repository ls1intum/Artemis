import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';
import { TumUiTableVirtualScrollComponent } from './tum-ui-table-virtual-scroll.component';

const items = Array.from({ length: 1_000 }, (_, index) => ({
    id: index + 1,
    name: `Participant ${index + 1}`,
    score: 100 - (index % 31),
}));

const meta = {
    title: 'Data Display/Virtual Scroll Table',
    component: TumUiTableVirtualScrollComponent<(typeof items)[number]>,
    args: {
        itemSize: 44,
        items,
        minWidth: '32rem',
        rowHover: true,
        scrollHeight: '264px',
        striped: true,
    },
    render: (args) => ({
        props: args,
        template: `
            <tum-ui-table-virtual-scroll
                [itemSize]="itemSize"
                [items]="items"
                [minWidth]="minWidth"
                [rowHover]="rowHover"
                [rowTemplate]="row"
                [scrollHeight]="scrollHeight"
                [striped]="striped"
            >
                <div role="columnheader" style="width: 16rem;">Participant</div>
                <div role="columnheader">Score</div>
            </tum-ui-table-virtual-scroll>

            <ng-template #row let-participant>
                <div role="cell" style="width: 16rem;">{{ participant.name }}</div>
                <div role="cell">{{ participant.score }}</div>
            </ng-template>
        `,
    }),
    parameters: {
        layout: 'padded',
    },
} satisfies Meta<TumUiTableVirtualScrollComponent<(typeof items)[number]>>;

export default meta;

type Story = StoryObj<TumUiTableVirtualScrollComponent<(typeof items)[number]>>;

export const Default: Story = {
    play: async ({ canvas }) => {
        await expect(canvas.getByRole('table')).toBeVisible();
        const renderedRows = canvas.getAllByRole('row');
        await expect(renderedRows.length).toBeGreaterThan(1);
        await expect(renderedRows.length).toBeLessThan(items.length);
    },
};
