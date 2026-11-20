/**
 * Forbid the bare spacing utilities 3 to 5 (`mb-3`, `px-4`, `gap-5`).
 *
 * Bootstrap 5, which is removed from the client, defined the same class NAMES as Tailwind for margin, padding and gap
 * (`m`, `mt`, `mb`, `ms`, `me`, `mx`, `my`, `p`, `pt`, `pb`, `ps`, `pe`, `px`, `py`, `gap`, each with 0 to 5), but
 * with another scale: Bootstrap's N was 0, .25, .5, 1, 1.5, 3 rem, Tailwind's N is N * .25rem. They agree for 0, 1
 * and 2 and differ for
 *
 *     N   Bootstrap        Tailwind
 *     3   1rem   (16px)    0.75rem (12px)
 *     4   1.5rem (24px)    1rem    (16px)
 *     5   3rem   (48px)    1.25rem (20px)
 *
 * Markup written with Bootstrap in mind, for example on an old branch, would therefore render a smaller space
 * without any error. And a bare Tailwind utility is a normal declaration in a cascade layer, which loses to every
 * unlayered declaration of the same property: the element defaults in `content/scss/_base.scss` (`p`, `h1` to `h6`,
 * `dd`, `ul` and others set their margins; a `<p class="mt-6">` renders no top margin) and the SCSS of the components.
 * So the author has to say which size is meant, and has to make it win:
 *
 *   - for the size Bootstrap rendered: `mb-3` -> `mb-4!`, `mb-4` -> `mb-6!`, `mb-5` -> `mb-12!`
 *   - for the Tailwind value: `mb-3!`, `mb-4!`, `mb-5!`
 *
 * The trailing `!` is Tailwind's important modifier. The steps 0, 1, 2 and `auto` need none: they are defined as
 * unlayered important rules in `content/scss/_utilities.scss`.
 *
 * Only the BARE token is reported: not a Tailwind variant (`md:mb-3`), not an important one (`mb-3!`), not an
 * arbitrary value (`mb-[3px]`), not a negative one (`-mb-3`) and not another utility that
 * merely looks similar (`gap-x-3`, `space-x-3`, `my-modal-3`). `ml`, `mr`, `pl` and `pr` did not exist in Bootstrap 5.
 * The set of class names was derived from the Bootstrap CSS that Artemis compiled (`$spacers` had 0 to 5).
 *
 * Scope: static `class="..."` (including ones with interpolations), `[class.<token>]`, and the bound source of
 * `[class]` / `[ngClass]`, plus `styleClass` inputs. Enabled for the folders that eslint.config.mjs lists, which
 * Tailwind scans (see the `@source` list in tailwind.css).
 *
 * Not judged, because the class name is not in the template text: a name that an expression computes
 * (`[class]="spacing"`), a literal inside an interpolation of a class list (`class="{{ a ? 'mb-3' : '' }}"`), a
 * literal glued to an interpolation (`mb-3-{{ x }}`), a plain `ngClass="..."` attribute, an ICU case body, and the
 * inline `template:` of a component in a `.ts` file.
 * See documentation/docs/developer/guidelines/client-development.mdx (### Styling).
 */
import { classTokens, classTokensInBindingExpression, classTokensInInterpolatedList, isClassListAttribute } from './class-list.mjs';

const AMBIGUOUS = /^(m|mt|mb|ms|me|mx|my|p|pt|pb|ps|pe|px|py|gap)-([345])$/;

// A shorthand and the sides it sets. An important Tailwind shorthand (`p-4!`) is a layered important declaration and beats
// a side that is not written important, also the bare steps 0 to 2 of `_utilities.scss`, so `p-4! pt-0` pads the top:
// the side has to be important too.
const SIDES_OF_SHORTHAND = {
    p: ['pt', 'pb', 'ps', 'pe', 'px', 'py'],
    px: ['ps', 'pe'],
    py: ['pt', 'pb'],
    m: ['mt', 'mb', 'ms', 'me', 'mx', 'my'],
    mx: ['ms', 'me'],
    my: ['mt', 'mb'],
};
const SPACING_TOKEN = /^(m|mt|mb|ms|me|mx|my|p|pt|pb|ps|pe|px|py)-(\d+(?:\.\d+)?|auto|px)(!?)$/;

// What the bare token rendered with Bootstrap and what it renders now (Tailwind), plus the important
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
            description:
                'Forbid bare spacing utilities 3 to 5, whose value differed between the removed Bootstrap and Tailwind; write the intended step with the important modifier.',
        },
        messages: {
            ambiguousSpacing:
                "'{{cls}}' is ambiguous: Bootstrap, which is removed, rendered {{bootstrap}} for it, Tailwind renders {{tailwind}}. Write '{{pinned}}' for {{bootstrap}}, or '{{twValue}}' for the Tailwind value {{tailwind}}. Keep the !: a bare utility loses to the unlayered element defaults and component styles. See client-development.mdx (### Styling).",
            shadowedByShorthand: "'{{cls}}' loses to the important shorthand '{{shorthand}}' in the same class list. Write '{{cls}}!' so the side wins.",
            pinCurrentValue: "Use the former Bootstrap size {{bootstrap}}: write '{{replacement}}'.",
            useTailwindValue: "Use the Tailwind value {{tailwind}}: write '{{replacement}}'.",
            makeSideImportant: "Make the side important: write '{{replacement}}'.",
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

        // Only a static class list is judged: its tokens are all on the element and together decide the cascade.
        function checkShadowedSides(tokens, base) {
            const parsed = tokens.map(({ token, index }) => ({ token, index, match: SPACING_TOKEN.exec(token) }));
            const importantShorthands = parsed.filter(({ match }) => match?.[3] === '!' && SIDES_OF_SHORTHAND[match[1]]).map(({ token, match }) => ({ token, prefix: match[1] }));
            for (const { token, index, match } of parsed) {
                if (!match || match[3] === '!') {
                    continue;
                }
                const shadowing = importantShorthands.find(({ prefix }) => SIDES_OF_SHORTHAND[prefix].includes(match[1]));
                if (shadowing) {
                    const start = base + index;
                    const end = start + token.length;
                    context.report({
                        loc: { start: sourceCode.getLocFromIndex(start), end: sourceCode.getLocFromIndex(end) },
                        messageId: 'shadowedByShorthand',
                        data: { cls: token, shorthand: shadowing.token },
                        suggest: [{ messageId: 'makeSideImportant', data: { replacement: `${token}!` }, fix: (fixer) => fixer.insertTextAfterRange([start, end], '!') }],
                    });
                }
            }
        }

        // The raw text of an attribute value, which the offsets of the tokens refer to; the parsed `node.value` may
        // have decoded HTML entities and so be shorter.
        const valueText = (node) => (node.valueSpan ? text.slice(node.valueSpan.start.offset, node.valueSpan.end.offset) : undefined);

        return {
            TextAttribute(node) {
                if (isClassListAttribute(node.name) && node.valueSpan) {
                    const tokens = classTokens(valueText(node));
                    checkAll(tokens, node.valueSpan.start.offset);
                    checkShadowedSides(tokens, node.valueSpan.start.offset);
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
