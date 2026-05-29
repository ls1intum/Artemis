import type MarkdownIt from 'markdown-it';
import type { PluginSimple } from 'markdown-it';

/**
 * Markdown-It plugin that allows replacing text in the raw markdown before tokenizing.
 * See more about Markdown-It plugins here: https://github.com/markdown-it/markdown-it/tree/master/docs
 */
export abstract class ArtemisTextReplacementPlugin {
    getExtension(): PluginSimple {
        return (md: MarkdownIt): void => {
            md.core.ruler.before('normalize', 'artemis_text_replacement', (state) => {
                // Perform the replacement on the raw markdown text
                state.src = this.replaceText(state.src);
            });
        };
    }

    /**
     * Performs text replacement on the raw markdown before parsing.
     * @param text The raw markdown text.
     * @returns The modified markdown text after replacements.
     */
    abstract replaceText(text: string): string;
}
