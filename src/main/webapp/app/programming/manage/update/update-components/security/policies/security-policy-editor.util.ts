import dayjs from 'dayjs/esm';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';
import {
    SecurityCommandPermission,
    SecurityFilePermission,
    SecurityNetworkPermission,
    SecurityPackagePermission,
    SecurityPolicyDocument,
    SecurityPolicyFile,
    SecurityPreset,
    SecurityTestBehavior,
    SecurityThreadPermission,
} from 'app/programming/shared/entities/security-policy.model';

/**
 * Pure helpers of the policy editor: identifying permission rows, telling committed rows from unsaved ones, counting and summarizing the unsaved changes,
 * rebasing them onto a newer commit, and the line diff of the review. Nothing here talks to the server.
 */

/** The key of a text that names a count: `<key>.one` for exactly one, `<key>.other` otherwise. The text receives the count as `count`. */
export function countKey(key: string, count: number): string {
    return `${key}.${count === 1 ? 'one' : 'other'}`;
}

/** The categories of the Allow Resource Access box, in the order of its tabs. */
export type ResourceKind = 'files' | 'network' | 'commands' | 'threads' | 'packages';

export const RESOURCE_KINDS: readonly ResourceKind[] = ['files', 'network', 'commands', 'threads', 'packages'];

/** The row type of each category. */
export interface ResourceRowByKind {
    files: SecurityFilePermission;
    network: SecurityNetworkPermission;
    commands: SecurityCommandPermission;
    threads: SecurityThreadPermission;
    packages: SecurityPackagePermission;
}

export type ResourceRow = ResourceRowByKind[ResourceKind];

/** Where a row comes from, as the Source column shows it: a preset (locked), the committed policy, or an unsaved change. */
export enum RowSource {
    PRESET = 'PRESET',
    COMMITTED = 'COMMITTED',
    /** Added since the last commit. */
    NEW = 'NEW',
    /** Committed with other permissions; the committed ones are enforced until the commit. */
    CHANGED = 'CHANGED',
    /** Deleted since the last commit; still enforced until the commit. */
    REMOVED = 'REMOVED',
}

/** A row of a category with its source and its identity. */
export interface SourcedRow<K extends ResourceKind = ResourceKind> {
    key: string;
    row: ResourceRowByKind[K];
    source: RowSource;
    /** The preset that adds a locked row. */
    preset?: string;
}

/** The tabs of the Configure Test Behaviour box, in their order. Security feedback has no policy field yet. */
export type BehaviourTab = 'securityFeedback' | 'standardFeedback' | 'visibility' | 'timeout' | 'output';

export const BEHAVIOUR_TABS: readonly BehaviourTab[] = ['securityFeedback', 'standardFeedback', 'visibility', 'timeout', 'output'];

/** The categories of the Configure Test Behaviour box that map to a policy field. */
export type BehaviourCategory = keyof SecurityTestBehavior;

export const BEHAVIOUR_CATEGORIES: readonly BehaviourCategory[] = ['privilegedExceptions', 'hiddenTests', 'strictTimeout', 'outputMirroring'];

/** The policy field of each tab; undefined for a tab without one. */
export const BEHAVIOUR_CATEGORY_OF_TAB: Record<BehaviourTab, BehaviourCategory | undefined> = {
    securityFeedback: undefined,
    standardFeedback: 'privilegedExceptions',
    visibility: 'hiddenTests',
    timeout: 'strictTimeout',
    output: 'outputMirroring',
};

/** A policy in the editor: what is committed and what the unsaved changes make of it. */
export interface EditorPolicy {
    /** A stable id for tracking the policy in the client; the committed path for committed policies. */
    id: string;
    /** The policy as loaded; absent for a new policy. */
    file?: SecurityPolicyFile;
    /** The committed document; absent for a new or a read-only policy. */
    base?: SecurityPolicyDocument;
    /** The document with the unsaved changes; absent for a read-only policy. Its test classes are the classes of the tests plus the extra classes. */
    working?: SecurityPolicyDocument;
    /** The committed tests of the policy, see {@link SecurityPolicyFile.assignedTests}. */
    baseTests: string[];
    /** The tests with the unsaved changes. */
    tests: string[];
    /** The committed test classes that no assigned test needs, e.g. helpers. */
    baseExtraClasses: string[];
    /** The extra test classes with the unsaved changes. */
    extraClasses: string[];
    /** The name with the unsaved changes; it determines the file name. */
    name: string;
    /**
     * Whether a new policy got a language configuration of its own, e.g. from an imported file, so it keeps it instead of following the committed policies
     * when dependency scanning is switched.
     */
    ownLanguageConfiguration?: boolean;
}

/** The unsaved changes of a policy, by kind, as the commit bar lists them. */
export interface PolicyChangeSummary {
    /** Rows added, changed or removed in Allow Resource Access. */
    restrictions: number;
    /** The rows changed per category. */
    restrictionsByKind: Record<ResourceKind, number>;
    /** Tabs changed in Configure Test Behaviour. */
    behaviours: number;
    /** Tests assigned or released. */
    testCases: number;
    /** Extra test classes declared or removed. */
    testClasses: number;
    /** A changed name, preset or other setting of the file, e.g. one an import changed, see {@link FILE_SETTINGS}. */
    settings: number;
    /** Whether the policy is not committed yet; it counts once for itself. */
    isNew: boolean;
    total: number;
}

/**
 * The settings of a policy file that no part of the editor shows but an import can change: they count as changed settings and survive a reload like the
 * other unsaved changes.
 */
export const FILE_SETTINGS = ['languageConfiguration', 'supervisedPackage', 'mainClass', 'timeouts'] as const;

/** @returns the settings of {@link FILE_SETTINGS} that differ between the two documents */
export function changedFileSettings(base: SecurityPolicyDocument | undefined, working: SecurityPolicyDocument): (typeof FILE_SETTINGS)[number][] {
    return FILE_SETTINGS.filter((setting) => !isSameValue(base?.[setting], working[setting]));
}

/** Every value of Java's {@code TimeUnit}, smallest first; a policy may use any of them. */
const TIME_UNITS: readonly string[] = ['NANOSECONDS', 'MICROSECONDS', 'MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS', 'DAYS'];

/** The time units that suit the timeout of a test case; nanoseconds, microseconds and days do not. */
const OFFERED_TIME_UNITS: readonly string[] = ['MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS'];

/**
 * @param inUse the units the policy uses; a unit that is not offered stays selectable while a policy uses it, so its value is shown as it is
 * @returns the time units a timeout field offers, smallest first
 */
export function offeredTimeUnits(inUse: readonly (string | undefined)[]): string[] {
    return TIME_UNITS.filter((unit) => OFFERED_TIME_UNITS.includes(unit) || inUse.includes(unit));
}

/** The file extension of new policies, like the one Artemis generates. */
const POLICY_EXTENSION = '.yaml';

/** An argument that is written without quotes. */
const PLAIN_ARGUMENT = /^[^\s"\\]+$/;

/** Puts a backslash before the quote or backslash that the expression matched. */
const ESCAPED_CHARACTER = String.raw`\$1`;

/**
 * Writes the arguments of a command for the field: separated by spaces, and in double quotes if an argument is empty or contains a space, a quote or a
 * backslash, which a backslash escapes. {@link splitArguments} reads it back to the same arguments.
 */
export function joinArguments(argumentList: readonly string[]): string {
    return argumentList.map((argument) => (PLAIN_ARGUMENT.test(argument) ? argument : `"${argument.replace(/(["\\])/g, ESCAPED_CHARACTER)}"`)).join(' ');
}

/** A character that a backslash in the arguments field escapes: a quote, a backslash or a space. */
const ESCAPED_IN_ARGUMENTS = /["\\\s]/;

/**
 * Splits the field into the arguments, which Ares compares exactly and in this order: spaces separate them unless they are in double quotes, and a
 * backslash takes a following quote, backslash or space literally. Before any other character it is a backslash of the argument, e.g. in {@code \d+}.
 *
 * @returns the arguments; undefined if a quote is not closed
 */
export function splitArguments(text: string): string[] | undefined {
    const argumentList: string[] = [];
    let current = '';
    let started = false;
    let quoted = false;
    for (let index = 0; index < text.length; index++) {
        const character = text[index];
        if (character === '\\' && index + 1 < text.length && ESCAPED_IN_ARGUMENTS.test(text[index + 1])) {
            current += text[++index];
            started = true;
        } else if (character === '"') {
            quoted = !quoted;
            started = true;
        } else if (!quoted && /\s/.test(character)) {
            if (started) {
                argumentList.push(current);
            }
            current = '';
            started = false;
        } else {
            current += character;
            started = true;
        }
    }
    if (quoted) {
        return undefined;
    }
    if (started) {
        argumentList.push(current);
    }
    return argumentList;
}

/** Separates the test class from the test case in an entry of an assignment. */
export const TEST_CASE_SEPARATOR = '#';

/** @returns the identity of a row within its category; two rows with the same key cannot both exist. */
export function rowKey<K extends ResourceKind>(kind: K, row: ResourceRowByKind[K]): string {
    switch (kind) {
        case 'files':
            return (row as SecurityFilePermission).path;
        case 'network': {
            const endpoint = row as SecurityNetworkPermission;
            return `${endpoint.host}:${endpoint.port}`;
        }
        case 'commands': {
            const command = row as SecurityCommandPermission;
            return joinArguments([command.command, ...(command.arguments ?? [])]);
        }
        case 'threads':
            return (row as SecurityThreadPermission).threadClass;
        case 'packages':
            return (row as SecurityPackagePermission).packageName;
        default:
            return '';
    }
}

/** @returns the rows of a category of a document or preset; empty if there are none */
export function rowsOf<K extends ResourceKind>(document: SecurityPolicyDocument | SecurityPreset | undefined, kind: K): ResourceRowByKind[K][] {
    return ((document?.[kind] as ResourceRowByKind[K][] | undefined) ?? []).slice();
}

/** @returns a copy of the document with the rows of a category replaced; the rows must be of that category */
export function withRows(document: SecurityPolicyDocument, kind: ResourceKind, rows: readonly ResourceRow[]): SecurityPolicyDocument {
    const copy = deepClone(document);
    const cloned = deepClone([...rows]);
    switch (kind) {
        case 'files':
            copy.files = cloned as SecurityFilePermission[];
            break;
        case 'network':
            copy.network = cloned as SecurityNetworkPermission[];
            break;
        case 'commands':
            copy.commands = cloned as SecurityCommandPermission[];
            break;
        case 'threads':
            copy.threads = cloned as SecurityThreadPermission[];
            break;
        case 'packages':
            copy.packages = cloned as SecurityPackagePermission[];
            break;
        default:
            break;
    }
    return copy;
}

/** Compares two values of the policy model, ignoring absent and empty fields like the server, which omits them. */
export function isSameValue(first: unknown, second: unknown): boolean {
    return JSON.stringify(normalized(first)) === JSON.stringify(normalized(second));
}

function normalized(value: unknown): unknown {
    if (Array.isArray(value)) {
        return value.map(normalized);
    }
    if (value !== null && typeof value === 'object') {
        const result: Record<string, unknown> = {};
        for (const key of Object.keys(value).sort((first, second) => first.localeCompare(second))) {
            const entry = normalized((value as Record<string, unknown>)[key]);
            const isEmpty = entry === undefined || entry === null || (Array.isArray(entry) && entry.length === 0) || entry === '';
            if (!isEmpty) {
                result[key] = entry;
            }
        }
        return Object.keys(result).length ? result : undefined;
    }
    return value;
}

/**
 * The rows a category shows: the rows the preset adds, locked, then the rows of the working document, each tagged new, changed or committed, and the
 * committed rows the unsaved changes deleted, so they can be restored. A row of the working document that the preset also adds is shown as a row of the
 * document, so it stays visible and can be edited or removed; the preset row comes back when it is removed.
 */
export function sourcedRows<K extends ResourceKind>(
    kind: K,
    base: SecurityPolicyDocument | undefined,
    working: SecurityPolicyDocument,
    preset: SecurityPreset | undefined,
): SourcedRow<K>[] {
    const committed = new Map(rowsOf(base, kind).map((row) => [rowKey(kind, row), row]));
    const workingRows = rowsOf(working, kind);
    const workingKeys = new Set(workingRows.map((row) => rowKey(kind, row)));
    const presetRows: SourcedRow<K>[] = rowsOf(preset, kind)
        .map((row) => ({ key: rowKey(kind, row), row, source: RowSource.PRESET, preset: preset?.label }))
        .filter((row) => !workingKeys.has(row.key));
    const presetKeys = new Set(presetRows.map((row) => row.key));
    const ownRows: SourcedRow<K>[] = workingRows.map((row) => {
        const key = rowKey(kind, row);
        return { key, row, source: rowSource(committed.get(key), row) };
    });
    const removedRows: SourcedRow<K>[] = [...committed.entries()]
        .filter(([key]) => !workingKeys.has(key) && !presetKeys.has(key))
        .map(([key, row]) => ({ key, row, source: RowSource.REMOVED }));
    return [...presetRows, ...ownRows, ...removedRows];
}

/** @returns whether a row of the working document is new, changed or as committed */
function rowSource(committedRow: unknown, row: unknown): RowSource {
    if (committedRow === undefined) {
        return RowSource.NEW;
    }
    return isSameValue(committedRow, row) ? RowSource.COMMITTED : RowSource.CHANGED;
}

/** @returns how many rows of a category were added, changed or removed */
export function countRowChanges(kind: ResourceKind, base: SecurityPolicyDocument | undefined, working: SecurityPolicyDocument): number {
    const before = new Map(rowsOf(base, kind).map((row) => [rowKey(kind, row), row]));
    const after = new Map(rowsOf(working, kind).map((row) => [rowKey(kind, row), row]));
    let changes = 0;
    after.forEach((row, key) => {
        if (!before.has(key) || !isSameValue(before.get(key), row)) {
            changes++;
        }
    });
    before.forEach((_, key) => {
        if (!after.has(key)) {
            changes++;
        }
    });
    return changes;
}

/** @returns the behaviour categories whose configuration differs */
export function changedBehaviours(base: SecurityPolicyDocument | undefined, working: SecurityPolicyDocument): BehaviourCategory[] {
    return BEHAVIOUR_CATEGORIES.filter((category) => !isSameValue(base?.testBehavior?.[category], working.testBehavior?.[category]));
}

/** @returns the entries that were added to and removed from a list */
export function listChanges(before: readonly string[], after: readonly string[]): { added: string[]; removed: string[] } {
    return { added: after.filter((entry) => !before.includes(entry)), removed: before.filter((entry) => !after.includes(entry)) };
}

/** @returns the fully qualified name of the test class of an entry of an assignment */
export function testClassOf(entry: string): string {
    const separator = entry.indexOf(TEST_CASE_SEPARATOR);
    return separator < 0 ? entry : entry.substring(0, separator);
}

/**
 * Assigns an entry again that was removed, without letting a whole test class and one of its test cases be assigned together.
 *
 * @returns the entries with the restored one
 */
export function withRestoredEntry(tests: readonly string[], entry: string): string[] {
    const testClass = testClassOf(entry);
    if (entry === testClass) {
        // A whole class stands for all of its test cases, so it replaces the entries of single ones; the server refuses both at once.
        return [...tests.filter((existing) => testClassOf(existing) !== testClass), entry];
    }
    return tests.includes(entry) || tests.includes(testClass) ? [...tests] : [...tests, entry];
}

/** @returns the test classes of the entries, each once, in their order */
export function classesOfTests(entries: readonly string[]): string[] {
    return [...new Set(entries.map(testClassOf))];
}

/**
 * The test classes a policy declares: the classes of its tests and its extra classes. Classes the committed document lists keep their position, so an
 * unchanged policy renders unchanged; new ones follow in alphabetical order.
 */
export function declaredTestClasses(previous: readonly string[], tests: readonly string[], extraClasses: readonly string[]): string[] {
    const declared = new Set([...classesOfTests(tests), ...extraClasses]);
    // A declaration of single test cases, e.g. pkg.Test#first, stays as it is while its class is still declared in no other way.
    const kept = previous.filter((testClass) => declared.has(testClass) || (testClass.includes(TEST_CASE_SEPARATOR) && declared.has(testClassOf(testClass))));
    const added = [...declared].filter((testClass) => !kept.includes(testClass)).sort((first, second) => first.localeCompare(second));
    return [...kept, ...added];
}

/** @returns the policy with other tests and extra classes; its document declares their classes */
export function withAssignment(policy: EditorPolicy, tests: readonly string[], extraClasses: readonly string[]): EditorPolicy {
    const working = policy.working ? cloneWith(policy.working, { testClasses: declaredTestClasses(policy.working.testClasses ?? [], tests, extraClasses) }) : undefined;
    return cloneWith(policy, { tests: [...tests], extraClasses: [...extraClasses], working });
}

/** @returns the name shown for a committed policy */
export function committedName(policy: EditorPolicy): string | undefined {
    return policy.file ? nameFromPath(policy.file.policyPath) : undefined;
}

/** Summarizes the unsaved changes of a policy; a read-only policy has none. */
export function summarizeChanges(policy: EditorPolicy): PolicyChangeSummary {
    const restrictionsByKind = { files: 0, network: 0, commands: 0, threads: 0, packages: 0 };
    const isNew = isNewPolicy(policy);
    if (!policy.working) {
        return { restrictions: 0, restrictionsByKind, behaviours: 0, testCases: 0, testClasses: 0, settings: 0, isNew, total: 0 };
    }
    const working = policy.working;
    for (const kind of RESOURCE_KINDS) {
        restrictionsByKind[kind] = countRowChanges(kind, policy.base, working);
    }
    const restrictions = RESOURCE_KINDS.reduce((sum, kind) => sum + restrictionsByKind[kind], 0);
    const behaviours = changedBehaviours(policy.base, working).length;
    const tests = listChanges(policy.baseTests, policy.tests);
    const testClasses = listChanges(policy.baseExtraClasses, policy.extraClasses);
    const settings = isNew ? 0 : changedSettings(policy, working);
    const testCases = tests.added.length + tests.removed.length;
    const classChanges = testClasses.added.length + testClasses.removed.length + changedDeclarations(policy, tests, testClasses);
    return {
        restrictions,
        restrictionsByKind,
        behaviours,
        testCases,
        testClasses: classChanges,
        settings,
        isNew,
        total: restrictions + behaviours + testCases + classChanges + settings + (isNew ? 1 : 0),
    };
}

/** Counts the changed settings of a committed policy: its name, its preset and the settings of its file permissions. */
function changedSettings(policy: EditorPolicy, working: SecurityPolicyDocument): number {
    const renamed = policy.name === committedName(policy) ? 0 : 1;
    const presetChanged = isSameValue(policy.base?.preset, working.preset) ? 0 : 1;
    return renamed + presetChanged + changedFileSettings(policy.base, working).length;
}

/**
 * Counts the declared test classes that changed on their own, e.g. an import that declares pkg.Test#second instead of pkg.Test#first while the tests
 * stay assigned. The declarations of changed tests and extra classes change with them and are counted with them already.
 */
function changedDeclarations(policy: EditorPolicy, tests: { added: string[]; removed: string[] }, extraClasses: { added: string[]; removed: string[] }): number {
    if (isNewPolicy(policy)) {
        return 0;
    }
    const counted = new Set([...tests.added, ...tests.removed, ...extraClasses.added, ...extraClasses.removed].map(testClassOf));
    const declarations = listChanges(policy.base?.testClasses ?? [], policy.working?.testClasses ?? []);
    return [...declarations.added, ...declarations.removed].filter((declaration) => !counted.has(testClassOf(declaration))).length;
}

/** @returns whether the policy is not committed yet */
export function isNewPolicy(policy: EditorPolicy): boolean {
    return !policy.file;
}

/** @returns whether the policy is shown read-only */
export function isReadOnlyPolicy(policy: EditorPolicy): boolean {
    return !policy.working;
}

/** @returns the policy as the editor starts it from a loaded file, without unsaved changes */
export function editorPolicyOf(file: SecurityPolicyFile): EditorPolicy {
    const tests = file.assignedTests ?? [];
    const classes = classesOfTests(tests);
    const extraClasses = (file.document?.testClasses ?? []).filter((testClass) => !testClass.includes(TEST_CASE_SEPARATOR) && !classes.includes(testClass));
    return {
        id: file.policyPath,
        file,
        base: file.document ? deepClone(file.document) : undefined,
        working: file.document ? deepClone(file.document) : undefined,
        baseTests: [...tests],
        tests: [...tests],
        baseExtraClasses: [...extraClasses],
        extraClasses: [...extraClasses],
        name: nameFromPath(file.policyPath),
    };
}

/**
 * Applies the unsaved changes made on top of one commit to the policy of a newer commit, so a reload keeps them: rows added, changed or removed by key,
 * changed behaviour categories, a changed preset or file setting, and the tests and extra classes that were assigned or released. Where the newer commit
 * changed the same thing, the unsaved change wins, and a whole test class replaces the entries of its test cases. If the newer file cannot be edited here,
 * the result is that read-only policy; the caller keeps the unsaved changes as a new policy.
 */
export function rebasePolicy(policy: EditorPolicy, file: SecurityPolicyFile): EditorPolicy {
    const fresh = editorPolicyOf(file);
    if (!policy.base || !policy.working || !fresh.base) {
        return fresh;
    }
    const oldBase = policy.base;
    const working = policy.working;
    let result = deepClone(fresh.base);
    for (const kind of RESOURCE_KINDS) {
        const before = new Map(rowsOf(oldBase, kind).map((row) => [rowKey(kind, row), row]));
        const after = new Map(rowsOf(working, kind).map((row) => [rowKey(kind, row), row]));
        const rows = new Map(rowsOf(result, kind).map((row) => [rowKey(kind, row), row]));
        after.forEach((row, key) => {
            if (!before.has(key) || !isSameValue(before.get(key), row)) {
                rows.set(key, row);
            }
        });
        before.forEach((_, key) => {
            if (!after.has(key)) {
                rows.delete(key);
            }
        });
        result = withRows(result, kind, [...rows.values()]);
    }
    const changedCategories = changedBehaviours(oldBase, working);
    if (changedCategories.length) {
        const behaviour: SecurityTestBehavior = deepClone(result.testBehavior ?? {});
        for (const category of changedCategories) {
            setBehaviour(behaviour, category, working.testBehavior?.[category]);
        }
        result = cloneWith(result, { testBehavior: behaviour });
    }
    if (!isSameValue(oldBase.preset, working.preset)) {
        result = cloneWith(result, { preset: working.preset });
    }
    for (const setting of changedFileSettings(oldBase, working)) {
        result = cloneWith(result, { [setting]: working[setting] });
    }
    // Declarations an import changed on their own, e.g. pkg.Test#second instead of pkg.Test#first; the assignment below keeps those of declared classes.
    result = cloneWith(result, { testClasses: applyListChanges(result.testClasses ?? [], listChanges(oldBase.testClasses ?? [], working.testClasses ?? [])) });
    const tests = withoutCoveredTestCases(applyListChanges(fresh.baseTests, listChanges(policy.baseTests, policy.tests)));
    const extraClasses = applyListChanges(fresh.baseExtraClasses, listChanges(policy.baseExtraClasses, policy.extraClasses));
    return withAssignment(cloneWith(fresh, { working: cloneWith(result, { policyPath: working.policyPath }), name: policy.name }), tests, extraClasses);
}

/** @returns the entries without the test cases of a class that is assigned as a whole, which the server refuses together with it */
export function withoutCoveredTestCases(tests: readonly string[]): string[] {
    return tests.filter((entry) => !entry.includes(TEST_CASE_SEPARATOR) || !tests.includes(testClassOf(entry)));
}

function applyListChanges(list: readonly string[], changes: { added: string[]; removed: string[] }): string[] {
    const result = list.filter((entry) => !changes.removed.includes(entry));
    changes.added.filter((entry) => !result.includes(entry)).forEach((entry) => result.push(entry));
    return result;
}

/** Sets or removes one category of a test behaviour, keeping the type of each category. */
export function setBehaviour<C extends BehaviourCategory>(behaviour: SecurityTestBehavior, category: C, value: SecurityTestBehavior[C] | undefined): void {
    if (value === undefined) {
        delete behaviour[category];
    } else {
        behaviour[category] = deepClone(value);
    }
}

/** @returns the file name of a new policy: its name in PascalCase with the YAML extension, e.g. {@code StressTests.yaml} for "Stress tests" */
export function policyFileName(name: string): string {
    const words = name
        .normalize('NFKD')
        .replace(/[^A-Za-z0-9 _-]/g, '')
        .split(/[\s_-]+/)
        .filter((word) => word.length > 0);
    if (!words.length) {
        return '';
    }
    return words.map((word) => word.charAt(0).toUpperCase() + word.slice(1)).join('') + POLICY_EXTENSION;
}

/**
 * @returns the path of a policy with a name: a committed policy keeps its path while its name is unchanged and is otherwise renamed in its directory; a
 *          new policy goes next to the first existing policy, or to the repository root
 */
export function policyPathFor(name: string, committedPath: string | undefined, existingPaths: readonly string[]): string {
    if (committedPath && name === nameFromPath(committedPath)) {
        return committedPath;
    }
    const fileName = policyFileName(name);
    if (!fileName) {
        return '';
    }
    const sibling = committedPath ?? existingPaths[0] ?? '';
    const slash = sibling.lastIndexOf('/');
    return slash >= 0 ? sibling.substring(0, slash + 1) + fileName : fileName;
}

/** @returns the name shown for a committed policy, derived from its file name, e.g. "Security Policy" for {@code SecurityPolicy.yaml} */
export function nameFromPath(policyPath: string): string {
    const fileName = policyPath.substring(policyPath.lastIndexOf('/') + 1).replace(/\.ya?ml$/i, '');
    return fileName.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ');
}

/** @returns the file name of a policy path */
export function fileNameOf(policyPath: string): string {
    return policyPath.substring(policyPath.lastIndexOf('/') + 1);
}

/** Creates the document of a new policy from the first committed policy, which shares the settings of the supervised code, with nothing permitted. */
export function newPolicyDocument(template: SecurityPolicyDocument | undefined, policyPath: string): SecurityPolicyDocument {
    return {
        policyPath,
        languageConfiguration: template?.languageConfiguration,
        supervisedPackage: template?.supervisedPackage,
        mainClass: template?.mainClass,
        testClasses: [],
    };
}

/** The format Ares reads dates in, e.g. {@code 2026-12-24 23:59 Europe/Berlin}. */
const ARES_DATE_FORMAT = 'YYYY-MM-DD HH:mm';

/** @returns the date in the format of the hidden tests of Ares, with the time zone of the browser */
export function formatAresDate(date: dayjs.Dayjs): string {
    return `${date.format(ARES_DATE_FORMAT)} ${Intl.DateTimeFormat().resolvedOptions().timeZone}`;
}

/** @returns the date of a value in the format of the hidden tests of Ares, read in the time zone of the browser; undefined if it is no such date */
export function parseAresDate(value: string | undefined): dayjs.Dayjs | undefined {
    const match = value?.match(/^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})(?: +(\S+))?/);
    if (!match) {
        return undefined;
    }
    const wallTime = `${match[1]} ${match[2]}`;
    const date = match[3] ? inZone(wallTime, match[3]) : dayjs(wallTime, ARES_DATE_FORMAT);
    return date?.isValid() ? date : undefined;
}

/** One day in milliseconds, more than any change of the offset of a time zone at once. */
const DAY_MILLISECONDS = 86_400_000;

/**
 * Resolves the wall time in the time zone like {@code java.time} does for Ares: a time that occurs twice, when the clocks are set back, takes the earlier
 * offset, i.e. the first of the two instants; a time that the clocks skip is moved later by the length of the gap, i.e. read with the offset before it.
 * Day.js itself resolves both with the offset the zone has on the day the editor runs, so the same date would mean another instant in summer and in winter.
 *
 * @returns the instant in the browser's time zone; undefined for an unknown zone
 */
function inZone(wallTime: string, zone: string): dayjs.Dayjs | undefined {
    try {
        const asUtc = dayjs.utc(wallTime, ARES_DATE_FORMAT).valueOf();
        const offsetAt = (instant: number) => dayjs(instant).tz(zone).utcOffset() * 60_000;
        const offsetBefore = offsetAt(asUtc - DAY_MILLISECONDS);
        const candidates = [offsetBefore, offsetAt(asUtc + DAY_MILLISECONDS)]
            .map((offset) => asUtc - offset)
            .filter((instant) => dayjs(instant).tz(zone).format(ARES_DATE_FORMAT) === wallTime);
        return dayjs(candidates.length ? Math.min(...candidates) : asUtc - offsetBefore);
    } catch {
        return undefined;
    }
}

/** One line of a side of the diff. */
export interface DiffLine {
    number: number;
    text: string;
}

/** One row of the side-by-side diff of the review. */
export type DiffRow =
    | { kind: 'same'; left: DiffLine; right: DiffLine }
    | { kind: 'removed'; left: DiffLine }
    | { kind: 'added'; right: DiffLine }
    | { kind: 'changed'; left: DiffLine; right: DiffLine }
    | { kind: 'collapsed'; count: number };

/** The number of unchanged lines kept around each change; longer unchanged runs collapse. */
const DIFF_CONTEXT = 1;

/**
 * The most cells of the table of the longest common subsequence, which bounds the memory. A larger changed part is compared line by line at the same
 * positions instead, so an edit that keeps the number of lines still shows exactly; one that inserts or removes lines in it shows the lines after it as changed.
 */
const MAX_DIFF_CELLS = 1_000_000;

/**
 * Computes a side-by-side line diff. The unchanged lines at the start and the end are matched first; the part between them is diffed with the longest
 * common subsequence if its table stays within {@link MAX_DIFF_CELLS}, so even a large policy with a small edit needs little memory. A removed line directly
 * followed by an added one is shown as changed, in one row. Unchanged runs are collapsed except for {@link DIFF_CONTEXT} lines next to a change.
 */
export function diffLines(before: string, after: string): DiffRow[] {
    const left = splitLines(before);
    const right = splitLines(after);
    let start = 0;
    while (start < left.length && start < right.length && left[start] === right[start]) {
        start++;
    }
    let leftEnd = left.length;
    let rightEnd = right.length;
    while (leftEnd > start && rightEnd > start && left[leftEnd - 1] === right[rightEnd - 1]) {
        leftEnd--;
        rightEnd--;
    }
    const rows: DiffRow[] = [];
    for (let index = 0; index < start; index++) {
        rows.push({ kind: 'same', left: { number: index + 1, text: left[index] }, right: { number: index + 1, text: right[index] } });
    }
    diffMiddle(left.slice(start, leftEnd), right.slice(start, rightEnd), start, rows);
    for (let offset = 0; offset < left.length - leftEnd; offset++) {
        const i = leftEnd + offset;
        const j = rightEnd + offset;
        rows.push({ kind: 'same', left: { number: i + 1, text: left[i] }, right: { number: j + 1, text: right[j] } });
    }
    return collapseUnchanged(pairChanges(rows));
}

/** Appends the rows of the changed part between the unchanged start and end; both sides of it begin at the line index {@code start}. */
function diffMiddle(left: string[], right: string[], start: number, rows: DiffRow[]): void {
    const removed = (i: number): DiffRow => ({ kind: 'removed', left: { number: start + i + 1, text: left[i] } });
    const added = (j: number): DiffRow => ({ kind: 'added', right: { number: start + j + 1, text: right[j] } });
    const width = right.length + 1;
    if ((left.length + 1) * width > MAX_DIFF_CELLS) {
        for (let k = 0; k < Math.max(left.length, right.length); k++) {
            if (k < left.length && k < right.length && left[k] === right[k]) {
                rows.push({ kind: 'same', left: { number: start + k + 1, text: left[k] }, right: { number: start + k + 1, text: right[k] } });
                continue;
            }
            if (k < left.length) {
                rows.push(removed(k));
            }
            if (k < right.length) {
                rows.push(added(k));
            }
        }
        return;
    }
    const lengths = new Uint32Array((left.length + 1) * width);
    for (let i = left.length - 1; i >= 0; i--) {
        for (let j = right.length - 1; j >= 0; j--) {
            lengths[i * width + j] = left[i] === right[j] ? lengths[(i + 1) * width + j + 1] + 1 : Math.max(lengths[(i + 1) * width + j], lengths[i * width + j + 1]);
        }
    }
    let i = 0;
    let j = 0;
    while (i < left.length || j < right.length) {
        if (i < left.length && j < right.length && left[i] === right[j]) {
            rows.push({ kind: 'same', left: { number: start + i + 1, text: left[i] }, right: { number: start + j + 1, text: right[j] } });
            i++;
            j++;
        } else if (j < right.length && (i === left.length || lengths[i * width + j + 1] >= lengths[(i + 1) * width + j])) {
            rows.push(added(j));
            j++;
        } else {
            rows.push(removed(i));
            i++;
        }
    }
}

function splitLines(text: string): string[] {
    if (!text) {
        return [];
    }
    const lines = text.split('\n');
    return lines.at(-1) === '' ? lines.slice(0, -1) : lines;
}

/** Pairs the removed and the added lines of each run of changes, line by line, so a changed line shows in one row. */
function pairChanges(rows: DiffRow[]): DiffRow[] {
    const result: DiffRow[] = [];
    let index = 0;
    while (index < rows.length) {
        const removed: DiffLine[] = [];
        const added: DiffLine[] = [];
        while (index < rows.length && (rows[index].kind === 'removed' || rows[index].kind === 'added')) {
            const row = rows[index];
            if (row.kind === 'removed') {
                removed.push(row.left);
            } else if (row.kind === 'added') {
                added.push(row.right);
            }
            index++;
        }
        const paired = Math.min(removed.length, added.length);
        for (let k = 0; k < paired; k++) {
            result.push({ kind: 'changed', left: removed[k], right: added[k] });
        }
        removed.slice(paired).forEach((line) => result.push({ kind: 'removed', left: line }));
        added.slice(paired).forEach((line) => result.push({ kind: 'added', right: line }));
        if (!removed.length && !added.length) {
            result.push(rows[index]);
            index++;
        }
    }
    return result;
}

function collapseUnchanged(rows: DiffRow[]): DiffRow[] {
    const isChange = rows.map((row) => row.kind !== 'same');
    const keep = rows.map((_, index) => {
        for (let offset = -DIFF_CONTEXT; offset <= DIFF_CONTEXT; offset++) {
            if (isChange[index + offset]) {
                return true;
            }
        }
        return false;
    });
    const result: DiffRow[] = [];
    let collapsed = 0;
    rows.forEach((row, index) => {
        if (keep[index]) {
            if (collapsed) {
                result.push({ kind: 'collapsed', count: collapsed });
                collapsed = 0;
            }
            result.push(row);
        } else {
            collapsed++;
        }
    });
    if (collapsed) {
        result.push({ kind: 'collapsed', count: collapsed });
    }
    return result;
}

/** @returns how many lines a diff adds and removes; a changed line counts once for each */
export function diffStats(rows: DiffRow[]): { added: number; removed: number } {
    let added = 0;
    let removed = 0;
    for (const row of rows) {
        if (row.kind === 'added' || row.kind === 'changed') {
            added++;
        }
        if (row.kind === 'removed' || row.kind === 'changed') {
            removed++;
        }
    }
    return { added, removed };
}

/** @returns the 1-based numbers of the lines of the new text that a change added or changed */
export function changedLineNumbers(before: string | undefined, after: string): Set<number> {
    if (before === undefined) {
        return new Set(splitLines(after).map((_, index) => index + 1));
    }
    if (before === after) {
        return new Set<number>();
    }
    const numbers = new Set<number>();
    for (const row of diffLines(before, after)) {
        if (row.kind === 'added' || row.kind === 'changed') {
            numbers.add(row.right.number);
        }
    }
    return numbers;
}
