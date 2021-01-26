const esModules = ['ngx-treeview', 'lodash-es'].join('|');
module.exports = {
    globals: {
        'ts-jest': {
            tsconfig: '<rootDir>/tsconfig.spec.json',
            stringifyContentPathRegex: '\\.html$',
            astTransformers: {
                before: [require.resolve('./InlineHtmlStripStylesTransformer')],
            },
            diagnostics: {
                ignoreCodes: [151001],
            },
        },
    },
    collectCoverageFrom: ['src/main/webapp/**/*.{js,jsx,ts,tsx}', '!src/main/webapp/**/*.module.{js,jsx,ts,tsx}'],
    coverageThreshold: {
        global: {
            // TODO: in the future, the following values should be increase to 80%
            statements: 66,
            branches: 48,
            functions: 55,
            lines: 65,
        },
    },
    preset: 'jest-preset-angular',
    setupFilesAfterEnv: ['<rootDir>/src/test/javascript/jest.ts', 'jest-sinon'],
    modulePaths: ['<rootDir>/src/main/webapp/'],
    transformIgnorePatterns: [`/node_modules/(?!${esModules})`],
    rootDir: '../../../',
    modulePathIgnorePatterns: [],
    testMatch: [
        '<rootDir>/src/test/javascript/spec/component/**/*.ts',
        '<rootDir>/src/test/javascript/spec/directive/**/*.ts',
        '<rootDir>/src/test/javascript/spec/integration/**/*.ts',
        '<rootDir>/src/test/javascript/spec/pipe/**/*.ts',
        '<rootDir>/src/test/javascript/spec/service/**/*.ts',
        '<rootDir>/src/test/javascript/spec/util/**/*.ts',
    ],
    moduleNameMapper: {
        '^app/(.*)': '<rootDir>/src/main/webapp/app/$1',
        'test/(.*)': '<rootDir>/src/test/javascript/spec/$1',
        '@assets/(.*)': '<rootDir>/src/main/webapp/assets/$1',
        '@core/(.*)': '<rootDir>/src/main/webapp/app/core/$1',
        '@env': '<rootDir>/src/main/webapp/environments/environment',
        '@src/(.*)': '<rootDir>/src/src/$1',
        '@state/(.*)': '<rootDir>/src/app/state/$1',
    },
};
