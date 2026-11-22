import type { Meta, StoryObj } from '@storybook/angular-vite';
import { TumAetUiAvatarComponent } from './tumaet-ui-avatar.component';

const meta = {
    title: 'Data Display/Avatar',
    component: TumAetUiAvatarComponent,
    args: { name: 'Ada Lovelace' },
} satisfies Meta<TumAetUiAvatarComponent>;
export default meta;
type Story = StoryObj<TumAetUiAvatarComponent>;
export const Initials: Story = {};
export const Portrait: Story = {
    args: {
        imageUrl:
            'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"%3E%3Crect width="48" height="48" fill="%230065bd"/%3E%3Ctext x="24" y="30" text-anchor="middle" fill="white" font-size="20"%3EAL%3C/text%3E%3C/svg%3E',
    },
};
export const ImageError: Story = { args: { imageUrl: '/missing-portrait.png' } };
export const NarrowDark: Story = { args: { name: 'Änne Öztürk', size: 'small' }, globals: { theme: 'dark', viewport: { value: 'mobile1' } } };
