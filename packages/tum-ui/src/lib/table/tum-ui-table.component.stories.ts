import { argsToTemplate, moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn } from 'storybook/test';
import { TumUiButtonDirective } from '../button/tum-ui-button.directive';
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
    argTypes: {
        rowActions: {
            control: false,
        },
        trackBy: {
            control: false,
        },
    },
} satisfies Meta<TumUiTableComponent<Participant>>;

export default meta;

type Story = StoryObj<TumUiTableComponent<Participant>>;

export const Default: Story = {
    play: async ({ canvasElement }) => {
        // Bootstrap Reboot is unlayered; the package must own borders without application overrides.
        const reset = document.createElement('style');
        reset.textContent = 'th, td { border-width: 0; }';
        document.head.append(reset);
        try {
            const header = canvasElement.querySelector('thead th')!;
            const firstCell = canvasElement.querySelector('tbody tr:first-child td')!;
            const lastCell = canvasElement.querySelector('tbody tr:last-child td')!;
            await expect(getComputedStyle(header).borderBottomWidth).toBe('1px');
            await expect(getComputedStyle(firstCell).borderBottomWidth).toBe('1px');
            await expect(getComputedStyle(lastCell).borderBottomWidth).toBe('0px');
        } finally {
            reset.remove();
        }
    },
};

export const RowActions: Story = {
    decorators: [
        moduleMetadata({
            imports: [TumUiButtonDirective],
        }),
    ],
    render: (args) => ({
        props: args,
        template: `
            <tum-ui-table ${argsToTemplate(args, { exclude: ['rowActions', 'trackBy'] })} [rowActions]="actions">
                <ng-template #actions let-participant>
                    <button tumUiButton size="small" type="button" [attr.aria-label]="'Inspect ' + participant.name">Inspect</button>
                </ng-template>
            </tum-ui-table>
        `,
    }),
};

export const InitiallySorted: Story = {
    args: {
        initialSortDirection: 'asc',
        initialSortField: 'name',
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

export const CompactViewport: Story = {
    parameters: {
        viewport: {
            options: {
                compact: {
                    name: 'Compact',
                    styles: {
                        width: '360px',
                        height: '640px',
                    },
                },
            },
        },
    },
    globals: {
        viewport: {
            value: 'compact',
            isRotated: false,
        },
    },
};
