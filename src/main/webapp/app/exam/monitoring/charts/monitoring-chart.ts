import { GraphColors } from 'app/entities/statistics.model';

// Chart content
export class ChartData {
    name: string;
    value: any;

    constructor(name: string, value: any) {
        this.name = name;
        this.value = value;
    }
}

export class ChartSeriesData {
    name: string;
    series: ChartData[];

    constructor(name: string, series: ChartData[]) {
        this.name = name;
        this.series = series;
    }
}

// Collection of colors used for the exam monitoring
const colors = [GraphColors.LIGHT_GREY, GraphColors.GREY, GraphColors.DARK_BLUE, GraphColors.BLUE, GraphColors.LIGHT_BLUE];

/**
 * Returns a color based on the index (modulo)
 * @param index
 */
export function getColor(index: number) {
    return colors[index % colors.length];
}
