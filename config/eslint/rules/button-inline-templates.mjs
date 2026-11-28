import angular from 'angular-eslint';

// Reuse Angular's extraction and source mapping, while applying only dedicated rules to inline templates. Each
// `@Component({ template })` is handed to ESLint twice, as the same text under two virtual extensions:
//
//   - `.button-intent` for the product button rule, which applies to the whole application, and
//   - `.lock-template` for the regression locks of the migrated modules (Bootstrap classes, ambiguous spacing,
//     hand-written PrimeNG classes, raw palette colours). eslint.config.mjs enables these only for the folders that
//     are locked, so existing external-template rule scopes do not silently expand with this check.
//
// Angular's postprocess maps the diagnostics of the template number `i` back to the component by its position, so the
// two blocks of one template are merged into a single entry again before they are handed over.
const EXTENSIONS = ['.button-intent', '.lock-template'];

export default {
    meta: { name: 'button-inline-templates', version: '2' },
    supportsAutofix: false,
    preprocess(text, filename) {
        const [source, ...templates] = angular.processInlineTemplates.preprocess(text, filename);
        return [source, ...templates.flatMap((block) => EXTENSIONS.map((extension) => ({ text: block.text, filename: block.filename.replace(/\.html$/, extension) })))];
    },
    postprocess(messages, filename) {
        const [source, ...blocks] = messages;
        const merged = [];
        for (let i = 0; i < blocks.length; i += EXTENSIONS.length) {
            merged.push(blocks.slice(i, i + EXTENSIONS.length).flat());
        }
        return angular.processInlineTemplates.postprocess([source, ...merged], filename);
    },
};
