const { merge } = require('webpack-merge');
const path = require('path');
const { hashElement } = require('folder-hash');
const MergeJsonWebpackPlugin = require('merge-jsons-webpack-plugin');
const BrowserSyncPlugin = require('browser-sync-webpack-plugin');
const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;
const WebpackNotifierPlugin = require('webpack-notifier');
const ESLintPlugin = require('eslint-webpack-plugin');

const proxyConfig = require('./proxy.conf');

module.exports = async (config, options, targetOptions) => {
    // PLUGINS
    if (config.mode === 'development') {
        config.plugins.push(
            new ESLintPlugin({
                extensions: ['js', 'ts'],
            }),
            new WebpackNotifierPlugin({
                title: 'Artemis',
                contentImage: path.join(__dirname, 'src/main/resources/public/images/logo.png'),
            })
        );
    }

    // configuring proxy for back end service
    const tls = Boolean(config.devServer && config.devServer.https);
    if (config.devServer) {
        config.devServer.proxy = proxyConfig({ tls });
    }
    if (targetOptions.target === 'serve' || config.watch) {
        config.plugins.push(
            new BrowserSyncPlugin(
                {
                    host: 'localhost',
                    port: 9000,
                    notify: false,
                    https: tls,
                    proxy: {
                        target: `http${tls ? 's' : ''}://localhost:${targetOptions.target === 'serve' ? '4200' : '8080'}`,
                        ws: true,
                        proxyOptions: {
                            changeOrigin: false, //pass the Host header to the server unchanged  https://github.com/Browsersync/browser-sync/issues/430
                        },
                    },
                    socket: {
                        clients: {
                            heartbeatTimeout: 60000,
                        },
                    },
                },
                {
                    reload: targetOptions.target === 'build', // enabled for build --watch
                }
            )
        );
    }

    if (config.mode === 'production') {
        config.plugins.push(
            new BundleAnalyzerPlugin({
                analyzerMode: 'static',
                openAnalyzer: false,
                // Webpack statistics in target folder
                reportFilename: '../stats.html',
            })
        );
    }

    config.plugins.push(
        new MergeJsonWebpackPlugin({
            output: {
                groupBy: [
                    { pattern: './src/main/webapp/i18n/en/*.json', fileName: './i18n/en.json' },
                    { pattern: './src/main/webapp/i18n/de/*.json', fileName: './i18n/de.json' },
                ],
            },
        }),
    );

    config = merge(
        config
    );

    return config;
};
