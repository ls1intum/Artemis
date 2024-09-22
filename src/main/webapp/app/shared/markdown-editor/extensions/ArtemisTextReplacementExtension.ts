import type MarkdownIt from 'markdown-it';
import type { PluginSimple } from 'markdown-it';

export abstract class ArtemisTextReplacementExtension {
    getExtension(): PluginSimple {
        return (md: MarkdownIt): void => {
            // Override the `render` method to process the raw Markdown text before tokenizing
            const originalRender = md.render.bind(md);
            md.render = (markdownText: string, ...args) => {
                // Perform the multi-line replacement on the raw markdown text
                const modifiedText = this.replaceText(markdownText);
                // Call the original render method with the modified text
                return originalRender(modifiedText, ...args);
            };
        };
    }

    abstract replaceText(text: string): string;
}
