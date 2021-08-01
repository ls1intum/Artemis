const webpack = require('webpack');
import 'webpack-dev-server';

const CopyWebpackPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');
import { AngularWebpackPlugin } from '@ngtools/webpack';
const MergeJsonWebpackPlugin = require('merge-jsons-webpack-plugin');
const environment = require('./environment');

import { mapTypescriptAliasToWebpackAlias, root } from './utils';

interface Options {
    // note: for some reason we have to use single and double quotes together here '"..."' to avoid warnings during webpack build
    env: '"production"' | '"development"';
}

export const commonConfig = (options: Options) => ({
    resolve: {
        extensions: ['.ts', '.js'],
        modules: ['node_modules'],
        mainFields: ['es2015', 'browser', 'module', 'main'],
        alias: mapTypescriptAliasToWebpackAlias(),
        fallback: {
            crypto: require.resolve('crypto-browserify'),
            stream: require.resolve('stream-browserify'),
        },
    },
    stats: {
        children: false,
    },
    performance: {
        maxEntrypointSize: 1024 * 1024,
        maxAssetSize: 1024 * 1024,
    },
    output: {
        publicPath: '',
    },
    module: {
        rules: [
            {
                test: /(?:\.ngfactory\.js|\.ngstyle\.js|\.ts)$/,
                loader: '@ngtools/webpack',
            },
            {
                test: /\.html$/,
                loader: 'html-loader',
                options: {
                    minimize: {
                        caseSensitive: true,
                        removeAttributeQuotes: false,
                        minifyJS: false,
                        minifyCSS: false,
                    },
                },
                exclude: root('src/main/webapp/index.html'),
            },
            {
                test: /\.(jpe?g|png|gif|svg|woff2?|ttf|eot)$/i,
                type: 'asset/resource',
                generator: {
                    filename: 'content/[hash][ext][query]',
                },
            },
            {
                test: /manifest.webapp$/,
                type: 'asset/resource',
                generator: {
                    filename: 'manifest.webapp',
                },
            },
            // Ignore warnings about System.import in Angular
            { test: /[\/\\]@angular[\/\\].+\.js$/, parser: { system: true } },
        ],
    },
    plugins: [
        new webpack.ProvidePlugin({
            process: 'process/browser',
        }),
        new webpack.DefinePlugin({
            __TIMESTAMP__: JSON.stringify(environment.__TIMESTAMP__),
            // APP_VERSION is passed as an environment variable from the Gradle / Maven build tasks.
            __VERSION__: JSON.stringify(environment.__VERSION__),
            __DEBUG_INFO_ENABLED__: environment.__DEBUG_INFO_ENABLED__ || options.env === '"development"',
            // The root URL for API calls, ending with a '/' - for example: `"https://www.jhipster.tech:8081/myservice/"`.
            // If this URL is left empty (""), then it will be relative to the current context.
            // If you use an API server, in `prod` mode, you will need to enable CORS
            // (see the `jhipster.cors` common JHipster property in the `application-*.yml` configurations)
            __SERVER_API_URL__: JSON.stringify(environment.__SERVER_API_URL__),
        }),
        new CopyWebpackPlugin({
            patterns: [
                { from: './src/main/webapp/content/', to: 'content' },
                { from: './src/main/webapp/favicon.svg', to: 'favicon.svg' },
                { from: './src/main/webapp/manifest.webapp', to: 'manifest.webapp' },
                { from: './src/main/webapp/robots.txt', to: 'robots.txt' },
                { from: './src/main/webapp/android-chrome-192x192.png', to: 'android-chrome-192x192.png' },
                { from: './src/main/webapp/android-chrome-512x512.png', to: 'android-chrome-512x512.png' },
                { from: './src/main/webapp/apple-touch-icon.png', to: 'apple-touch-icon.png' },
                { from: './src/main/webapp/browserconfig.xml', to: 'browserconfig.xml' },
                { from: './src/main/webapp/favicon.ico', to: 'favicon.ico' },
                { from: './src/main/webapp/favicon-16x16.png', to: 'favicon-16x16.png' },
                { from: './src/main/webapp/favicon-32x32.png', to: 'favicon-32x32.png' },
                { from: './src/main/webapp/mstile-70x70.png', to: 'mstile-70x70.png' },
                { from: './src/main/webapp/mstile-144x144.png', to: 'mstile-144x144.png' },
                { from: './src/main/webapp/mstile-150x150.png', to: 'mstile-150x150.png' },
                { from: './src/main/webapp/mstile-310x150.png', to: 'mstile-310x150.png' },
                { from: './src/main/webapp/mstile-310x310.png', to: 'mstile-310x310.png' },
                { from: './src/main/webapp/safari-pinned-tab.svg', to: 'safari-pinned-tab.svg' },
            ],
        }),
        new MergeJsonWebpackPlugin({
            output: {
                groupBy: [
                    { pattern: './src/main/webapp/i18n/en/*.json', fileName: './i18n/en.json' },
                    { pattern: './src/main/webapp/i18n/de/*.json', fileName: './i18n/de.json' },
                ],
            },
        }),
        new HtmlWebpackPlugin({
            template: './src/main/webapp/index.html',
            chunks: ['polyfills', 'main', 'global'],
            chunksSortMode: 'manual',
            inject: 'body',
            base: '/',
        }),
        new AngularWebpackPlugin({
            tsconfig: root('tsconfig.app.json')
        }),
    ],
});
