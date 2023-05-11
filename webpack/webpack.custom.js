const webpack = require('webpack');
const { merge } = require('webpack-merge');
const path = require('path');
const { hashElement } = require('folder-hash');
const MergeJsonWebpackPlugin = require('merge-jsons-webpack-plugin');
const BrowserSyncPlugin = require('browser-sync-webpack-plugin');
const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;
const WebpackNotifierPlugin = require('webpack-notifier');
const ESLintPlugin = require('eslint-webpack-plugin');

const environment = require('./environment');
const proxyConfig = require('./proxy.conf');

module.exports = async (config, options, targetOptions) => {
    const languagesHash = await hashElement(path.resolve(__dirname, '../src/main/webapp/i18n'), {
        algo: 'md5',
        encoding: 'hex',
        files: { include: ['*.json'] },
    });

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
        new webpack.DefinePlugin({
            I18N_HASH: JSON.stringify(languagesHash.hash),
            // APP_VERSION is passed as an environment variable from the Gradle / Maven build tasks.
            __VERSION__: JSON.stringify(environment.__VERSION__),
            __DEBUG_INFO_ENABLED__: environment.__DEBUG_INFO_ENABLED__ || config.mode === 'development',
        }),
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
