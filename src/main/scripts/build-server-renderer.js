/**
 * Builds the server-side markdown renderer bundle for GraalJS.
 * Bundles the same markdown-it pipeline used by the Angular client
 * into a single IIFE that GraalJS can load.
 *
 * Usage: node src/main/scripts/build-server-renderer.js
 * Or via npm: npm run build:server-renderer
 */
const esbuild = require('esbuild');
const path = require('path');

const projectRoot = path.resolve(__dirname, '../../..');
const outFile = path.join(projectRoot, 'build/resources/main/graaljs/markdown-renderer.bundle.js');

// Entry module code — mirrors the Angular client's markdown-it pipeline
// Source of truth: src/main/webapp/app/shared/util/markdown.conversion.util.ts
const entryCode = String.raw`
import MarkdownIt from 'markdown-it';
import MarkdownItHighlightjs from 'markdown-it-highlightjs';
import MarkdownItKatex from '@vscode/markdown-it-katex';
import MarkdownItGitHubAlerts from 'markdown-it-github-alerts';

// --- FormulaCompatibilityPlugin ---
// Mirrors: markdown.conversion.util.ts FormulaCompatibilityPlugin
// Converts inline $$ delimiters to $ when surrounded by text,
// and converts \\begin/\\end to \begin/\end for KaTeX compatibility.
const inlineFormulaRegex = /.+\$\$[^$]+\$\$|\$\$[^$]+\$\$.+/g;

function formulaCompatibilityPlugin(md) {
    md.core.ruler.before('normalize', 'artemis_text_replacement', (state) => {
        state.src = state.src
            .split('\n')
            .map(line => {
                if (line.match(inlineFormulaRegex)) {
                    line = line.replace(/\$\$/g, '$');
                }
                if (line.includes('\\\\begin') || line.includes('\\\\end')) {
                    line = line.replaceAll('\\\\begin', '\\begin').replaceAll('\\\\end', '\\end');
                }
                return line;
            })
            .join('\n');
    });
}

// --- MarkdownitTagClass plugin ---
// Mirrors: markdown.conversion.util.ts MarkdownitTagClass
// Assigns CSS classes to specific HTML tags in the markdown output.
function setTokenClasses(tokens, mapping) {
    tokens.forEach(token => {
        const isOpeningTag = token.nesting !== -1;
        if (isOpeningTag && mapping[token.tag]) {
            const existingClassAttr = token.attrGet('class') || '';
            const existingClasses = existingClassAttr.split(' ').filter(Boolean);
            const givenClasses = mapping[token.tag];
            const newClasses = [...existingClasses, ...(Array.isArray(givenClasses) ? givenClasses : [givenClasses])];
            token.attrSet('class', newClasses.join(' ').trim());
        }
        if (token.children) {
            setTokenClasses(token.children, mapping);
        }
    });
}

function markdownitTagClass(md, mapping) {
    md.core.ruler.push('markdownit-tag-class', (state) => {
        setTokenClasses(state.tokens, mapping || {});
    });
}

// --- Configure markdown-it ---
// Plugin order MUST match the Angular client:
// highlightjs -> formula-compat -> katex -> github-alerts -> tag-class
const md = MarkdownIt({
    html: true,
    linkify: true,
    breaks: false,
});

md.use(MarkdownItHighlightjs);
md.use(formulaCompatibilityPlugin);
md.use(MarkdownItKatex, { enableMathInlineInHtml: true });
md.use(MarkdownItGitHubAlerts);
md.use(markdownitTagClass, { table: 'table' });

// --- Export render function ---
globalThis.renderMarkdown = function(text) {
    if (!text) return '';
    let result = md.render(text);
    // Keep legacy behavior: strip trailing newline
    if (result.endsWith('\n')) {
        result = result.slice(0, -1);
    }
    return result;
};
`;

esbuild.buildSync({
    stdin: {
        contents: entryCode,
        resolveDir: projectRoot,
        loader: 'js',
    },
    bundle: true,
    format: 'iife',
    outfile: outFile,
    platform: 'neutral',
    mainFields: ['module', 'main'],
    target: 'es2020',
    minify: false,
});

console.log('Server renderer bundle built successfully:', outFile);
