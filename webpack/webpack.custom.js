const MonacoWebpackPlugin = require('monaco-editor-webpack-plugin');
const { merge } = require('webpack-merge');

module.exports = async (config) => {
    config.plugins.push(
        new MonacoWebpackPlugin({
            languages: ['python, java, c'],
            features: [
                '!accessibilityHelp',
                '!bracketMatching',
                '!caretOperations',
                '!clipboard',
                '!codeAction',
                '!codelens',
                '!colorDetector',
                '!comment',
                '!contextmenu',
                '!coreCommands',
                '!cursorUndo',
                '!dnd',
                '!find',
                '!folding',
                '!fontZoom',
                '!format',
                '!gotoError',
                '!gotoLine',
                '!gotoSymbol',
                '!hover',
                '!iPadShowKeyboard',
                '!inPlaceReplace',
                '!inspectTokens',
                '!linesOperations',
                '!links',
                '!multicursor',
                '!parameterHints',
                '!quickCommand',
                '!quickOutline',
                '!referenceSearch',
                '!rename',
                '!smartSelect',
                '!snippets',
                '!suggest',
                '!toggleHighContrast',
                '!toggleTabFocusMode',
                '!transpose',
                '!wordHighlighter',
                '!wordOperations',
                '!wordPartOperations',
            ],
        }),
    );

    config.module.rules.push(
        {
            test: /\.css$/,
            use: ['style-loader', 'css-loader'],
        },
        {
            test: /\.ttf$/,
            type: 'asset/resource',
        },
    );

    config = merge(config);

    return config;
};
