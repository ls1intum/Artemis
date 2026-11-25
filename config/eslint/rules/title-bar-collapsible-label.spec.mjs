import { describe, it, expect } from 'vitest';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const webapp = resolve(repoRoot, 'src/main/webapp');

/**
 * A title bar collapses the labels of the controls projected into it once it runs out of room, leaving icon-only
 * controls. Two halves have to line up for that: the bar declares itself a size container named `title-bar`
 * (`container: title-bar / inline-size`), and a label opts in with `.title-bar-collapsible-label`, whose rule is a
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
const CONTROLS_CLASS = 'title-bar-controls';
const DECLARES_CONTAINER = /container(-name)?:\s*title-bar\b/;

const globalFile = resolve(webapp, 'content/css/layout.css');

/** The body of the `@container title-bar` query that holds the rule of the given selector, if there is one. */
const containerQueryOf = (css, selector) => css.match(new RegExp(`@container\\s+title-bar\\s*\\([^)]*\\)\\s*\\{[^}]*?${selector}\\s*\\{([^}]*)\\}`))?.[1];

describe('collapsible title bar labels', () => {
    it('names the container of a page top bar', () => {
        const global = readFileSync(globalFile, 'utf8');
        const rules = [...global.matchAll(/^\.page-top-bar\s*\{([^}]*)\}/gm)].map((match) => match[1]);
        // Either the shorthand (`container: title-bar / inline-size`) or the two longhands, but the name must be there.
        expect(rules.some((body) => DECLARES_CONTAINER.test(body) && /inline-size/.test(body)), '.page-top-bar is not a size container named title-bar').toBe(true);
    });

    it('queries that same container name from the opt-in class', () => {
        const body = containerQueryOf(readFileSync(globalFile, 'utf8'), `\\.${LABEL_CLASS}`);
        expect(body, `.${LABEL_CLASS} must collapse inside an @container title-bar query in layout.css`).toBeTruthy();
        // `display: none` would drop the label out of the accessibility tree and leave the icon-only control unnamed.
        expect(body).not.toMatch(/display:\s*none/);
    });

    it('establishes the container in every bar that styles projected controls', () => {
        // A component marks its bar with the class in its template or on its host; its stylesheet has to make the bar a
        // size container, because the rules of layout.css for that class include a container query.
        const offenders = [...walk(webapp, '.html'), ...walk(webapp, '.ts')]
            .filter((file) => !file.endsWith('.spec.ts') && new RegExp(`\\b${CONTROLS_CLASS}\\b`).test(readFileSync(file, 'utf8')))
            .filter((file) => {
                const stylesheet = file.replace(/\.(html|ts)$/, '.css');
                return !existsSync(stylesheet) || !DECLARES_CONTAINER.test(readFileSync(stylesheet, 'utf8'));
            })
            .map(relative);
        expect(offenders, `these style projected title bar controls but never become a size container, so ${LABEL_CLASS} cannot work in them`).toEqual([]);
    });

    it('queries that container from the drop-on-narrow class too', () => {
        const body = containerQueryOf(readFileSync(globalFile, 'utf8'), `\\.${OPTIONAL_CLASS}\\.${OPTIONAL_CLASS}`);
        expect(body, `.${OPTIONAL_CLASS} must drop its control inside an @container title-bar query in layout.css`).toBeTruthy();
        expect(body).toMatch(/display:\s*none/);
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
