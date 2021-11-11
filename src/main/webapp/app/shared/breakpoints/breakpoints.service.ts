import { Injectable } from '@angular/core';

export const CustomBreakpointNames = {
    extraSmall: 'extraSmall',
    small: 'small',
    medium: 'medium',
    large: 'large',
    extraLarge: 'extraLarge',
};

@Injectable({ providedIn: 'root' })
export class BreakpointsService {
    breakpoints: object = {
        '(max-width: 576px)': CustomBreakpointNames.extraSmall,
        '(min-width: 576px)': CustomBreakpointNames.small,
        '(min-width: 768px)': CustomBreakpointNames.medium,
        '(min-width: 992px)': CustomBreakpointNames.large,
        '(min-width: 1200px)': CustomBreakpointNames.extraLarge,
    };

    constructor() {}

    getBreakpoints(): string[] {
        return Object.keys(this.breakpoints);
    }

    getBreakpointName(breakpointValue: string): string {
        return this.breakpoints[breakpointValue];
    }
}
