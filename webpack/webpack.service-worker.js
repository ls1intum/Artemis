const path = require('path');

const webBuildTargetFolder = path.join(__dirname, '..', 'build', 'resources', 'main', 'static');
const targetServiceWorkerFilename = 'service-worker.js';

module.exports = {
    target: 'node',
    mode: 'none',
    // WARNING: commented out to disable source maps
    //devtool: 'inline-source-map',
    entry: {
        index: path.join(__dirname, '..', 'src', 'main', 'webapp', 'app', 'service-worker.ts'),
    },
    resolve: { extensions: ['.js', '.ts'] },
    output: {
        path: webBuildTargetFolder,
        filename: targetServiceWorkerFilename,
    },
    module: {
        rules: [
            {
                test: /\.ts$/,
                loader: 'ts-loader',
                options: {
                    onlyCompileBundledFiles: true,
                },
            },
        ],
    },
    plugins: [],
};
