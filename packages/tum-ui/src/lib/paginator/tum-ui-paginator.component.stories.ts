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
    parameters: {
        layout: 'padded',
    },
} satisfies Meta<TumUiPaginatorComponent>;

export default meta;

type Story = StoryObj<TumUiPaginatorComponent>;

export const Default: Story = {
    play: async ({ args, canvas, userEvent }) => {
        await expect(canvas.getByText('Showing 41 to 60 of 128')).toBeVisible();
        await expect(canvas.getByRole('button', { current: 'page' })).toHaveTextContent('3');

        await userEvent.click(canvas.getByRole('button', { name: 'Next page' }));
        await expect(args.pageChange).toHaveBeenCalledWith(3);
    },
};

export const Empty: Story = {
    args: {
        page: 0,
        totalRecords: 0,
    },
};
