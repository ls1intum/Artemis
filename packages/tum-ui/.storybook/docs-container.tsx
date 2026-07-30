import { DocsContainer, type DocsContainerProps } from '@storybook/addon-docs/blocks';
import type { PropsWithChildren } from 'react';
import { themes } from 'storybook/theming';

import { resolveTheme, type ThemeName } from './theme';

function currentTheme(context: DocsContainerProps['context']): ThemeName {
    const story = context.componentStories()[0];
    return resolveTheme(story ? context.getStoryContext(story).globals.theme : undefined);
}

export function ThemedDocsContainer({ children, context }: PropsWithChildren<DocsContainerProps>) {
    return (
        <DocsContainer context={context} theme={themes[currentTheme(context)]}>
            {children}
        </DocsContainer>
    );
}
