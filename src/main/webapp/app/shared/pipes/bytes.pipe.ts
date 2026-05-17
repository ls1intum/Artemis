import { Pipe, PipeTransform } from '@angular/core';

/**
 * Formats a byte count as a human-readable string with binary units (1 KiB = 1024 B). Mirrors the rendering
 * convention used by {@code df -h} / {@code du -h} on Linux, which is what operators see when they SSH into a
 * build agent — keeping the admin UI consistent with what they would see directly on the host.
 *
 * @example
 *   `1024 | bytes`        → `1.0 KB`
 *   `3221225472 | bytes`  → `3.0 GB`     (3 GiB; matches the Spring DataSize default of `3GB`)
 *   `0 | bytes`           → `0 B`
 *   `undefined | bytes`   → `—`           (dash placeholder, used when the agent has not yet reported the value)
 */
@Pipe({ name: 'bytes', standalone: true })
export class BytesPipe implements PipeTransform {
    private static readonly UNITS = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];

    private static readonly PLACEHOLDER = '—';

    transform(value: number | null | undefined, fractionDigits = 1): string {
        if (value === null || value === undefined || Number.isNaN(value)) {
            return BytesPipe.PLACEHOLDER;
        }
        if (value === 0) {
            return '0 B';
        }
        const negative = value < 0;
        let v = Math.abs(value);
        let unit = 0;
        while (v >= 1024 && unit < BytesPipe.UNITS.length - 1) {
            v /= 1024;
            unit++;
        }
        // For bytes, no fraction digits — "768 B" is more useful than "768.0 B".
        const formatted = unit === 0 ? Math.round(v).toString() : v.toFixed(fractionDigits);
        return `${negative ? '-' : ''}${formatted} ${BytesPipe.UNITS[unit]}`;
    }
}
