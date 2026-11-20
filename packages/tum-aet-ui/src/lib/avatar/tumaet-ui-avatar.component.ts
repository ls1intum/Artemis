import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal } from '@angular/core';
import { TUM_AET_UI_TRANSLATOR } from '../i18n/tumaet-ui-translations';

/** A portrait with a Unicode initials fallback. Use decorative beside a visible name. */
@Component({
    selector: 'tumaet-ui-avatar',
    templateUrl: './tumaet-ui-avatar.component.html',
    styleUrl: './tumaet-ui-avatar.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: {
        '[attr.role]': 'decorative() ? null : "img"',
        '[attr.aria-label]': 'decorative() ? null : accessibleName()',
        '[attr.aria-hidden]': 'decorative() ? "true" : null',
        '[attr.data-size]': 'size()',
    },
})
export class TumAetUiAvatarComponent {
    private readonly translator = inject(TUM_AET_UI_TRANSLATOR);

    readonly name = input.required<string>();
    readonly imageUrl = input<string>();
    readonly size = input<'small' | 'default' | 'large'>('default');
    readonly decorative = input(false);
    protected readonly failed = linkedSignal({ source: this.imageUrl, computation: () => false });
    protected readonly accessibleName = computed(() => {
        this.translator.translationChanges?.();
        return this.name().trim() || this.translator.translate('tumAetUi.avatar.unknown');
    });
    protected readonly initials = computed(() => {
        const graphemes = (text: string) => Array.from(new Intl.Segmenter(undefined, { granularity: 'grapheme' }).segment(text), (part) => part.segment);
        const words = this.name()
            .trim()
            .split(/\s+/)
            .map((word) => graphemes(word).filter((part) => /\p{L}/u.test(part)))
            .filter((letters) => letters.length);
        if (!words.length) {
            return '?';
        }
        return (words.length > 1 ? [words[0][0], words[words.length - 1][0]] : words[0].slice(0, 2)).join('').toLocaleUpperCase();
    });
}
