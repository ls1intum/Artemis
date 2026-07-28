import type { Meta, StoryObj } from '@storybook/angular-vite';
import { TumUiButtonComponent } from './tum-ui-button.component';
import { TumUiButtonSeverity, TumUiButtonSize, TumUiButtonVariant } from './tum-ui-button.variants';

const severities: TumUiButtonSeverity[] = ['primary', 'secondary', 'success', 'info', 'warn', 'danger', 'contrast'];
const variants: TumUiButtonVariant[] = ['solid', 'outlined', 'text'];

interface ButtonStoryArgs {
    label: string;
    severity: TumUiButtonSeverity;
    size: TumUiButtonSize;
    variant: TumUiButtonVariant;
    disabled: boolean;
    rounded: boolean;
    loading: boolean;
}

const meta = {
    title: 'Actions/Button',
    component: TumUiButtonComponent,
    args: {
        label: 'Continue',
        severity: 'primary',
        size: 'default',
        variant: 'solid',
        disabled: false,
        rounded: false,
        loading: false,
    },
    argTypes: {
        severity: {
            control: 'select',
            options: severities,
        },
        size: {
            control: 'select',
            options: ['small', 'default', 'large'],
        },
        variant: {
            control: 'select',
            options: variants,
        },
    },
    render: ({ label, ...args }) => ({
        props: { ...args, label },
        template: `
            <tum-ui-button
                [severity]="severity"
                [size]="size"
                [variant]="variant"
                [disabled]="disabled"
                [rounded]="rounded"
                [loading]="loading"
            >
                {{ label }}
            </tum-ui-button>
        `,
    }),
    parameters: {
        layout: 'centered',
    },
} satisfies Meta<ButtonStoryArgs>;

export default meta;

type Story = StoryObj<ButtonStoryArgs>;

export const Default: Story = {};

export const Variants: Story = {
    render: ({ label, size }) => ({
        props: { label, severities, size, variants },
        template: `
            <div style="display: grid; grid-template-columns: auto repeat(7, minmax(6rem, auto)); align-items: center; gap: 0.75rem;">
                @for (variant of variants; track variant) {
                    <strong style="text-transform: capitalize;">{{ variant }}</strong>
                    @for (severity of severities; track severity) {
                        <tum-ui-button [severity]="severity" [size]="size" [variant]="variant">
                            {{ label }}
                        </tum-ui-button>
                    }
                }
            </div>
        `,
    }),
    parameters: {
        controls: {
            include: ['label', 'size'],
        },
        layout: 'padded',
    },
};

export const States: Story = {
    render: ({ severity, size, variant }) => ({
        props: { severity, size, variant },
        template: `
            <div style="display: flex; align-items: center; gap: 0.75rem;">
                <tum-ui-button [severity]="severity" [size]="size" [variant]="variant">Default</tum-ui-button>
                <tum-ui-button [severity]="severity" [size]="size" [variant]="variant" disabled>Disabled</tum-ui-button>
                <tum-ui-button [severity]="severity" [size]="size" [variant]="variant" loading>Loading</tum-ui-button>
                <tum-ui-button [severity]="severity" [size]="size" [variant]="variant" rounded>Rounded</tum-ui-button>
            </div>
        `,
    }),
    parameters: {
        controls: {
            include: ['severity', 'size', 'variant'],
        },
    },
};

export const DarkTheme: Story = {
    ...Variants,
    globals: {
        theme: 'dark',
    },
};
