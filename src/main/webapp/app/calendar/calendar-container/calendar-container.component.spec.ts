import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CalendarContainerComponent } from './calendar-container.component';
import { BreakpointObserver, BreakpointState, Breakpoints } from '@angular/cdk/layout';
import { BehaviorSubject, of } from 'rxjs';
import { CalendarDesktopOverviewComponent } from 'app/calendar/desktop/overview/calendar-desktop-overview.component';
import { CalendarMobileOverviewComponent } from 'app/calendar/mobile/overview/calendar-mobile-overview.component';
import { Component } from '@angular/core';

@Component({ selector: 'jhi-calendar-desktop-overview', template: '' })
class StubCalendarDesktopOverviewComponent {}

@Component({ selector: 'jhi-calendar-mobile-overview', template: '' })
class StubCalendarMobileOverviewComponent {}
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CalendarViewStateService } from 'app/calendar/shared/service/calendar-view-state.service';
import dayjs from 'dayjs/esm';

describe('CalendarContainerComponent', () => {
    let component: CalendarContainerComponent;
    let fixture: ComponentFixture<CalendarContainerComponent>;
    let breakpoint$: BehaviorSubject<BreakpointState>;
    let observedQueries: string[];

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(async () => {
        observedQueries = [];
        breakpoint$ = new BehaviorSubject<BreakpointState>({
            matches: false,
            breakpoints: { [Breakpoints.Handset]: false },
        });

        await TestBed.configureTestingModule({
            imports: [CalendarContainerComponent, StubCalendarDesktopOverviewComponent, StubCalendarMobileOverviewComponent],
            providers: [
                {
                    provide: BreakpointObserver,
                    useValue: {
                        observe: (query: string) => {
                            observedQueries.push(query);
                            return breakpoint$.asObservable();
                        },
                        isMatched: () => false,
                    },
                },
                { provide: ActivatedRoute, useValue: { parent: { paramMap: of({ get: () => '42' }) } } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(CalendarContainerComponent, {
                remove: {
                    imports: [CalendarDesktopOverviewComponent, CalendarMobileOverviewComponent],
                },
                add: {
                    imports: [StubCalendarDesktopOverviewComponent, StubCalendarMobileOverviewComponent],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(CalendarContainerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render desktop overview when not mobile', () => {
        const desktop = fixture.nativeElement.querySelector('jhi-calendar-desktop-overview');
        const mobile = fixture.nativeElement.querySelector('jhi-calendar-mobile-overview');
        expect(desktop).toBeTruthy();
        expect(mobile).toBeFalsy();
    });

    it('should hand over to the mobile overview before the app-wide handset breakpoint', () => {
        // The calendar's title bar is denser than any other page's, so it switches at a width of its own.
        expect(observedQueries).toEqual(['(max-width: 1024px)']);
    });

    it('should keep the displayed period across the switch, which recreates the overview', () => {
        const viewState = fixture.debugElement.injector.get(CalendarViewStateService);
        const october = dayjs('2026-10-01').startOf('month');
        viewState.firstDateOfDisplayedMonth.set(october);

        breakpoint$.next({ matches: true, breakpoints: { [Breakpoints.Handset]: true } });
        fixture.detectChanges();
        breakpoint$.next({ matches: false, breakpoints: { [Breakpoints.Handset]: false } });
        fixture.detectChanges();

        // The service is provided by the container, so it outlives both overviews.
        expect(fixture.debugElement.injector.get(CalendarViewStateService).firstDateOfDisplayedMonth()).toBe(october);
    });

    it('should render mobile overview when screen is small', () => {
        breakpoint$.next({
            matches: true,
            breakpoints: { [Breakpoints.Handset]: true },
        });

        fixture.detectChanges();

        const desktop = fixture.nativeElement.querySelector('jhi-calendar-desktop-overview');
        const mobile = fixture.nativeElement.querySelector('jhi-calendar-mobile-overview');
        expect(desktop).toBeFalsy();
        expect(mobile).toBeTruthy();
    });
});
