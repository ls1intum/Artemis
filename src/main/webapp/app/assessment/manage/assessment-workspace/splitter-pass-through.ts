import type { SplitterPassThrough } from 'primeng/types/splitter';

/**
 * The width (px) of the gutter between two panels, the same on every page that divides panels: the assessment workspace, the
 * exercise page, and the code editor's own dividers (which mirror it in `--code-editor-divider`). The grip drawn on it is
 * {@link splitterPassThrough}'s, and the code editor grid draws the same one.
 */
export const SPLITTER_GUTTER_SIZE = 12;

/**
 * The colour of the gutters: the grey of the shell around the panels, as on the pages of a student, or the colour of the panels
 * themselves, which the assessment pages use so the line between two panels reads as part of one white surface.
 */
export type SplitterGutterStyle = 'shell' | 'panel';

/**
 * Pass-through styles that give every `p-splitter` of an assessment page the same look: a root in the colour of the gutters, panels that may
 * shrink to nothing, and a gutter whose handle shows two thin grip lines. The gutter is a wide, easy to hit strip; its handle is
 * only drawn, so a click always lands on the gutter. A page that its tests find the gutter and panels of by test id passes them.
 */
export function splitterPassThrough(
    layout: 'horizontal' | 'vertical',
    testIds: { gutter?: string; panel?: string } = {},
    gutterStyle: SplitterGutterStyle = 'shell',
): SplitterPassThrough {
    const dividerColor = 'color-mix(in srgb, var(--tumaet-ui-text-color) 62%, transparent)';
    const handleBackground =
        layout === 'horizontal'
            ? `linear-gradient(to right, transparent 0 3px, ${dividerColor} 3px 5px, transparent 5px 7px, ${dividerColor} 7px 9px, transparent 9px)`
            : `linear-gradient(to bottom, transparent 0 3px, ${dividerColor} 3px 5px, transparent 5px 7px, ${dividerColor} 7px 9px, transparent 9px)`;
    const panel: Record<string, unknown> = { style: { minWidth: '0', minHeight: '0', overflow: 'hidden' } };
    if (testIds.panel) {
        panel['data-testid'] = testIds.panel;
    }
    const gutterBackground = gutterStyle === 'panel' ? 'var(--module-bg)' : 'var(--bs-body-bg)';
    const gutter: Record<string, unknown> = {
        style: {
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            // The grey of the shell between its regions, so the line between two panels looks the same on every page, whatever
            // the panels around it show behind it
            background: gutterBackground,
            cursor: layout === 'horizontal' ? 'ew-resize' : 'ns-resize',
        },
    };
    if (testIds.gutter) {
        gutter['data-testid'] = testIds.gutter;
    }
    return {
        // Behind the rounded corners of the panels: the colour of the gutters, or the corners of a panel would show a white splitter
        root: { style: { border: '0', background: gutterBackground } },
        panel,
        gutter,
        gutterHandle: {
            style: {
                width: layout === 'horizontal' ? '0.75rem' : '1.25rem',
                height: layout === 'horizontal' ? '1.25rem' : '0.75rem',
                background: handleBackground,
                pointerEvents: 'none',
            },
        },
    };
}
