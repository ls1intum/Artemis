
exports.config = {
    allScriptsTimeout: 20000,

    specs: [
        './src/test/javascript/e2e/account/**/*.spec.ts',
        './src/test/javascript/e2e/admin/**/*.spec.ts',
        './src/test/javascript/e2e/entities/**/*.spec.ts',
        /* jhipster-needle-add-protractor-tests - JHipster will add protractors tests here */
    ],

    capabilities: {
        browserName: 'chrome',
        chromeOptions: {
            args: process.env.JHI_E2E_HEADLESS
                ? [ "--headless", "--disable-gpu", "--window-size=800,600", "incognito" ]
                : [ "--disable-gpu", "--window-size=800,600", "incognito" ]
        }
    },

    directConnect: true,

    baseUrl: 'http://localhost:9000/',

    framework: 'mocha',

    SELENIUM_PROMISE_MANAGER: false,

    mochaOpts: {
        reporter: 'spec',
        slow: 3000,
        ui: 'bdd',
        timeout: 720000
    },

    beforeLaunch: function() {
        require('ts-node').register({
            project: 'tsconfig-e2e.json'
        });
    },

    onPrepare: function() {
        browser.driver.manage().window().setSize(1280, 1024);
        // Disable animations
        // @ts-ignore
        browser.executeScript('document.body.className += " notransition";');
        const chai = require('chai');
        const chaiAsPromised = require('chai-as-promised');
        chai.use(chaiAsPromised);
        const chaiString = require('chai-string');
        chai.use(chaiString);
        // @ts-ignore
        global.chai = chai;
    },

    useAllAngular2AppRoots: true
};
