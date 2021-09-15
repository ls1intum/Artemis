const mainConfig = require('./jest.config');

module.exports = {
    ...mainConfig,
    globals: {
        //TODO: this is copied from webpack/environment.ts --> would be better, if we could reuse it
        __TIMESTAMP__: String(new Date().getTime()),
        __VERSION__: process.env.hasOwnProperty('APP_VERSION') ? process.env.APP_VERSION : 'DEV',
        __DEBUG_INFO_ENABLED__: false,
        __SERVER_API_URL__: '',
        'ts-jest': {
            tsconfig: '<rootDir>/tsconfig.spec.json',
            stringifyContentPathRegex: '\\.html$',
            isolatedModules: true,
            diagnostics: {
                ignoreCodes: [151001],
            },
        },
    },
};
