/**
 * Formats the labels on the y-axis in order to display only integer values
 * @param tick the default y-axis tick
 * @returns modified y-axis tick
 */
export function yAxisTickFormatting(tick: string): string {
    return parseFloat(tick).toFixed(0);
}

/**
 * Appends a percentage sign to the y-axis tick
 * @param tick the default tick
 * @returns {{ tick }}%
 */
export function axisTickFormattingWithPercentageSign(tick: string): string {
    return tick + '%';
}
