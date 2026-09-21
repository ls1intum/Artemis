import { faFileImport, faList, faMagnifyingGlass, faPlus } from '@fortawesome/free-solid-svg-icons';
import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiButtonComponent } from '../button/tum-ui-button.component';
import { TumUiEmptyStateComponent } from './tum-ui-empty-state.component';
import type { TumUiEmptyStateVariant } from './tum-ui-empty-state.variants';

const variants: TumUiEmptyStateVariant[] = ['outlined', 'solid', 'plain'];

interface EmptyStateStoryArgs {
    title: string;
    description: string;
    variant: TumUiEmptyStateVariant;
    actions?: never;
    documentation?: never;
}

const meta = {
    title: 'Data Display/Empty State',
    component: TumUiEmptyStateComponent,
    args: {
        title: 'No exercises yet',
        description: 'Exercises you create for this course will be listed here. You can also import exercises from an existing course.',
        variant: 'outlined',
    },
    argTypes: {
        variant: {
            control: 'select',
            options: variants,
        },
        actions: {
            description: 'Optional content projected into the action area using the `[tumUiEmptyStateActions]` attribute.',
            control: false,
            table: {
                category: 'Content projection',
                type: { summary: 'HTML content' },
                defaultValue: { summary: 'optional' },
            },
        },
        documentation: {
            description: 'Optional link projected below the actions using the `[tumUiEmptyStateDocumentation]` attribute.',
            control: false,
            table: {
                category: 'Content projection',
                type: { summary: 'HTML link' },
                defaultValue: { summary: 'optional' },
            },
        },
    },
    decorators: [
        moduleMetadata({
            imports: [TumUiButtonComponent],
        }),
    ],
    render: (args) => ({
        props: {
            ...args,
            icon: faList,
        },
        template: `<tum-ui-empty-state [icon]="icon" [title]="title" [description]="description" [variant]="variant" />`,
    }),
} satisfies Meta<EmptyStateStoryArgs>;

export default meta;

type Story = StoryObj<EmptyStateStoryArgs>;

export const Default: Story = {};

export const Solid: Story = {
    args: {
        variant: 'solid',
    },
};

export const Plain: Story = {
    args: {
        variant: 'plain',
    },
};

export const EmptySearchResults: Story = {
    args: {
        title: 'No matching exercises',
        description: 'Try adjusting your search or filters to find what you are looking for.',
    },
    render: (args) => ({
        props: {
            ...args,
            icon: faMagnifyingGlass,
        },
        template: `<tum-ui-empty-state [icon]="icon" [title]="title" [description]="description" [variant]="variant" />`,
    }),
};

const renderWithProjectedContent = (args: EmptyStateStoryArgs, content: string) => ({
    props: {
        ...args,
        icon: faList,
        addIcon: faPlus,
        importIcon: faFileImport,
    },
    template: `
        <tum-ui-empty-state [icon]="icon" [title]="title" [description]="description" [variant]="variant">
            ${content}
        </tum-ui-empty-state>
    `,
});

const actionsContent = `
    <tum-ui-button tumUiEmptyStateActions [icon]="addIcon">Add exercise</tum-ui-button>
    <tum-ui-button tumUiEmptyStateActions [icon]="importIcon" variant="outlined">Import from course</tum-ui-button>
`;

const documentationContent = `
    <a tumUiEmptyStateDocumentation href="https://docs.artemis.tum.de/instructor/exercises/intro" target="_blank" rel="noopener noreferrer">
        Learn about exercises
    </a>
`;

export const WithActions: Story = {
    render: (args) => renderWithProjectedContent(args, actionsContent),
};

export const WithDocumentation: Story = {
    render: (args) => renderWithProjectedContent(args, documentationContent),
};

export const WithActionsAndDocumentation: Story = {
    name: 'With Actions & Documentation',
    render: (args) => renderWithProjectedContent(args, actionsContent + documentationContent),
};
