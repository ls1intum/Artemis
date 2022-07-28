const esModules = ['lodash-es', 'franc-min', 'trigram-utils', 'n-gram', 'collapse-white-space', '@angular/animations', '@angular/common', '@ls1intum/apollon',
    '@angular/compiler', '@angular/core', '@angular/forms', '@angular/localize', '@angular/platform-browser', '@angular/platform-browser-dynamic', '@angular/router',
    '@ngx-translate/core', '@ngx-translate/http-loader', '@fortawesome/angular-fontawesome', '@angular/cdk', '@angular/material', '@angular/cdk', 'dayjs/esm',
    'rxjs/operators', '@ng-bootstrap/ng-bootstrap', 'ngx-webstorage', '@ctrl/ngx-emoji-mart', 'ngx-device-detector', '@swimlane/ngx-charts',
    '@angular/service-worker', '@danielmoncada/angular-datetime-picker', '@flaviosantoro92/ngx-datatable'].join('|');

const {
    compilerOptions: { baseUrl = './' },
} = require('./tsconfig.json');
const environment = require('./webpack/environment');

module.exports = {
    globalSetup: 'jest-preset-angular/global-setup',
    globals: {
        ...environment,
        'ts-jest': {
            tsconfig: '<rootDir>/tsconfig.spec.json',
            stringifyContentPathRegex: '\\.html$',
            isolatedModules: true,
            diagnostics: {
                ignoreCodes: [151001],
            },
        },
    },
    testEnvironmentOptions: {
        url: 'https://artemis.fake/test'
    },
    roots: ['<rootDir>', `<rootDir>/${baseUrl}`],
    modulePaths: [`<rootDir>/${baseUrl}`],
    setupFiles: ['jest-date-mock'],
    cacheDirectory: '<rootDir>/build/jest-cache',
    coverageDirectory: '<rootDir>/build/test-results/',
    reporters: ['default', ['jest-junit', { outputDirectory: '<rootDir>/build/test-results/', outputName: 'TESTS-results-jest.xml' }]],
    collectCoverageFrom: ['src/main/webapp/**/*.{js,jsx,ts,tsx}', '!src/main/webapp/**/*.module.{js,jsx,ts,tsx}'],
    coveragePathIgnorePatterns: [
        '/node_modules/',
        'src/main/webapp/app/account/account.route.ts',
        'src/main/webapp/app/admin/admin.route.ts',
        'src/main/webapp/app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.route.ts',
        'src/main/webapp/app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.route.ts',
        'src/main/webapp/app/exercises/quiz/manage/quiz-management.route.ts',
        'src/main/webapp/app/admin/organization-management/organization-management.route.ts',
        'src/main/webapp/app/admin/system-notification-management/system-notification-management.route.ts',
        'src/main/webapp/app/admin/upcoming-exams-and-exercises/upcoming-exams-and-exercises.route.ts',
        'src/main/webapp/app/admin/user-management/user-management.route.ts',
        'src/main/webapp/app/assessment/assessment-locks/assessment-locks.route.ts',
        'src/main/webapp/app/complaints/list-of-complaints/list-of-complaints.route.ts',
        'src/main/webapp/app/course/dashboards/assessment-dashboard/assessment-dashboard.route.ts',
        'src/main/webapp/app/course/manage/course-management.route.ts',
        'src/main/webapp/app/exam/exam-scores/exam-scores.route.ts',
        'src/main/webapp/app/exam/participate/exam-participation.route.ts',
        'src/main/webapp/app/exercises/file-upload/manage/file-upload-exercise-management.route.ts',
        'src/main/webapp/app/exercises/modeling/manage/modeling-exercise.route.ts',
        'src/main/webapp/app/exam/manage/exam-management.route.ts',
        'src/main/webapp/app/exercises/shared/exercise-hint/manage/exercise-hint.route.ts',
        'src/main/webapp/app/core/config/prod.config.ts'
    ],
    coverageThreshold: {
        global: {
            // TODO: in the future, the following values should be increase to at least 85%
            statements: 84.5,
            branches: 71.6,
            functions: 77.4,
            lines: 84.2,
        },
    },
    setupFilesAfterEnv: ['<rootDir>/src/test/javascript/spec/jest-test-setup.ts', 'jest-extended/all'],
    moduleFileExtensions: ['ts', 'html', 'js', 'json', 'mjs'],
    resolver: '<rootDir>/jest.resolver.js',
    transformIgnorePatterns: [`/node_modules/(?!${esModules})`],
    transform: {
        '^.+\\.(ts|js|mjs|html|svg)$': 'jest-preset-angular',
    },
    modulePathIgnorePatterns: [],
    testTimeout: 3000,
    testMatch: [
        '<rootDir>/src/test/javascript/spec/component/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/directive/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/entities/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/integration/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/pipe/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/service/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/util/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/interceptor/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/config/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/core/**/*.spec.ts'
    ],
    moduleNameMapper: {
        '^app/(.*)': '<rootDir>/src/main/webapp/app/$1',
        'test/(.*)': '<rootDir>/src/test/javascript/spec/$1',
        '@assets/(.*)': '<rootDir>/src/main/webapp/assets/$1',
        '@core/(.*)': '<rootDir>/src/main/webapp/app/core/$1',
        '@env': '<rootDir>/src/main/webapp/environments/environment',
        '@src/(.*)': '<rootDir>/src/src/$1',
        '@state/(.*)': '<rootDir>/src/app/state/$1',
        "^lodash-es$": "lodash"
    },
};
