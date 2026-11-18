export class Option {
    key?: boolean | string;
    label?: string;

    constructor(key?: boolean | string, label?: string) {
        this.key = key;
        this.label = label;
    }
}

export class Duration {
    hours?: number;
    minutes?: number;
    seconds?: number;

    constructor(minutes: number, seconds: number, hours = 0) {
        this.hours = hours;
        this.minutes = minutes;
        this.seconds = seconds;
    }
}
