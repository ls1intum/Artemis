/**
 * Appends a percentage sign to every tick on the x axis
 * @param tick the default tick label as string
 * @returns tick label string that is extended by an percentage sign
 */
export function xAxisFormatting(tick: string): string {
    return tick + '%';
}

/**
 * Dynamically generates a color based on the input
 * @param i factor that is modifying the first coordinate of the color
 * @param l percentage defining the last part of the color
 * @returns color in hsl format
 */
export function getColor(i: number, l: number): string {
    return `hsl(${(i * 360 * 3) % 360}, 55%, ${l}%)`;
}
