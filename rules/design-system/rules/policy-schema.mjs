// The option shape every class rule shares, so a reader of one config
// knows them all.
export const entriesSchema = { type: 'array', items: { type: 'string' } };
const message = { type: 'string', maxLength: 500 };
export const contractsSchema = {
    type: 'array',
    items: {
        type: 'object',
        properties: {
            pattern: { type: 'string' },
            allow: entriesSchema,
            deny: entriesSchema,
            message,
        },
        required: ['pattern'],
        additionalProperties: false,
    },
};
export const policySchema = {
    allow: entriesSchema,
    deny: entriesSchema,
    contracts: contractsSchema,
    message,
};
