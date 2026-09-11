import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const webapp = resolve(repoRoot, 'src/main/webapp');

/**
 * A title bar collapses the labels of the controls projected into it once it runs out of room, leaving icon-only
 * controls. Two halves have to line up for that: the bar declares itself a size container (`title-bar-size-container`,
 * which names the container `title-bar`), and a label opts in with `.title-bar-collapsible-label`, whose rule is a
 * `@container title-bar` query.
 *
 * Both halves fail silently. A container query whose named container does not exist never matches, so the labels simply
 * keep rendering at full length and squeeze their neighbours — which is the bug this replaced, not an error anyone
 * sees. Component tests cannot catch it either: jsdom applies no container queries at all. Hence these invariants are
 * checked against the sources.
 */
const walk = (dir, extension, out = []) => {
    for (const entry of readdirSync(dir)) {
        if (entry === 'node_modules') continue;
        const full = join(dir, entry);
        if (statSync(full).isDirectory()) walk(full, extension, out);
        else if (entry.endsWith(extension)) out.push(full);
    }
    return out;
};

const relative = (file) => file.slice(repoRoot.length + 1);

const LABEL_CLASS = 'title-bar-collapsible-label';
const OPTIONAL_CLASS = 'title-bar-optional-control';
const CONTAINER_MIXIN = 'title-bar-size-container';
const CONTROLS_MIXIN = 'title-bar-compact-controls';

const mixinsFile = resolve(webapp, 'content/scss/_artemis-mixins.scss');
const globalFile = resolve(webapp, 'content/scss/global.scss');

describe('collapsible title bar labels', () => {
    it('names its container in the mixin the bars include', () => {
        const mixins = readFileSync(mixinsFile, 'utf8');
        const body = mixins.match(new RegExp(`@mixin ${CONTAINER_MIXIN}\\s*\\{([\\s\\S]*?)\\n\\}`));
        expect(body, `${CONTAINER_MIXIN} is not defined in _artemis-mixins.scss`).toBeTruthy();
        // Either the shorthand (`container: title-bar / inline-size`) or the two longhands, but the name must be there.
        expect(body[1]).toMatch(/container(-name)?:\s*title-bar/);
        expect(body[1]).toMatch(/inline-size/);
    });

    it('queries that same container name from the opt-in class', () => {
        const global = readFileSync(globalFile, 'utf8');
        const rule = global.match(new RegExp(`\\.${LABEL_CLASS}\\s*\\{([\\s\\S]*?)\\n\\}\\n`));
        expect(rule, `.${LABEL_CLASS} is not defined in global.scss`).toBeTruthy();
        expect(rule[1], `.${LABEL_CLASS} must collapse inside an @container title-bar query`).toMatch(/@container\s+title-bar\s*\(/);
        // `display: none` would drop the label out of the accessibility tree and leave the icon-only control unnamed.
        expect(rule[1]).not.toMatch(/display:\s*none/);
    });

    it('establishes the container in every bar that styles projected controls', () => {
        const offenders = walk(webapp, '.scss')
            .filter((file) => {
                const content = readFileSync(file, 'utf8');
                return new RegExp(`@include ${CONTROLS_MIXIN}`).test(content) && !new RegExp(`@include ${CONTAINER_MIXIN}`).test(content);
            })
            .map(relative);
        expect(offenders, `these style projected title bar controls but never become a size container, so ${LABEL_CLASS} cannot work in them`).toEqual([]);
    });

    it('queries that container from the drop-on-narrow class too', () => {
        const global = readFileSync(globalFile, 'utf8');
        const rule = global.match(new RegExp(`\\.${OPTIONAL_CLASS}\\s*\\{([\\s\\S]*?)\\n\\}\\n`));
        expect(rule, `.${OPTIONAL_CLASS} is not defined in global.scss`).toBeTruthy();
        expect(rule[1], `.${OPTIONAL_CLASS} must drop its control inside an @container title-bar query`).toMatch(/@container\s+title-bar\s*\(/);
        expect(rule[1]).toMatch(/display:\s*none/);
    });

    it('only marks elements that sit inside a title bar', () => {
        // The markers that open a bar region: the shell bar's projection slots, the shell bar itself, or a self-rendered bar.
        const barMarker = /titleBarActions|titleBarTitle|titleBarToolbar|page-top-bar|controlsViewContainer|<jhi-course-title-bar/;
        const indentOf = (line) => line.match(/^[ \t]*/)[0].length;

        /**
         * Whether the given line sits inside the region opened by the nearest bar marker above it. Prettier formats
         * these templates with a fixed indent, so "the region has not closed yet" is "every line since the marker is
         * indented deeper than the marker itself". Without this the check passed on a marker anywhere in the file,
         * which let a control that renders far outside the bar borrow a marker it has nothing to do with.
         */
        const insideBarRegion = (lines, index) => {
            for (let marker = index; marker >= 0; marker--) {
                if (!barMarker.test(lines[marker])) continue;
                const markerIndent = indentOf(lines[marker]);
                for (let between = marker + 1; between <= index; between++) {
                    if (lines[between].trim() && indentOf(lines[between]) <= markerIndent) return false;
                }
                return true;
            }
            return false;
        };

        // Inline templates carry these classes too, so a component that declares its markup in the decorator is checked
        // like an external template. Spec files are skipped: their fixtures are markup about the markup.
        const templates = [...walk(webapp, '.html'), ...walk(webapp, '.ts').filter((file) => !file.endsWith('.spec.ts'))].map((file) => ({
            file,
            lines: readFileSync(file, 'utf8').split('\n'),
        }));

        // A shared control that is rendered both into a bar and into a page body binds the class instead of hardcoding
        // it, so the consumer decides. Those lines are not a claim about where this template renders.
        const conditionalBinding = new RegExp(`\\[class\\.(${LABEL_CLASS}|${OPTIONAL_CLASS})\\]`, 'g');
        const claimsToBeInABar = (line) => {
            const withoutBindings = line.replace(conditionalBinding, '');
            return withoutBindings.includes(LABEL_CLASS) || withoutBindings.includes(OPTIONAL_CLASS);
        };

        const linesMarking = ({ lines }) => lines.map((line, index) => index).filter((index) => claimsToBeInABar(lines[index]));

        /**
         * A component may keep its collapsible label in its own template and be projected into a bar by whoever renders
         * it. That is fine as long as every usage sits inside a bar region. The selector is matched as a tag so that
         * `jhi-button` does not count a `jhi-button-group` usage as its own.
         */
        const projectedOnlyIntoBars = (template) => {
            const declaration = template.file.endsWith('.ts') ? template.file : template.file.replace(/\.html$/, '.ts');
            let selector;
            try {
                selector = readFileSync(declaration, 'utf8').match(/selector:\s*['"]([^'"]+)['"]/)?.[1];
            } catch {
                return false;
            }
            if (!selector) return false;

            // The tag often opens a multi-line element, so the boundary has to accept the end of the line as well.
            const usage = new RegExp(`<${selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?=[\\s/>]|$)`);
            let usages = 0;
            for (const other of templates) {
                if (other.file === template.file) continue;
                for (let index = 0; index < other.lines.length; index++) {
                    if (!usage.test(other.lines[index])) continue;
                    usages++;
                    if (!insideBarRegion(other.lines, index)) return false;
                }
            }
            return usages > 0;
        };

        const offenders = templates
            .filter((template) => {
                const marked = linesMarking(template);
                if (marked.length === 0) return false;
                return !marked.every((index) => insideBarRegion(template.lines, index)) && !projectedOnlyIntoBars(template);
            })
            .map((template) => relative(template.file));
        expect(offenders, `these classes only work inside a title bar; elsewhere the query never matches`).toEqual([]);
    });
});
