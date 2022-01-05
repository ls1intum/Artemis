import { Injectable } from '@angular/core';

export const CustomBreakpointNames = {
    extraSmall: 'extraSmall',
    small: 'small',
    medium: 'medium',
    large: 'large',
    extraLarge: 'extraLarge',
};

@Injectable({
    providedIn: 'root',
})
export class BreakpointsService {
    breakpoints = new Map<string, string>();

    constructor() {
        this.breakpoints.set('(max-width: 576px)', CustomBreakpointNames.extraSmall);
        this.breakpoints.set('(min-width: 576px)', CustomBreakpointNames.small);
        this.breakpoints.set('(min-width: 768px)', CustomBreakpointNames.medium);
        this.breakpoints.set('(min-width: 992px)', CustomBreakpointNames.large);
        this.breakpoints.set('(min-width: 1200px)', CustomBreakpointNames.extraLarge);
    }

    getBreakpoints(): string[] {
        return Array.from(this.breakpoints.keys());
    }

    getBreakpointName(breakpointKey: string): string {
        return this.breakpoints.get(breakpointKey)!;
    }
}
