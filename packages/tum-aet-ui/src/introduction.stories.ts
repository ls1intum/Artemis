import type { Meta, StoryObj } from '@storybook/angular-vite';

const meta = {
    title: 'Introduction',
} satisfies Meta;

export default meta;

type Story = StoryObj<typeof meta>;

export const ThemeContext: Story = {
    tags: ['!autodocs', '!dev', '!test'],
    render: () => ({ template: '' }),
};
