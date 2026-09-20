import { componentWrapperDecorator } from '@storybook/angular-vite';

export const formStoryDecorator = componentWrapperDecorator((story) => `<div class="tum-ui-story-form">${story}</div>`);
export const inlineControlStoryDecorator = componentWrapperDecorator((story) => `<div class="tum-ui-story-inline-control">${story}</div>`);
