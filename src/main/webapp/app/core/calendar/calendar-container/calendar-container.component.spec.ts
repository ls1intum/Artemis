import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CalendarContainerComponent } from './calendar-container.component';
import { BreakpointObserver, BreakpointState, Breakpoints } from '@angular/cdk/layout';
import { BehaviorSubject, of } from 'rxjs';
import { CalendarDesktopOverviewComponent } from 'app/core/calendar/desktop/overview/calendar-desktop-overview.component';
import { CalendarMobileOverviewComponent } from 'app/core/calendar/mobile/overview/calendar-mobile-overview.component';
import { Component } from '@angular/core';

@Component({ selector: 'jhi-calendar-desktop-overview', template: '' })
class StubCalendarDesktopOverviewComponent {}

@Component({ selector: 'jhi-calendar-mobile-overview', template: '' })
class StubCalendarMobileOverviewComponent {}
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CalendarContainerComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CalendarContainerComponent;
    let fixture: ComponentFixture<CalendarContainerComponent>;
    let breakpoint$: BehaviorSubject<BreakpointState>;

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(async () => {
        breakpoint$ = new BehaviorSubject<BreakpointState>({
            matches: false,
            breakpoints: { [Breakpoints.Handset]: false },
        });

        await TestBed.configureTestingModule({
            imports: [CalendarContainerComponent, StubCalendarDesktopOverviewComponent, StubCalendarMobileOverviewComponent],
            providers: [
                { provide: BreakpointObserver, useValue: { observe: () => breakpoint$.asObservable() } },
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
