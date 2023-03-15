import { BreakpointObserver } from '@angular/cdk/layout';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { BreakpointsService } from './breakpoints.service';

@Injectable({
    providedIn: 'root',
})
export class LayoutService {
    activeBreakpoints: string[] = [];

    constructor(private breakpointObserver: BreakpointObserver, private breakpointService: BreakpointsService) {}

    subscribeToLayoutChanges(): Observable<string[]> {
        return this.breakpointObserver.observe(this.breakpointService.getBreakpoints()).pipe(map((observeResponse) => this.parseBreakpointsResponse(observeResponse.breakpoints)));
    }

    parseBreakpointsResponse(breakpoints: { [key: string]: boolean }): string[] {
        this.activeBreakpoints = [];

        Object.keys(breakpoints).map((key) => {
            if (breakpoints[key]) {
                this.activeBreakpoints.push(this.breakpointService.getBreakpointName(key));
            }
        });

        return this.activeBreakpoints;
    }

    isBreakpointActive(breakpointName: string) {
        return this.activeBreakpoints.find((breakpoint) => breakpoint === breakpointName);
    }
}
