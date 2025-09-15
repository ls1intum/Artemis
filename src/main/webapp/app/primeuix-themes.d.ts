/**
 * Module declaration for @primeuix/themes subpath imports.
 *
 * Why this is needed:
 * - Our tsconfig currently uses "moduleResolution": "node", which does not understand modern "exports" maps in package.json (used by @primeuix/themes).
 * - As a result, TypeScript cannot resolve imports like: import Aura from '@primeuix/themes/aura';
 * - This declaration is a workaround to still enable the import
 */
declare module '@primeuix/themes/*' {
    const preset: any;
    export default preset;
}
