export class Option {
    key?: boolean | string;
    label?: string;

    constructor(key?: boolean | string, label?: string) {
        this.key = key;
        this.label = label;
    }
}

export class Duration {
    days?: number;
    hours?: number;
    minutes?: number;
    seconds?: number;

    constructor(minutes: number, seconds: number, days = 0, hours = 0) {
        this.days = days;
        this.hours = hours;
        this.minutes = minutes;
        this.seconds = seconds;
    }
}
