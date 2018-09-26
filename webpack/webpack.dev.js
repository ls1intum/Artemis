const webpack = require('webpack');
const writeFilePlugin = require('write-file-webpack-plugin');
const webpackMerge = require('webpack-merge');
const BrowserSyncPlugin = require('browser-sync-webpack-plugin');
const WebpackNotifierPlugin = require('webpack-notifier');
const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin');
const path = require('path');
const sass = require('sass');

const utils = require('./utils.js');
const commonConfig = require('./webpack.common.js');

const ENV = 'development';

module.exports = webpackMerge(commonConfig({ env: ENV }), {
    devtool: 'eval-source-map',
    devServer: {
        contentBase: './build/www',
        proxy: [{
            context: [
                /* jhipster-needle-add-entity-to-webpack - JHipster will add entity api paths here */
                '/api',
                '/management',
                '/v2/api-docs',
                '/h2-console',
                '/auth'
            ],
            target: 'http://127.0.0.1:8080',
            secure: false,
            headers: { host: 'localhost:9000' }
        },{
            context: [
                '/websocket'
            ],
            target: 'ws://127.0.0.1:8080',
            ws: true
        }],
        watchOptions: {
            ignored: /node_modules/
        }
    },
    entry: {
        polyfills: './src/main/webapp/app/polyfills',
        global: './src/main/webapp/content/scss/global.scss',
        bower: './src/main/webapp/ng1/bower-deps',
        ng1: './src/main/webapp/ng1/app.module',
        main: './src/main/webapp/app/app.main'
    },
    output: {
        path: utils.root('build/www'),
        filename: 'app/[name].bundle.js',
        chunkFilename: 'app/[id].chunk.js'
    },
    module: {
        rules: [{
            test: /\.ts$/,
            enforce: 'pre',
            loaders: 'tslint-loader',
            exclude: ['node_modules', new RegExp('reflect-metadata\\' + path.sep + 'Reflect\\.ts')]
        },
            {
                test: /\.ts$/,
                use: [
                    'angular2-template-loader',
                    {
                        loader: 'cache-loader',
                        options: {
                            cacheDirectory: path.resolve('build/cache-loader')
                        }
                    },
                    {
                        loader: 'thread-loader',
                        options: {
                            // there should be 1 cpu for the fork-ts-checker-webpack-plugin
                            workers: require('os').cpus().length - 1
                        }
                    },
                    {
                        loader: 'ts-loader',
                        options: {
                            transpileOnly: true,
                            happyPackMode: true
                        }
                    },
                    'angular-router-loader'
                ],
                exclude: ['node_modules']
            },
            {
                test: /\.scss$/,
                use: ['to-string-loader', 'css-loader', {
                    loader: 'sass-loader',
                    options: { implementation: sass }
                }],
                exclude: /(vendor\.scss|global\.scss)/
            },
            {
                test: /(vendor\.scss|global\.scss)/,
                use: ['style-loader', 'css-loader', 'postcss-loader', {
                    loader: 'sass-loader',
                    options: { implementation: sass }
                }]
            },
            {
                test: /\.css$/,
                loaders: ['to-string-loader', 'css-loader'],
                exclude: /(vendor\.css|global\.css)/
            },
            {
                test: /(vendor\.css|global\.css)/,
                loaders: ['style-loader', 'css-loader']
            }]
    },
    plugins: [
        new ForkTsCheckerWebpackPlugin(),
        new BrowserSyncPlugin({
            host: 'localhost',
            port: 9000,
            proxy: {
                target: 'http://localhost:9060',
                ws: true
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
            title: 'JHipster',
            contentImage: path.join(__dirname, 'logo-jhipster.png')
        })
    ],
    mode: 'development'
});
