const webpack = require('webpack');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const AngularCompilerPlugin = require('@ngtools/webpack').AngularCompilerPlugin;
const MergeJsonWebpackPlugin = require("merge-jsons-webpack-plugin");
const WorkboxPlugin = require('workbox-webpack-plugin');

const utils = require('./utils.js');

module.exports = (options) => ({
    resolve: {
        extensions: ['.ts', '.js'],
        modules: ['node_modules'],
        mainFields: ['es2015', 'browser', 'module', 'main'],
        alias: utils.mapTypescriptAliasToWebpackAlias()
    },
    stats: {
        children: false
    },
    performance: {
        maxEntrypointSize: 1024*1024,
        maxAssetSize: 1024*1024,
    },
    module: {
        rules: [
            {
                test: /(?:\.ngfactory\.js|\.ngstyle\.js|\.ts)$/,
                loader: '@ngtools/webpack'
            },
            {
                test: /\.html$/,
                loader: 'html-loader',
                options: {
                    minimize: {
                        caseSensitive: true,
                        removeAttributeQuotes: false,
                        minifyJS: false,
                        minifyCSS: false
                    }
                },
                exclude: utils.root('src/main/webapp/index.html')
            },
            {
                test: /\.(jpe?g|png|gif|svg|woff2?|ttf|eot)$/i,
                loader: 'file-loader',
                options: {
                    digest: 'hex',
                    hash: 'sha512',
                    name: 'content/[hash].[ext]'
                }
            },
            {
                test: /manifest.webapp$/,
                loader: 'file-loader',
                options: {
                    name: 'manifest.webapp'
                }
            },
            // Ignore warnings about System.import in Angular
            { test: /[\/\\]@angular[\/\\].+\.js$/, parser: { system: true } },
        ]
    },
    plugins: [
        new webpack.DefinePlugin({
            'process.env': {
                NODE_ENV: `'${options.env}'`,
                BUILD_TIMESTAMP: `'${new Date().getTime()}'`,
                // APP_VERSION is passed as an environment variable from the Gradle / Maven build tasks.
                VERSION: `'${process.env.hasOwnProperty('APP_VERSION') && process.env.APP_VERSION !== "unspecified" ? process.env.APP_VERSION : utils.parseVersion()}'`,
                DEBUG_INFO_ENABLED: options.env === 'development',
                // The root URL for API calls, ending with a '/' - for example: `"https://www.jhipster.tech:8081/myservice/"`.
                // If this URL is left empty (""), then it will be relative to the current context.
                // If you use an API server, in `prod` mode, you will need to enable CORS
                // (see the `jhipster.cors` common JHipster property in the `application-*.yml` configurations)
                SERVER_API_URL: `''`
            }
        }),
        new CopyWebpackPlugin({
            patterns: [
                { from: './src/main/webapp/content/', to: 'content' },
                { from: './src/main/resources/public/images/favicon.ico', to: 'public/images/favicon.ico' },
                { from: './src/main/webapp/manifest.webapp', to: 'manifest.webapp' },
                { from: './src/main/webapp/robots.txt', to: 'robots.txt' },
            ],
        }),
        new MergeJsonWebpackPlugin({
            output: {
                groupBy: [
                    { pattern: "./src/main/webapp/i18n/en/*.json", fileName: "./i18n/en.json" },
                    { pattern: "./src/main/webapp/i18n/de/*.json", fileName: "./i18n/de.json" }
                ]
            }
        }),
        new HtmlWebpackPlugin({
            template: './src/main/webapp/index.html',
            chunks: ['polyfills', 'main', 'global'],
            chunksSortMode: 'manual',
            inject: 'body',
            'base': '/'
        }),
        new AngularCompilerPlugin({
            mainPath: utils.root('src/main/webapp/app/app.main.ts'),
            tsConfigPath: utils.root('tsconfig.app.json'),
            sourceMap: true
        }),
        new WorkboxPlugin.GenerateSW({
            clientsClaim: true,
            skipWaiting: true,
            runtimeCaching: [
                {
                    urlPattern: /\.(?:cc|js|html)$/,
                    handler: "StaleWhileRevalidate",
                    options: {
                        cacheName: "artemisCache",
                        expiration: {
                            maxAgeSeconds: 60 * 60 * 24
                        },
                        broadcastUpdate: {
                            channelName: "update-artemisCache"
                        }
                    }
                },
                {
                    urlPattern: /\.(?:png|jpg|svg|jpeg|gif|eot|ttf|woff|woff2)$/,
                    handler: "StaleWhileRevalidate",
                    options: {
                        cacheName: "assetCache",
                        broadcastUpdate: {
                            channelName: "update-assetCache"
                        }
                    }
                }
            ]
        })
    ]
});
