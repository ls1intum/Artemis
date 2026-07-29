import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn } from 'storybook/test';
import { TumUiPaginatorComponent } from './tum-ui-paginator.component';

const meta = {
    title: 'Data Display/Paginator',
    component: TumUiPaginatorComponent,
    args: {
        page: 2,
        pageChange: fn(),
        pageSize: 20,
        pageSizeChange: fn(),
        totalRecords: 128,
    },
    render: (args) => ({
        props: args,
        template: `
            <tum-ui-paginator
                ${argsToTemplate(args, { exclude: ['page', 'pageChange', 'pageSize', 'pageSizeChange'] })}
                [page]="page"
                [pageSize]="pageSize"
                (pageChange)="page = $event; pageChange($event)"
                (pageSizeChange)="page = 0; pageSize = $event; pageSizeChange($event)"
            />
        `,
    }),
    parameters: {
        layout: 'padded',
    },
} satisfies Meta<TumUiPaginatorComponent>;

export default meta;

type Story = StoryObj<TumUiPaginatorComponent>;

export const Default: Story = {
    play: async ({ args, canvas, userEvent }) => {
        await expect(canvas.getByRole('navigation', { name: 'Pagination' })).toBeVisible();
        await expect(canvas.getByText('Showing 41 to 60 of 128')).toBeVisible();
        await expect(canvas.getByRole('button', { current: 'page' })).toHaveTextContent('3');

        await userEvent.click(canvas.getByRole('button', { name: 'Next page' }));
        await expect(args.pageChange).toHaveBeenCalledWith(3);
        await expect(canvas.getByText('Showing 61 to 80 of 128')).toBeVisible();
        await expect(canvas.getByRole('button', { current: 'page' })).toHaveTextContent('4');

        const pageSize = canvas.getByRole('combobox', { name: 'Rows per page' });
        await userEvent.selectOptions(pageSize, canvas.getByRole('option', { name: '50' }));
        await expect(args.pageSizeChange).toHaveBeenCalledWith(50);
        await expect(canvas.getByText('Showing 1 to 50 of 128')).toBeVisible();
        await expect(canvas.getByRole('button', { current: 'page' })).toHaveTextContent('1');
    },
};

export const Empty: Story = {
    args: {
        page: 0,
        totalRecords: 0,
    },
};
