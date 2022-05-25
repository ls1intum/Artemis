import { GraphColors } from 'app/entities/statistics.model';

// Chart content
export class ChartData {
    name: string;
    value: number;

    constructor(name: string, value: number) {
        this.name = name;
        this.value = value;
    }
}

// Collection of colors used for the exam monitoring
const colors = [
    GraphColors.MONITORING_VARIANT_ONE,
    GraphColors.MONITORING_VARIANT_TWO,
    GraphColors.MONITORING_VARIANT_THREE,
    GraphColors.MONITORING_VARIANT_FOUR,
    GraphColors.MONITORING_VARIANT_FIVE,
    GraphColors.MONITORING_VARIANT_SIX,
    GraphColors.MONITORING_VARIANT_SEVEN,
    GraphColors.MONITORING_VARIANT_EIGHT,
    GraphColors.MONITORING_VARIANT_NINE,
];

/**
 * Returns a color based on the index (modulo)
 * @param index
 */
export function getColor(index: number) {
    return colors[index % colors.length];
}
