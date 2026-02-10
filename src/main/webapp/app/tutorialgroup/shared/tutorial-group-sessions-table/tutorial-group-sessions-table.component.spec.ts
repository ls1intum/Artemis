import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component, input, viewChild, viewChildren } from '@angular/core';
import { TutorialGroupSessionDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroupSessionRowStubComponent } from 'test/helpers/stubs/tutorialgroup/tutorial-group-sessions-table-stub.component';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { generateExampleTutorialGroupSessionDTO } from 'test/helpers/sample/tutorialgroup/tutorialGroupSessionExampleModels';
import dayjs from 'dayjs/esm';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupSessionsTableComponent } from 'app/tutorialgroup/shared/tutorial-group-sessions-table/tutorial-group-sessions-table.component';

@Component({ selector: 'jhi-mock-extra-column', template: '' })
class MockExtraColumnComponent {
    tutorialGroupSession = input<TutorialGroupSessionDTO>();
}

@Component({
    selector: 'jhi-mock-wrapper',
    template: `
        <jhi-tutorial-group-sessions-table [sessions]="sessions()" [timeZone]="timeZone()" [showIdColumn]="true" [tutorialGroup]="tutorialGroup()">
            <ng-template let-session>
                <jhi-mock-extra-column [tutorialGroupSession]="session()" />
            </ng-template>
        </jhi-tutorial-group-sessions-table>
    `,
    imports: [TutorialGroupSessionsTableComponent, MockExtraColumnComponent],
})
class MockWrapperComponent {
    readonly tutorialGroup = input.required<TutorialGroup>();
    readonly sessions = input.required<TutorialGroupSessionDTO[]>();
    readonly timeZone = input.required<string>();

    sessionTableInstance = viewChild.required(TutorialGroupSessionsTableComponent);

    mockExtraColumns = viewChildren(MockExtraColumnComponent);
}

describe('TutorialGroupSessionsTableWrapperTest', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<MockWrapperComponent>;
    let component: MockWrapperComponent;
    let tableInstance: TutorialGroupSessionsTableComponent;
    let mockExtraColumns: MockExtraColumnComponent[];
    let sessionOne: TutorialGroupSessionDTO;
    let sessionTwo: TutorialGroupSessionDTO;
    let tutorialGroup: TutorialGroup;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                TutorialGroupSessionsTableComponent,
                TutorialGroupSessionRowStubComponent,
                MockWrapperComponent,
                MockExtraColumnComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(TranslateDirective),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MockWrapperComponent);
                component = fixture.componentInstance;
                tutorialGroup = generateExampleTutorialGroup({});
                sessionOne = generateExampleTutorialGroupSessionDTO({ id: 1 });
                sessionTwo = generateExampleTutorialGroupSessionDTO({ id: 2 });
                fixture.componentRef.setInput('timeZone', 'Europe/Berlin');
                fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
                fixture.componentRef.setInput('sessions', [sessionOne, sessionTwo]);
                fixture.detectChanges();
                tableInstance = component.sessionTableInstance();
                mockExtraColumns = [...component.mockExtraColumns()]; // spread to make mutable
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should pass the session to the headers', () => {
        expect(tableInstance.sessions()).toEqual([sessionOne, sessionTwo]);
        expect(tableInstance.timeZone()).toBe('Europe/Berlin');
        expect(mockExtraColumns).toHaveLength(2);
        mockExtraColumns.sort((a, b) => a.tutorialGroupSession()!.id! - b.tutorialGroupSession()!.id!);
        expect(mockExtraColumns[0].tutorialGroupSession()).toEqual(sessionOne);
        expect(mockExtraColumns[1].tutorialGroupSession()).toEqual(sessionTwo);
        expect(fixture.nativeElement.querySelectorAll('jhi-mock-extra-column')).toHaveLength(2);
    });

    it('should return the correct number of columns', () => {
        expect(tableInstance.numberOfColumns).toBe(6);
    });
});

describe('TutorialGroupSessionTableComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TutorialGroupSessionsTableComponent>;
    let component: TutorialGroupSessionsTableComponent;
    let pastSession: TutorialGroupSessionDTO;
    let upcomingSession: TutorialGroupSessionDTO;
    let tutorialGroup: TutorialGroup;
    const timeZone = 'Europe/Berlin';
    const currentDate = dayjs(new Date(Date.UTC(2021, 0, 2, 12, 0, 0)));

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                TutorialGroupSessionsTableComponent,
                TutorialGroupSessionRowStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(TranslateDirective),
            ],
            providers: [provideHttpClient(), MockProvider(AlertService), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupSessionsTableComponent);
                component = fixture.componentInstance;

                pastSession = generateExampleTutorialGroupSessionDTO({
                    id: 1,
                    startDate: dayjs('2021-01-01T12:00:00.000Z').tz('Europe/Berlin'),
                    endDate: dayjs('2021-01-01T13:00:00.000Z').tz('Europe/Berlin'),
                    location: 'Room 1',
                });
                upcomingSession = generateExampleTutorialGroupSessionDTO({
                    id: 2,
                    startDate: dayjs('2021-01-03T12:00:00.000Z').tz('Europe/Berlin'),
                    endDate: dayjs('2021-01-03T13:00:00.000Z').tz('Europe/Berlin'),
                    location: 'Room 1',
                });
                tutorialGroup = generateExampleTutorialGroup({});
                tutorialGroup.nextSession = upcomingSession;

                fixture.componentRef.setInput('sessions', [upcomingSession, pastSession]);
                fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
                fixture.componentRef.setInput('timeZone', timeZone);
                vi.spyOn(component, 'getCurrentDate').mockReturnValue(currentDate);
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should sync next session and upcoming sessions when attendance changed', () => {
        // Effects run automatically when inputs change via setInput
        fixture.detectChanges();

        const sessionWithAttendanceData = { ...upcomingSession, attendanceCount: 1 } as TutorialGroupSessionDTO;
        component.onAttendanceChanged(sessionWithAttendanceData);
        fixture.detectChanges();
        expect(component.nextSession).toEqual(sessionWithAttendanceData);
        expect(component.upcomingSessions[0]).toEqual(sessionWithAttendanceData);
        expect(component.pastSessions[0]).toEqual(pastSession);
    });

    it('should sync next session and past sessions when attendance changed', () => {
        // Create a new object to trigger the signal change
        fixture.componentRef.setInput('tutorialGroup', { ...tutorialGroup, nextSession: pastSession });
        fixture.detectChanges();

        const sessionWithAttendanceData = { ...pastSession, attendanceCount: 1 } as TutorialGroupSessionDTO;
        component.onAttendanceChanged(sessionWithAttendanceData);
        fixture.detectChanges();
        expect(component.nextSession).toEqual(sessionWithAttendanceData);
        expect(component.upcomingSessions[0]).toEqual(upcomingSession);
        expect(component.pastSessions[0]).toEqual(sessionWithAttendanceData);
    });

    it('should split sessions into upcoming and past', () => {
        // Effects run automatically when inputs are set
        fixture.detectChanges();
        expect(component.upcomingSessions).toHaveLength(1);
        expect(component.upcomingSessions).toEqual([upcomingSession]);
        expect(component.pastSessions).toHaveLength(1);
        expect(component.pastSessions).toEqual([pastSession]);
        expect(component.nextSession).toEqual(upcomingSession);
    });

    it('should return the correct number of columns', () => {
        expect(component.numberOfColumns).toBe(4);
    });
});
