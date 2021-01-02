const webpack = require('webpack');
const writeFilePlugin = require('write-file-webpack-plugin');
const { merge } = require('webpack-merge');
const BrowserSyncPlugin = require('browser-sync-webpack-plugin');
const FriendlyErrorsWebpackPlugin = require('friendly-errors-webpack-plugin');
const SimpleProgressWebpackPlugin = require('simple-progress-webpack-plugin');
const WebpackNotifierPlugin = require('webpack-notifier');
const path = require('path');
const sass = require('sass');

const utils = require('./utils.js');
const commonConfig = require('./webpack.common.js');

const ENV = 'development';

module.exports = (options) => merge(commonConfig({ env: ENV }), {
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
        },{
            context: [
                '/websocket'
            ],
            target: 'ws://127.0.0.1:8080',
            ws: true
        }],
        stats: options.stats,
        watchOptions: {
            ignored: /node_modules/
        },
        https: options.tls,
        historyApiFallback: true
    },
    entry: {
        global: './src/main/webapp/content/scss/global.scss',
        main: './src/main/webapp/app/app.main'
    },
    output: {
        path: utils.root('build/resources/main/static/'),
        filename: 'app/[name].bundle.js',
        chunkFilename: 'app/[id].chunk.js'
    },
    module: {
        rules: [{
            test: /\.(j|t)s$/,
            enforce: 'pre',
            loader: 'eslint-loader',
            exclude: /node_modules/
        },
        {
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
        new FriendlyErrorsWebpackPlugin(),
        new BrowserSyncPlugin({
            https: options.tls,
            host: 'localhost',
            port: 9000,
            proxy: {
                target: `http${options.tls ? 's' : ''}://localhost:9060`,
                ws: true,
                proxyOptions: {
                    changeOrigin: false  //pass the Host header to the server unchanged  https://github.com/Browsersync/browser-sync/issues/430
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
            /angular(\\|\/)core(\\|\/)/,
            path.resolve(__dirname, './src/main/webapp')
        ),
        new writeFilePlugin(),
        new webpack.WatchIgnorePlugin([
            utils.root('src/test'),
        ]),
        new WebpackNotifierPlugin({
            title: 'Artemis'
        })
    ].filter(Boolean),
    mode: 'development'
});
