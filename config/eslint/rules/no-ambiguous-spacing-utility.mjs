/**
 * Forbid the bare spacing utilities whose value differs between Bootstrap and Tailwind (`mb-3`, `px-4`, `gap-5`).
 *
 * Bootstrap 5 is still loaded, and it defines the same class NAMES as Tailwind for margin, padding and gap
 * (`m`, `mt`, `mb`, `ms`, `me`, `mx`, `my`, `p`, `pt`, `pb`, `ps`, `pe`, `px`, `py`, `gap`, each with 0 to 5), but
 * with another scale: Bootstrap's N is 0, .25, .5, 1, 1.5, 3 rem, Tailwind's N is N * .25rem. They agree for 0, 1
 * and 2 and differ for
 *
 *     N   Bootstrap        Tailwind
 *     3   1rem   (16px)    0.75rem (12px)
 *     4   1.5rem (24px)    1rem    (16px)
 *     5   3rem   (48px)    1.25rem (20px)
 *
 * Bootstrap's utilities are unlayered and `!important`, Tailwind's are layered, so Bootstrap wins: a bare `mb-3`
 * renders 16px today and would silently render 12px once Bootstrap is removed. The author of a template cannot tell
 * which of the two they get, so the bare spelling is ambiguous. Spell what is meant instead:
 *
 *   - to keep what renders today, pin it: `mb-3` -> `mb-4!`, `mb-4` -> `mb-6!`, `mb-5` -> `mb-12!`
 *   - to take the Tailwind value, say so: `mb-3!`, `mb-4!`, `mb-5!`
 *
 * The trailing `!` is Tailwind's important modifier, and both spellings need it, for two reasons:
 *
 *   - `mb-4!` is another class name than `mb-4`, so Bootstrap's rule does not apply to it. A bare `mb-4` would be
 *     Bootstrap's 24px again, not the 16px that Tailwind means by it.
 *   - A bare `mb-6` has no Bootstrap class of the same name, but it is a normal declaration in a layer, and that loses
 *     to every unlayered normal declaration of the same property: Bootstrap's Reboot (`p`, `h1` to `h6`, `dd`, `ul`
 *     and others set their margins; a `<p class="mt-6">` renders no top margin) and the SCSS of the components.
 *     Bootstrap's `mb-4` is important and used to win against those, so only the important spelling renders what the
 *     author saw.
 *
 * Only the BARE token is reported: not a Tailwind variant (`md:mb-3`; Bootstrap's own `mb-md-3` is another name), not an important one (`mb-3!`), not an
 * arbitrary value (`mb-[3px]`), not a negative one (`-mb-3`; Bootstrap spells it `mb-n3`) and not another utility that
 * merely looks similar (`gap-x-3`, `space-x-3`, `my-modal-3`). `ml`, `mr`, `pl` and `pr` do not exist in Bootstrap 5.
 * The set of class names was derived from the Bootstrap CSS that Artemis compiles (`$spacers` has 0 to 5).
 *
 * Scope: static `class="..."` (including ones with interpolations), `[class.<token>]`, and the bound source of
 * `[class]` / `[ngClass]`, plus PrimeNG's `styleClass` inputs. Enabled only for the folders that eslint.config.mjs
 * lists, because they are scanned by Tailwind (see the `@source` allowlist in tailwind.css) and migrated to it.
 *
 * Not judged, because the class name is not in the template text: a name that an expression computes
 * (`[class]="spacing"`), a literal inside an interpolation of a class list (`class="{{ a ? 'mb-3' : '' }}"`), a
 * literal glued to an interpolation (`mb-3-{{ x }}`), a plain `ngClass="..."` attribute, an ICU case body, and the
 * inline `template:` of a component in a `.ts` file.
 * See documentation/docs/developer/guidelines/client-development.mdx (### Styling).
 */
import { classTokens, classTokensInBindingExpression, classTokensInInterpolatedList, isClassListAttribute } from './class-list.mjs';

const AMBIGUOUS = /^(m|mt|mb|ms|me|mx|my|p|pt|pb|ps|pe|px|py|gap)-([345])$/;

// What the bare token renders (Bootstrap) and what it would render without Bootstrap (Tailwind), plus the important
// Tailwind step that has the Bootstrap value (`pinned`) and the one that has the Tailwind value (`tailwindStep`).
const SCALE = {
    3: { bootstrap: '1rem (16px)', tailwind: '0.75rem (12px)', pinned: '4!', tailwindStep: '3!' },
    4: { bootstrap: '1.5rem (24px)', tailwind: '1rem (16px)', pinned: '6!', tailwindStep: '4!' },
    5: { bootstrap: '3rem (48px)', tailwind: '1.25rem (20px)', pinned: '12!', tailwindStep: '5!' },
};

export default {
    meta: {
        type: 'problem',
        hasSuggestions: true,
        docs: {
            description: 'Forbid bare spacing utilities 3 to 5 whose value differs between Bootstrap and Tailwind; pin the value or take the Tailwind one.',
        },
        messages: {
            ambiguousSpacing:
                "'{{cls}}' is a Bootstrap and a Tailwind class with different values. While Bootstrap is loaded its !important rule wins and renders {{bootstrap}}, but Tailwind alone renders {{tailwind}}, so the spacing changes when Bootstrap is removed. Write '{{pinned}}' to keep {{bootstrap}}, or '{{twValue}}' for the Tailwind value {{tailwind}}. Keep the !: it is a class Bootstrap does not define and it wins over Bootstrap's element styles. See client-development.mdx (### Styling).",
            pinCurrentValue: "Keep the rendered {{bootstrap}}: write '{{replacement}}'.",
            useTailwindValue: "Use the Tailwind value {{tailwind}}: write '{{replacement}}'.",
        },
        schema: [],
    },
    create(context) {
        const sourceCode = context.sourceCode;
        const text = sourceCode.getText();
        // An element with a structural directive (`*ngIf`) is visited twice, as the template and as the element, and a
        // bound class on it would be reported at both.
        const reported = new Set();

        // `start` is the offset of the token in the file.
        function check(token, start) {
            const match = AMBIGUOUS.exec(token);
            if (!match || reported.has(start)) {
                return;
            }
            reported.add(start);
            const [, prefix, step] = match;
            const { bootstrap, tailwind, pinned, tailwindStep } = SCALE[step];
            const end = start + token.length;
            const replace = (replacement) => (fixer) => fixer.replaceTextRange([start, end], replacement);
            const pinnedClass = `${prefix}-${pinned}`;
            const tailwindClass = `${prefix}-${tailwindStep}`;
            context.report({
                loc: { start: sourceCode.getLocFromIndex(start), end: sourceCode.getLocFromIndex(end) },
                messageId: 'ambiguousSpacing',
                data: { cls: token, bootstrap, tailwind, pinned: pinnedClass, twValue: tailwindClass },
                suggest: [
                    { messageId: 'pinCurrentValue', data: { bootstrap, replacement: pinnedClass }, fix: replace(pinnedClass) },
                    { messageId: 'useTailwindValue', data: { tailwind, replacement: tailwindClass }, fix: replace(tailwindClass) },
                ],
            });
        }

        function checkAll(tokens, base) {
            for (const { token, index } of tokens) {
                check(token, base + index);
            }
        }

        // The raw text of an attribute value, which the offsets of the tokens refer to; the parsed `node.value` may
        // have decoded HTML entities and so be shorter.
        const valueText = (node) => (node.valueSpan ? text.slice(node.valueSpan.start.offset, node.valueSpan.end.offset) : undefined);

        return {
            TextAttribute(node) {
                if (isClassListAttribute(node.name) && node.valueSpan) {
                    checkAll(classTokens(valueText(node)), node.valueSpan.start.offset);
                }
            },
            BoundAttribute(node) {
                if ((isClassListAttribute(node.name) || node.name === 'ngClass') && node.valueSpan) {
                    // `class="a-{{ x }} mb-3"` is a class list with interpolations; the other bindings are expressions.
                    const scan = node.value?.ast?.type === 'Interpolation' ? classTokensInInterpolatedList : classTokensInBindingExpression;
                    checkAll(scan(valueText(node)), node.valueSpan.start.offset);
                } else if (node.keySpan?.details?.startsWith('class.')) {
                    // [class.mb-3]="..." -> the class token is the attribute name after `class.`. Gate on the `class.` key
                    // so a component INPUT that shares a name ([p-3]) is not mistaken for a class.
                    check(node.name, node.keySpan.start.offset + 'class.'.length);
                }
            },
        };
    },
};
