import { argsToTemplate, moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn } from 'storybook/test';
import { TumUiTableSortableColumnComponent } from './tum-ui-table-sortable-column.component';
import { TumUiTableDirective } from './tum-ui-table.directive';

const meta = {
    title: 'Data Display/Native Table',
    component: TumUiTableDirective,
    subcomponents: {
        SortableColumn: TumUiTableSortableColumnComponent,
    },
    argTypes: {
        size: {
            control: 'inline-radio',
            options: ['small', 'normal', 'large'],
        },
        sortOrder: {
            control: 'inline-radio',
            options: [1, -1],
        },
    },
    decorators: [
        moduleMetadata({
            imports: [TumUiTableSortableColumnComponent],
        }),
    ],
    args: {
        rowHover: true,
        size: 'normal',
        sortChange: fn(),
        sortField: 'name',
        sortOrder: 1,
        striped: true,
    },
    render: (args) => ({
        props: args,
        template: `
            <table
                tumUiTable
                ${argsToTemplate(args, { exclude: ['sortChange'] })}
                (sortChange)="sortField = $event.field; sortOrder = $event.order; sortChange($event)"
            >
                <thead>
                    <tr>
                        <th tumUiSortableColumn="name">Participant</th>
                        <th tumUiSortableColumn="score">Score</th>
                        <th>Status</th>
                    </tr>
                </thead>
                <tbody>
                    <tr><td>Ada Lovelace</td><td>98</td><td>Submitted</td></tr>
                    <tr><td>Grace Hopper</td><td>94</td><td>Submitted</td></tr>
                    <tr><td>Margaret Hamilton</td><td>91</td><td>In review</td></tr>
                </tbody>
            </table>
        `,
    }),
    parameters: {
        layout: 'padded',
    },
} satisfies Meta<TumUiTableDirective>;

export default meta;

type Story = StoryObj<TumUiTableDirective>;

export const Default: Story = {
    play: async ({ args, canvas, userEvent }) => {
        const participantHeader = canvas.getByRole('columnheader', { name: /Participant/ });
        await expect(participantHeader).toHaveAttribute('aria-sort', 'ascending');

        await userEvent.click(canvas.getByRole('button', { name: /Participant/ }));

        await expect(args.sortChange).toHaveBeenCalledWith({ field: 'name', order: -1 });
        await expect(participantHeader).toHaveAttribute('aria-sort', 'descending');
    },
};
