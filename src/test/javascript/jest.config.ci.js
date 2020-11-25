const mainConfig = require('./jest.config');

module.exports = {
    ...mainConfig,
    globals: {
        'ts-jest': {
            tsconfig: '<rootDir>/tsconfig.spec.json',
            stringifyContentPathRegex: '\\.html$',
            astTransformers: {
                before: [require.resolve('./InlineHtmlStripStylesTransformer')],
            },
            isolatedModules: true,
            diagnostics: {
                ignoreCodes: [151001],
            },
        },
    },
};
