const tslintHtmlReport = require('tslint-html-report');

const config = {
    tslint: 'tslint.json', // path to tslint.json
    srcFiles: 'src/main/webapp/app/**/*.ts', // files to lint
    outDir: 'reports/tslint-html-report', // output folder to write the report to
    html: 'tslint-report.html', // name of the html report generated
    exclude: ['node_modules/**'], // Files/patterns to exclude
    breakOnError: true, // Should it throw an error in tslint errors are found
    typeCheck: true, // enable type checking. requires tsconfig.json
    tsconfig: 'tsconfig.json', // path to tsconfig.json
};

tslintHtmlReport(
    config,
    result => console.log("test")
);
