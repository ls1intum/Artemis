import { Injectable } from '@angular/core';
import { NgbTimeAdapter, NgbTimeStruct } from '@ng-bootstrap/ng-bootstrap';

@Injectable()
export class NgbTimeStringAdapter extends NgbTimeAdapter<string> {
    fromModel(value: string | null): NgbTimeStruct | null {
        if (!value) {
            return null;
        }
        const split = value.split(':');
        return {
            hour: parseInt(split[0], 10),
            minute: parseInt(split[1], 10),
            second: parseInt(split[2], 10),
        };
    }

    toModel(time: NgbTimeStruct | null): string | null {
        return time !== null ? `${pad(time.hour)}:${pad(time.minute)}:${pad(time.second)}` : null;
    }
}

const pad = (i: number): string => (i < 10 ? `0${i}` : `${i}`);
