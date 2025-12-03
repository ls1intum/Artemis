import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CalendarContainerComponent } from './calendar-container.component';
import { BreakpointObserver, BreakpointState, Breakpoints } from '@angular/cdk/layout';
import { BehaviorSubject } from 'rxjs';
import { MockComponent } from 'ng-mocks';
import { CalendarDesktopOverviewComponent } from 'app/core/calendar/desktop/overview/calendar-desktop-overview.component';
import { CalendarMobileOverviewComponent } from 'app/core/calendar/mobile/overview/calendar-mobile-overview.component';

describe('CalendarContainerComponent', () => {
    let component: CalendarContainerComponent;
    let fixture: ComponentFixture<CalendarContainerComponent>;
    let breakpoint$: BehaviorSubject<BreakpointState>;

    beforeEach(async () => {
        breakpoint$ = new BehaviorSubject<BreakpointState>({
            matches: false,
            breakpoints: { [Breakpoints.Handset]: false },
        });

        await TestBed.configureTestingModule({
            imports: [CalendarContainerComponent, MockComponent(CalendarDesktopOverviewComponent), MockComponent(CalendarMobileOverviewComponent)],
            providers: [{ provide: BreakpointObserver, useValue: { observe: () => breakpoint$.asObservable() } }],
        }).compileComponents();

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
