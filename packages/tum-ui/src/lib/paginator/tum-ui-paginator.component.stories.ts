import type { Meta, StoryObj } from '@storybook/angular-vite';
import { useArgs } from 'storybook/preview-api';
import { expect, fn } from 'storybook/test';
import { TumUiPaginatorComponent } from './tum-ui-paginator.component';

interface PaginatorStoryArgs {
    disabled: boolean;
    page: number;
    pageChange: (page: number) => void;
    pageSize: number;
    pageSizeChange: (pageSize: number) => void;
    totalRecords: number;
}

const meta = {
    title: 'Data Display/Paginator',
    component: TumUiPaginatorComponent,
    args: {
        page: 2,
        pageChange: fn(),
        pageSize: 20,
        pageSizeChange: fn(),
        totalRecords: 128,
        disabled: false,
    },
    render: function Render(args) {
        const [{ page, pageSize }, updateArgs] = useArgs<PaginatorStoryArgs>();
        return {
            props: {
                ...args,
                page,
                pageSize,
                pageChange: (nextPage: number) => {
                    updateArgs({ page: nextPage });
                    args.pageChange(nextPage);
                },
                pageSizeChange: (nextPageSize: number) => {
                    updateArgs({ page: 0, pageSize: nextPageSize });
                    args.pageSizeChange(nextPageSize);
                },
            },
        };
    },
    parameters: {
        layout: 'padded',
    },
} satisfies Meta<PaginatorStoryArgs>;

export default meta;

type Story = StoryObj<PaginatorStoryArgs>;

export const Default: Story = {
    play: async ({ canvas }) => {
        await expect(canvas.getByRole('navigation', { name: 'Pagination' })).toBeVisible();
        await expect(canvas.getByText('Showing 41 to 60 of 128')).toBeVisible();
        await expect(canvas.getByRole('button', { current: 'page' })).toHaveTextContent('3');
    },
};

export const Empty: Story = {
    args: {
        page: 0,
        totalRecords: 0,
    },
};

export const Disabled: Story = {
    args: {
        disabled: true,
    },
};
