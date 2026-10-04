import { DocsContainer, type DocsContainerProps } from '@storybook/addon-docs/blocks';
import { type PropsWithChildren, createElement } from 'react';
import { themes } from 'storybook/theming';

import { type ThemeName, resolveTheme } from './theme';

function currentTheme(context: DocsContainerProps['context']): ThemeName {
    const story = context.componentStories()[0];
    return resolveTheme(story ? context.getStoryContext(story).globals.theme : undefined);
}

export function ThemedDocsContainer({ children, context }: PropsWithChildren<DocsContainerProps>) {
    return createElement(DocsContainer, { context, theme: themes[currentTheme(context)] }, children);
}
