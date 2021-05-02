const webpack = require('webpack');
const { merge } = require('webpack-merge');
const BrowserSyncPlugin = require('browser-sync-webpack-plugin');
const FriendlyErrorsWebpackPlugin = require('friendly-errors-webpack-plugin');
const SimpleProgressWebpackPlugin = require('simple-progress-webpack-plugin');
const WebpackNotifierPlugin = require('webpack-notifier');
const path = require('path');
const sass = require('sass');
const ESLintPlugin = require('eslint-webpack-plugin');

import { commonConfig } from './webpack.common';
import { root } from './utils';

module.exports = (options: any) => merge(commonConfig({ env: '"development"' }), {
    devtool: 'eval-source-map',
    devServer: {
        contentBase: './build/resources/main/static/',
        proxy: [{
            context: [
                '/',
            ],
            target: `http${options.tls ? 's' : ''}://${options.docker ? 'artemis-server' : 'localhost'}:8080`,
            secure: false,
            changeOrigin: options.tls,
            headers: { host: 'localhost:9000' }
        }, {
            context: [
                '/websocket'
            ],
            target: 'ws://127.0.0.1:8080',
            ws: true
        }],
        stats: options.stats,
        watchOptions: {
            ignored: 'node_modules/**'
        },
        https: options.tls,
        historyApiFallback: true
    },
    entry: {
        global: './src/main/webapp/content/scss/global.scss',
        main: './src/main/webapp/app/app.main'
    },
    output: {
        path: root('build/resources/main/static/'),
        filename: 'app/[name].bundle.js',
        chunkFilename: 'app/[id].chunk.js'
    },
    module: {
        rules: [{
            test: /\.scss$/,
            use: [
                'to-string-loader',
                {
                    loader: 'css-loader',
                    options: { esModule: false }
                },
                'postcss-loader',
                {
                    loader: 'sass-loader',
                    options: { implementation: sass }
                }
            ],
            exclude: /(vendor\.scss|global\.scss)/
        },
        {
            test: /(vendor\.scss|global\.scss)/,
            use: [
                'style-loader',
                {
                    loader: 'css-loader',
                    options: { esModule: false }
                },
                'postcss-loader',
                {
                    loader: 'sass-loader',
                    options: { implementation: sass }
                }
            ]
        }]
    },
    stats: process.env.JHI_DISABLE_WEBPACK_LOGS ? 'none' : options.stats,
    plugins: [
        process.env.JHI_DISABLE_WEBPACK_LOGS
            ? null
            : new SimpleProgressWebpackPlugin({
                format: options.stats === 'minimal' ? 'compact' : 'expanded'
              }),
        new ESLintPlugin(),
        new FriendlyErrorsWebpackPlugin(),
        new BrowserSyncPlugin({
            https: options.tls,
            host: 'localhost',
            port: 9000,
            proxy: {
                target: `http${options.tls ? 's' : ''}://localhost:9060`,
                ws: true,
                proxyOptions: {
                    changeOrigin: false  // pass the Host header to the server unchanged  https://github.com/Browsersync/browser-sync/issues/430
                }
            },
            socket: {
                clients: {
                    heartbeatTimeout: 60000
                }
            }
        }, {
            reload: false
        }),
        new webpack.ContextReplacementPlugin(
            /angular([\\/])core([\\/])/,
            path.resolve(__dirname, './src/main/webapp')
        ),
        new webpack.WatchIgnorePlugin({
            paths: [root('src/test')],
        }),
        new WebpackNotifierPlugin({
            title: 'Artemis'
        })
    ].filter(Boolean),
    mode: 'development'
});
