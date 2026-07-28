import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn, userEvent } from 'storybook/test';
import { TumUiTableComponent } from './tum-ui-table.component';
import { ColumnDef } from './tum-ui-table.types';

interface Participant {
    id: number;
    name: string;
    score: number;
    status: string;
}

const columns: ColumnDef<Participant>[] = [
    { field: 'name', header: 'Participant', sort: true, width: '14rem' },
    { field: 'score', header: 'Score', sort: true },
    { field: 'status', header: 'Status', hideBelow: 'sm' },
];

const rows: Participant[] = [
    { id: 1, name: 'Ada Lovelace', score: 98, status: 'Submitted' },
    { id: 2, name: 'Grace Hopper', score: 94, status: 'Submitted' },
    { id: 3, name: 'Margaret Hamilton', score: 91, status: 'In review' },
];

const meta = {
    title: 'Data Display/Table',
    component: TumUiTableComponent<Participant>,
    args: {
        columns,
        dataRequest: fn(),
        pageSize: 20,
        rows,
        striped: true,
        totalRecords: 128,
    },
    parameters: {
        layout: 'padded',
    },
} satisfies Meta<TumUiTableComponent<Participant>>;

export default meta;

type Story = StoryObj<TumUiTableComponent<Participant>>;

export const Default: Story = {
    play: async ({ args, canvas }) => {
        await expect(canvas.getAllByRole('row')).toHaveLength(4);

        const participantHeader = canvas.getByRole('columnheader', { name: /Participant/ });
        await expect(participantHeader).toHaveAttribute('aria-sort', 'none');
        await userEvent.click(canvas.getByRole('button', { name: /Participant/ }));

        await expect(participantHeader).toHaveAttribute('aria-sort', 'ascending');
        await expect(args.dataRequest).toHaveBeenLastCalledWith({
            page: 0,
            pageSize: 20,
            searchTerm: undefined,
            sort: { direction: 'asc', field: 'name' },
        });
    },
};

export const Loading: Story = {
    args: {
        loading: true,
    },
};

export const Empty: Story = {
    args: {
        rows: [],
        totalRecords: 0,
    },
};
