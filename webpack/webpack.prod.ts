const webpack = require('webpack');
const { merge } = require('webpack-merge');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const OptimizeCSSAssetsPlugin = require('css-minimizer-webpack-plugin');
const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;
const MomentLocalesPlugin = require('moment-locales-webpack-plugin');
const TerserPlugin = require('terser-webpack-plugin');
const WorkboxPlugin = require('workbox-webpack-plugin');

import { commonConfig } from './webpack.common';
import { root } from './utils';

const sass = require('sass');

module.exports = merge(commonConfig({ env: '"production"' }), {
    // Enable source maps. Please note that this will slow down the build.
    devtool: 'source-map',
    entry: {
        global: './src/main/webapp/content/scss/global.scss',
        main: './src/main/webapp/app/app.main'
    },
    output: {
        path: root('build/resources/main/static/'),
        filename: 'app/[name].[fullhash].bundle.js',
        chunkFilename: 'app/[id].[fullhash].chunk.js'
    },
    module: {
        rules: [
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
                {
                    loader: MiniCssExtractPlugin.loader,
                    options: {
                        publicPath: '../'
                    }
                },
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
        },
        {
            test: /\.css$/,
            use: [
                'to-string-loader',
                {
                    loader: 'css-loader',
                    options: { esModule: false }
                }
            ],
            exclude: /(vendor\.css|global\.css)/
        },
        {
            test: /(vendor\.css|global\.css)/,
            use: [
                {
                    loader: MiniCssExtractPlugin.loader,
                    options: {
                        publicPath: '../'
                    }
                },
                {
                    loader: 'css-loader',
                    options: { esModule: false }
                },
                'postcss-loader'
            ]
        }]
    },
    optimization: {
        runtimeChunk: false,
        minimizer: [
            new TerserPlugin({
                parallel: 2,
                terserOptions: {
                    ecma: 6,
                    ie8: false,
                    toplevel: true,
                    module: true,
                    compress: {
                        dead_code: true,
                        warnings: false,
                        properties: true,
                        drop_debugger: true,
                        conditionals: true,
                        booleans: true,
                        loops: true,
                        unused: true,
                        toplevel: true,
                        if_return: true,
                        inline: true,
                        join_vars: true,
                        ecma: 6,
                        module: true,
                    },
                    output: {
                        comments: false,
                        beautify: false,
                        indent_level: 2,
                        ecma: 6
                    },
                    mangle: {
                        module: true,
                        toplevel: true
                    }
                }
            }),
            new OptimizeCSSAssetsPlugin({})
        ]
    },
    plugins: [
        new MiniCssExtractPlugin({
            // Options similar to the same options in webpackOptions.output, both options are optional
            filename: 'content/[name].[contenthash].css',
            chunkFilename: '[id].css'
        }),
        new MomentLocalesPlugin({
            localesToKeep: ['en', 'de']
        }),
        new BundleAnalyzerPlugin({
            analyzerMode: 'static',
            openAnalyzer: false,
            generateStatsFile: true}),
        new webpack.LoaderOptionsPlugin({
            minimize: true,
            debug: false
        }),
        new WorkboxPlugin.GenerateSW({
            clientsClaim: true,
            skipWaiting: true,
        })
    ],
    mode: 'production'
});
