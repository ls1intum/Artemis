import { defaultPlugins } from '@hey-api/openapi-ts';
module.exports = {
    experimentalParser: true,
    input: '../server/application-server/openapi.yaml',
    output: {
        format: 'prettier',
        lint: 'eslint',
        path: 'src/app/core/modules/openapi',
    },
    plugins: [
        ...defaultPlugins,
        '@hey-api/schemas',
        {
            enums: 'javascript',
            name: '@hey-api/typescript',
        },
        '@hey-api/client-fetch',
    ],
};
