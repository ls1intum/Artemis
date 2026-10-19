import type { MarkdownIt } from 'markdown-it';

/**
 * Signature of a markdown-it plugin that takes no options.
 *
 * markdown-it 15 ships first-party type declarations and no longer exports the `PluginSimple` alias
 * that the (now removed) `@types/markdown-it` package used to provide, so the project declares it here.
 */
export type MarkdownItPlugin = (md: MarkdownIt) => void;
