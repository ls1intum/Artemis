// Class-group trie adapted from shadcn lint; the grammar is pinned through cn/config.
import { defaultConfig } from 'cn/config';
import { GROUP_CATEGORY } from './categories.mjs';
import { splitVariants } from './classes.mjs';
import * as cnValidators from './validators.mjs';
const config = defaultConfig();
export function resolveCnConfig() {
    return config;
}
const validatorByName = cnValidators;
// tailwind-merge's prefix; two dots cannot collide with a plugin group.
const ARBITRARY_PROPERTY_PREFIX = 'arbitrary..';
function createNode() {
    const node = { next: new Map(), validators: null, group: null };
    return node;
}
function isMarker(def, key) {
    const keys = Object.keys(def);
    return keys.length === 1 && keys[0] === key && typeof def[key] === 'string';
}
function getPart(node, path) {
    for (const part of path.split('-')) {
        let next = node.next.get(part);
        if (!next) {
            next = createNode();
            node.next.set(part, next);
        }
        node = next;
    }
    return node;
}
function buildTrie(config) {
    const root = createNode();
    const addValidator = (node, test, group) => {
        (node.validators ??= []).push({ test, group });
    };
    const process = (def, node, group) => {
        if (typeof def === 'string') {
            const target = def === '' ? node : getPart(node, def);
            target.group = group;
            return;
        }
        if (typeof def === 'function') {
            // tailwind-merge passes these as functions; cn uses the markers.
            if (def.isThemeGetter === true) {
                const getTheme = def;
                for (const inner of getTheme(config.theme)) process(inner, node, group);
                return;
            }
            addValidator(node, def, group);
            return;
        }
        if (isMarker(def, '$t')) {
            for (const inner of config.theme[def.$t] ?? []) {
                process(inner, node, group);
            }
            return;
        }
        if (isMarker(def, '$v')) {
            const name = def.$v;
            const test = validatorByName[name];
            if (!test) throw new Error(`cn-classifier: unknown validator "${name}"`);
            addValidator(node, test, group);
            return;
        }
        for (const [key, value] of Object.entries(def)) {
            const child = getPart(node, key);
            for (const inner of value) process(inner, child, group);
        }
    };
    for (const [group, defs] of Object.entries(config.classGroups)) {
        for (const def of defs) process(def, root, group);
    }
    return root;
}
// tailwind-merge's search order: exact parts as deep as they go, then
// validators on the tail, from the deepest node back up.
function walk(parts, start, root) {
    const path = [root];
    let node = root;
    let index = start;
    while (index < parts.length) {
        const next = node.next.get(parts[index]);
        if (!next) break;
        node = next;
        path.push(node);
        index++;
    }
    let level = path.length - 1;
    if (index === parts.length) {
        if (node.group) return node.group;
        // An exact match with no group has an empty tail to validate.
        level--;
    }
    for (; level >= 0; level--) {
        const validators = path[level].validators;
        if (!validators) continue;
        const rest = parts.slice(start + level).join('-');
        for (const { test, group } of validators) {
            if (test(rest)) return group;
        }
    }
    return null;
}
function arbitraryPropertyGroup(base) {
    const content = base.slice(1, -1);
    const colon = content.indexOf(':');
    return colon > 0 ? ARBITRARY_PROPERTY_PREFIX + content.slice(0, colon) : null;
}
// Only a slash outside brackets starts a modifier like bg-primary/90.
function postfixIndex(base) {
    let depth = 0;
    let index = -1;
    for (let i = 0; i < base.length; i++) {
        const char = base[i];
        if (char === '[' || char === '(') depth++;
        else if (char === ']' || char === ')') depth--;
        else if (char === '/' && depth === 0) index = i;
    }
    return index;
}
// Tailwind still generates these utilities under the names they had in
// Tailwind 3, so a project that has not renamed them is writing real
// classes. cn's config carries the current names only, and reads
// `decoration-clone` as a text-decoration color, so the rename happens
// before the lookup.
const RENAMED = new Map([
    ['flex-grow', 'grow'],
    ['flex-shrink', 'shrink'],
    ['overflow-ellipsis', 'text-ellipsis'],
    ['decoration-slice', 'box-decoration-slice'],
    ['decoration-clone', 'box-decoration-clone'],
]);
const RENAMED_SCALE = /^flex-(grow|shrink)-(.+)$/;
function currentName(base) {
    const renamed = RENAMED.get(base);
    if (renamed) return renamed;
    const scale = RENAMED_SCALE.exec(base);
    return scale ? `${scale[1]}-${scale[2]}` : base;
}
export function createClassifier(config = resolveCnConfig()) {
    const root = buildTrie(config);
    const postfixLookupGroups = new Set(config.postfixLookupClassGroups ?? []);
    const lookup = (base) => {
        if (base.startsWith('[') && base.endsWith(']')) {
            return arbitraryPropertyGroup(base);
        }
        const parts = base.split('-');
        return walk(parts, parts[0] === '' && parts.length > 1 ? 1 : 0, root);
    };
    // A few thousand distinct tokens, repeated everywhere.
    const memo = new Map();
    const groupOf = (token) => {
        const hit = memo.get(token);
        if (hit !== undefined) return hit;
        if (memo.size > 50_000) memo.clear();
        const group = classify(token);
        memo.set(token, group);
        return group;
    };
    const classify = (token) => {
        let base = splitVariants(token.trim()).base;
        if (base.endsWith('!')) base = base.slice(0, -1);
        else if (base.startsWith('!')) base = base.slice(1);
        if (!base) return null;
        base = currentName(base);
        const slash = postfixIndex(base);
        if (slash === -1) return lookup(base);
        // tailwind-merge's order: without the modifier first, so text-sm/6
        // stays font-size instead of reaching text-color's catch-all.
        const group = lookup(base.slice(0, slash));
        if (group && postfixLookupGroups.has(group)) {
            return lookup(base) ?? group;
        }
        return group ?? lookup(base);
    };
    return { groupOf };
}
const classifierByConfig = new WeakMap();
// Groups the category table does not know classify as layout, which is
// the permissive direction: worth a warning rather than silence.
export function unknownGroups(config) {
    return Object.keys(config.classGroups).filter((group) => !(group in GROUP_CATEGORY));
}
// One trie per distinct config, built lazily.
export function classifierFor() {
    let classifier = classifierByConfig.get(config);
    if (!classifier) {
        const unknown = unknownGroups(config);
        if (unknown.length) throw new Error(`Unclassified cn groups: ${unknown.join(', ')}. Update the design-system categories before upgrading cn.`);
        classifier = createClassifier(config);
        classifierByConfig.set(config, classifier);
    }
    return classifier;
}

export function groupOf(token, fromFile) {
    return classifierFor(fromFile).groupOf(token);
}
