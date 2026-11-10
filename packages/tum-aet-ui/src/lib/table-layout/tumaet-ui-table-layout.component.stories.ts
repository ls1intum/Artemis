import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { TumAetUiButtonDirective } from '../button/tumaet-ui-button.directive';
import { TumAetUiInputDirective } from '../input/tumaet-ui-input.directive';
import { TumAetUiPaginatorComponent } from '../paginator/tumaet-ui-paginator.component';
import { TumAetUiTableSortableColumnComponent } from '../table-directive/tumaet-ui-table-sortable-column.component';
import { TumAetUiTableDirective } from '../table-directive/tumaet-ui-table.directive';
import { TumAetUiTableLayoutComponent } from './tumaet-ui-table-layout.component';

const ROWS = Array.from({ length: 60 }, (_, index) => ({
    id: 1000 + index,
    login: `student${index}`,
    name: `Student ${index}`,
    email: `student${index}@example.org`,
}));

interface TableLayoutStoryArgs {
    loading: boolean;
    fill: boolean;
}

const meta = {
    title: 'Data Display/Table Layout',
    component: TumAetUiTableLayoutComponent,
    decorators: [
        moduleMetadata({
            imports: [TumAetUiTableDirective, TumAetUiTableSortableColumnComponent, TumAetUiPaginatorComponent, TumAetUiInputDirective, TumAetUiButtonDirective],
        }),
    ],
    args: { loading: false, fill: false },
    render: (args) => ({
        props: { ...args, rows: ROWS },
        template: `
            <div style="height: 420px; display: flex; flex-direction: column;">
                <tumaet-ui-table-layout [loading]="loading" [fill]="fill">
                    <div tumAetUiTableToolbar style="display: flex; flex: 1; gap: 0.5rem; align-items: center;">
                        <input tumAetUiInput tumAetUiInputSize="small" type="search" placeholder="Search" style="min-width: 4rem; max-width: 18rem; flex: 1;" />
                        <span style="flex: 1"></span>
                        <button tumAetUiButton size="small" type="button">Create</button>
                    </div>
                    <table tumAetUiTable size="small">
                        <thead>
                            <tr>
                                <th tumAetUiSortableColumn="id">ID</th>
                                <th tumAetUiSortableColumn="login">Login</th>
                                <th tumAetUiSortableColumn="name">Name</th>
                                <th>Email</th>
                            </tr>
                        </thead>
                        <tbody>
                            @for (row of rows; track row.id) {
                                <tr>
                                    <td>{{ row.id }}</td>
                                    <td>{{ row.login }}</td>
                                    <td>{{ row.name }}</td>
                                    <td>{{ row.email }}</td>
                                </tr>
                            }
                        </tbody>
                    </table>
                    <tumaet-ui-paginator tumAetUiTableFooter [page]="0" [pageSize]="50" [totalRecords]="1284" />
                </tumaet-ui-table-layout>
            </div>
        `,
    }),
    parameters: { layout: 'padded' },
} satisfies Meta<TableLayoutStoryArgs>;

export default meta;

type Story = StoryObj<TableLayoutStoryArgs>;

/** Toolbar and footer stay in view while the body scrolls under the 30px sticky column header. */
export const Default: Story = {};

export const Loading: Story = { args: { loading: true } };
