export class Option {
    key: boolean | string;
    label: string;

    constructor(key?: boolean | string, label?: string) {
        this.key = key;
        this.label = label;
    }
}

export class Duration {
    minutes: number;
    seconds: number;

    constructor(minutes?: number, seconds?: number) {
        this.minutes = minutes;
        this.seconds = seconds;
    }
}
