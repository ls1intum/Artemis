import { DocsContainer, type DocsContainerProps } from '@storybook/addon-docs/blocks';
import type { PropsWithChildren } from 'react';
import { themes } from 'storybook/theming';

type ThemeName = 'light' | 'dark';

function themeName(value: unknown): ThemeName {
    return value === 'dark' ? 'dark' : 'light';
}

function currentTheme(context: DocsContainerProps['context']): ThemeName {
    const story = context.componentStories()[0];
    return story ? themeName(context.getStoryContext(story).globals.theme) : 'light';
}

export function ThemedDocsContainer({ children, context }: PropsWithChildren<DocsContainerProps>) {
    return (
        <DocsContainer context={context} theme={themes[currentTheme(context)]}>
            {children}
        </DocsContainer>
    );
}
