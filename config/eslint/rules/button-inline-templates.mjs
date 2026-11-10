import angular from 'angular-eslint';

// Reuse Angular's extraction and source mapping, while applying only the product button rule to
// inline templates. Existing external-template rule scopes must not silently expand with this check.
export default {
    meta: { name: 'button-inline-templates', version: '1' },
    supportsAutofix: false,
    preprocess(text, filename) {
        return angular.processInlineTemplates
            .preprocess(text, filename)
            .map((block) => (typeof block === 'string' ? block : { text: block.text, filename: block.filename.replace(/\.html$/, '.button-intent') }));
    },
    postprocess(messages, filename) {
        return angular.processInlineTemplates.postprocess(messages, filename);
    },
};
