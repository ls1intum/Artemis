import { visit } from 'unist-util-visit';
import type { Plugin } from 'unified';
import type { Root, Text } from 'mdast';

/**
 * Remark plugin that emits a build warning when Sphinx-style :ref:`...` syntax
 * is found in Markdown/MDX files. These references are not supported in
 * Docusaurus and render as raw text instead of links.
 */
const warnSphinxRefs: Plugin<[], Root> = () => {
    return (tree, file) => {
        visit(tree, 'text', (node: Text) => {
            const refPattern = /:ref:`[^`]+`/g;
            let match;
            while ((match = refPattern.exec(node.value)) !== null) {
                file.message(
                    `Unsupported Sphinx :ref: syntax found: "${match[0]}". ` +
                    `Replace with a Markdown link, e.g. [link text](/path/to/page).`,
                    node,
                    'remark-warn-sphinx-refs'
                );
            }
        });
    };
};

export default warnSphinxRefs;
