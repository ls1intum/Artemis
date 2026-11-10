import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFilter, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { TumAetUiButtonDirective } from '../button/tumaet-ui-button.directive';
import { TumAetUiInputDirective } from '../input/tumaet-ui-input.directive';
import { TumAetUiPaginatorComponent } from '../paginator/tumaet-ui-paginator.component';
import { TumAetUiTableSortableColumnComponent } from '../table-directive/tumaet-ui-table-sortable-column.component';
import { TumAetUiTableDirective, TumAetUiTableSortEvent } from '../table-directive/tumaet-ui-table.directive';
import { TumAetUiTableLayoutComponent } from './tumaet-ui-table-layout.component';

interface ExampleUser {
    id: number;
    login: string;
    name: string;
    email: string;
    language: string;
    created: string;
}

const NAMES = ['Ada Lovelace', 'Alan Turing', 'Grace Hopper', 'Edsger Dijkstra', 'Barbara Liskov', 'Donald Knuth', 'Margaret Hamilton', 'Linus Torvalds'];
const TOTAL_USERS = 1284;

const USERS: ExampleUser[] = Array.from({ length: TOTAL_USERS }, (_, index) => {
    const name = NAMES[index % NAMES.length];
    const login = `${name.split(' ')[1].toLowerCase().slice(0, 7)}${index}`;
    return {
        id: 1000 + index,
        login,
        name,
        email: `${login}@example.org`,
        language: index % 5 === 0 ? 'de' : 'en',
        created: new Date(Date.UTC(2024, 8, 1 + ((index * 3) % 400))).toISOString().slice(0, 10),
    };
});

/**
 * The reference page of the data table design: a list with search, actions, sortable columns and paging. Applications
 * build the same structure around their own data. Everything outside the body stays in view while the rows scroll.
 */
@Component({
    selector: 'tumaet-ui-table-layout-example',
    imports: [
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiInputDirective,
        TumAetUiPaginatorComponent,
        TumAetUiTableDirective,
        TumAetUiTableSortableColumnComponent,
        TumAetUiTableLayoutComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        <div style="display: flex; flex-direction: column; height: 520px">
            <tumaet-ui-table-layout [loading]="loading()" fill>
                <div tumAetUiTableToolbar style="display: flex; flex: 1; min-width: 0; align-items: center; gap: 0.5rem">
                    <input
                        tumAetUiInput
                        tumAetUiInputSize="small"
                        type="search"
                        placeholder="Search users"
                        aria-label="Search users"
                        style="flex: 1; min-width: 4rem; max-width: 18rem"
                    />
                    <span style="flex: 1"></span>
                    <button tumAetUiButton type="button" size="small" severity="danger" variant="outlined" aria-label="Delete selected users" disabled>
                        <fa-icon [icon]="faTrash" />
                        @if (!compact()) {
                            <span>Delete</span>
                        }
                    </button>
                    <button tumAetUiButton type="button" size="small" severity="secondary" variant="outlined" aria-label="Filter users">
                        <fa-icon [icon]="faFilter" />
                        @if (!compact()) {
                            <span>Filter</span>
                        }
                    </button>
                    <button tumAetUiButton type="button" size="small" aria-label="Create user">
                        <fa-icon [icon]="faPlus" />
                        @if (!compact()) {
                            <span>Create user</span>
                        }
                    </button>
                </div>

                <table tumAetUiTable size="small" [sortField]="sortField()" [sortOrder]="sortOrder()" (sortChange)="onSort($event)" style="min-width: 44rem">
                    <thead>
                        <tr>
                            <th tumAetUiSortableColumn="id">ID</th>
                            <th tumAetUiSortableColumn="login">Login</th>
                            <th tumAetUiSortableColumn="name">Name</th>
                            <th tumAetUiSortableColumn="email">Email</th>
                            <th tumAetUiSortableColumn="language">Language</th>
                            <th tumAetUiSortableColumn="created">Created</th>
                        </tr>
                    </thead>
                    <tbody>
                        @for (user of pageRows(); track user.id) {
                            <tr style="white-space: nowrap">
                                <td>{{ user.id }}</td>
                                <td>{{ user.login }}</td>
                                <td>{{ user.name }}</td>
                                <td>{{ user.email }}</td>
                                <td>{{ user.language }}</td>
                                <td>{{ user.created }}</td>
                            </tr>
                        }
                    </tbody>
                </table>

                <tumaet-ui-paginator
                    tumAetUiTableFooter
                    [page]="page()"
                    [pageSize]="pageSize()"
                    [totalRecords]="total"
                    [disabled]="loading()"
                    (pageChange)="page.set($event)"
                    (pageSizeChange)="onPageSizeChange($event)"
                />
            </tumaet-ui-table-layout>
        </div>
    `,
})
class TableLayoutExampleComponent {
    /** Dims the body with a spinner while rows load. */
    readonly loading = input(false);
    /** Icons only in the toolbar, as applications do below the `md` breakpoint. */
    readonly compact = input(false);

    protected readonly faFilter = faFilter;
    protected readonly faPlus = faPlus;
    protected readonly faTrash = faTrash;
    protected readonly total = TOTAL_USERS;

    protected readonly page = signal(0);
    protected readonly pageSize = signal(50);
    protected readonly sortField = signal<keyof ExampleUser>('id');
    protected readonly sortOrder = signal(1);

    protected readonly pageRows = computed(() => {
        const field = this.sortField();
        const order = this.sortOrder();
        const sorted = [...USERS].sort((a, b) => (a[field] < b[field] ? -1 : a[field] > b[field] ? 1 : 0) * order);
        return sorted.slice(this.page() * this.pageSize(), (this.page() + 1) * this.pageSize());
    });

    protected onSort(event: TumAetUiTableSortEvent): void {
        this.sortField.set(event.field as keyof ExampleUser);
        this.sortOrder.set(event.order);
        this.page.set(0);
    }

    protected onPageSizeChange(size: number): void {
        this.pageSize.set(size);
        this.page.set(0);
    }
}

interface TableLayoutStoryArgs {
    loading: boolean;
    compact: boolean;
    width: string;
}

const meta = {
    title: 'Data Display/Table Layout',
    component: TumAetUiTableLayoutComponent,
    decorators: [moduleMetadata({ imports: [TableLayoutExampleComponent] })],
    args: { loading: false, compact: false, width: '100%' },
    argTypes: {
        loading: { control: 'boolean', description: 'Dims the body with a spinner while rows load.' },
        compact: { control: 'boolean', description: 'Icons only in the toolbar, as applications do below the `md` breakpoint.' },
        width: { control: 'text', description: 'Width of the story, to see the frame follow its container.' },
    },
    render: (args) => ({
        props: args,
        template: `<div [style.width]="width" style="max-width: 100%"><tumaet-ui-table-layout-example [loading]="loading" [compact]="compact" /></div>`,
    }),
    parameters: {
        layout: 'padded',
        docs: {
            description: {
                component:
                    'The design template for every data table: a sticky toolbar of at most 40px, a body that is the only part that scrolls under a sticky 30px column header, and a sticky footer of at most 40px with the range and a slim pager. Toolbar and footer never wrap. Scroll the rows, sort the columns and resize the story to see them stay in view.',
            },
        },
    },
} satisfies Meta<TableLayoutStoryArgs>;

export default meta;

type Story = StoryObj<TableLayoutStoryArgs>;

/** Toolbar, 30px header and footer stay in view while the body scrolls; sorting and paging work. */
export const Default: Story = {};

/** Below 28rem the footer shrinks to the total and "page / pages", and the toolbar keeps to icons. */
export const Phone: Story = { args: { compact: true, width: '390px' } };

/** While rows load, the body is dimmed, the frame is announced as busy and the pager is disabled by the page. */
export const Loading: Story = { args: { loading: true } };
