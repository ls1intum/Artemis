import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const webapp = resolve(repoRoot, 'src/main/webapp');
const read = (path) => readFileSync(resolve(webapp, path), 'utf8');

/**
 * The shells pin a lot of heights so that every bar reads alike, and every pinned height is a place where content that
 * legitimately needs more room gets clipped instead. These three came up in review, all silent: nothing errors, the
 * layout is simply wrong in a state nobody looked at.
 *
 * jsdom performs no layout, so a component test cannot see any of them. The invariants are therefore checked here,
 * against the declarations themselves.
 */
describe('regions that have to stay flexible', () => {
    it('lets a page top bar control grow past the contract height', () => {
        const global = read('content/scss/global.scss');
        const bar = global.match(/\.page-top-bar\s*\{([\s\S]*?)\n\}/);
        expect(bar, '.page-top-bar is not defined in global.scss').toBeTruthy();
        const formControl = bar[1].match(/\.form-control\s*\{([\s\S]*?)\n {4}\}/);
        expect(formControl, '.page-top-bar .form-control is not defined').toBeTruthy();
        // The only `.form-control` in a page top bar is the communication search, which wraps a badge per selected
        // conversation and author. A fixed height leaves every wrapped row outside the bar.
        expect(formControl[1], 'pin a minimum, not a height: the control below this wraps its content').not.toMatch(/\n\s*height:/);
        expect(formControl[1]).toMatch(/min-height:\s*var\(--title-bar-content-height\)/);
    });

    it('sizes both panes of the communication row from the row', () => {
        const scss = read('app/communication/shared/course-conversations/course-conversations.component.scss');
        const override = scss.match(/([^\n{]*)\{\s*\n\s*height:\s*auto;/);
        expect(override, 'the message row no longer overrides `dynamic-content-height`').toBeTruthy();
        // Scoping this to one pane leaves its sibling — the open answer thread — on the viewport-derived height, which
        // disagrees with the row it sits in, so the thread ends short of the divider above the footer or runs past it.
        expect(override[1], 'scope the override to the row so the open thread is covered as well').toContain('.communication-message-row');
    });

    it('lets a title bar shrink its action group', () => {
        const scss = read('app/course/shared/course-title-bar/course-title-bar.component.scss');
        const actions = scss.match(/\.title-bar-actions\s*\{([\s\S]*?)\n {4}\}/);
        expect(actions, '.title-bar-actions is not defined in the course title bar').toBeTruthy();
        // Without this the wrapper keeps its automatic minimum size, and a page whose controls could give way (the
        // calendar filter and its chips) instead pushes the whole row past the right edge of the bar.
        expect(actions[1]).toMatch(/min-width:\s*0/);
    });
});
