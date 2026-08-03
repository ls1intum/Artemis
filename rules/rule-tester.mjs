/**
 * Shared RuleTester setup for the custom ESLint rules in this directory.
 *
 * The template rules visit `TextAttribute` / `BoundAttribute` nodes produced by the Angular HTML template
 * parser, so their tests must run RuleTester with `@angular-eslint/template-parser`; the TypeScript rules
 * visit class members and decorators and need `@typescript-eslint/parser` instead. Hence the two factories
 * below — both share the ajv workaround documented next.
 *
 * Environment workaround
 * ----------------------
 * ESLint 10 ships a JSON-Schema draft-04 meta-schema (legacy `id` keyword, boolean `exclusiveMinimum`,
 * …) that its RuleTester compiles through `lib/shared/ajv.js`. ESLint declares `ajv@^6`, but in this
 * repo's pnpm tree only `ajv@8` is present, so ESLint's internal `require("ajv")` resolves to ajv@8,
 * whose strict draft-2020 codegen rejects the draft-04 meta-schema. This breaks RuleTester (but not the
 * ESLint CLI, which never compiles that meta-schema). Until ESLint's ajv@6 dependency is materialised,
 * we neutralise the meta-schema compilation on the *exact* ajv module instance ESLint resolves — the
 * actual rule-options validation (our rules declare `schema: []`) still runs on ajv@8's native dialect.
 */
import { createRequire } from 'node:module';
import * as templateParser from '@angular-eslint/template-parser';
import * as tsParser from '@typescript-eslint/parser';

const require = createRequire(import.meta.url);
// Resolve ajv the way ESLint does: pnpm's hoisted symlink and the `.pnpm` realpath are distinct module
// instances, so patching via this file's own require would miss the copy ESLint actually uses.
const eslintRequire = createRequire(require.resolve('eslint'));
const ajvModule = eslintRequire('ajv');
const AjvClass = ajvModule.default ?? ajvModule;

// Idempotent: a marker prevents double-patching if this module is imported by multiple specs.
if (!AjvClass.__artemisRuleTesterPatched) {
    AjvClass.prototype.addMetaSchema = function () {
        return this;
    };
    AjvClass.prototype.validateSchema = function () {
        return true;
    };
    AjvClass.__artemisRuleTesterPatched = true;
}

const { RuleTester } = await import('eslint');

/** A RuleTester preconfigured with the Angular template parser. */
export function createTemplateRuleTester() {
    return new RuleTester({ languageOptions: { parser: templateParser } });
}

/**
 * A RuleTester preconfigured with the TypeScript parser, for the rules in this directory that visit
 * TypeScript AST nodes (class members, decorators, …) rather than Angular template nodes.
 *
 * No `project`/`projectService` is configured on purpose: these rules are purely syntactic, so type
 * information is unnecessary and requiring a tsconfig would make the tests depend on the client build.
 */
export function createTypeScriptRuleTester() {
    return new RuleTester({
        languageOptions: {
            parser: tsParser,
            parserOptions: { ecmaVersion: 'latest', sourceType: 'module' },
        },
    });
}
