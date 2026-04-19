import MarkdownIt from 'markdown-it';

export function MarkdownItMermaidPlugin(md: MarkdownIt): void {
    const defaultRender =
        md.renderer.rules.fence ||
        function (tokens, idx, options, env, self) {
            return self.renderToken(tokens, idx, options);
        };

    md.renderer.rules.fence = function (tokens, idx, options, env, self) {
        const token = tokens[idx];
        const info = token.info ? token.info.trim() : '';

        if (info === 'mermaid') {
            const code = md.utils.escapeHtml(token.content.trim());
            return `<div class="mermaid">${code}</div>\n`;
        }

        return defaultRender(tokens, idx, options, env, self);
    };
}
