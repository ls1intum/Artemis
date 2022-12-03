import { Component, Input, QueryList, SimpleChange, SimpleChanges, ViewChild, ViewChildren } from '@angular/core';
import { TutorialGroupSessionsTableComponent } from 'app/course/tutorial-groups/shared/tutorial-group-sessions-table/tutorial-group-sessions-table.component';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroupSessionRowStubComponent } from '../stubs/tutorial-group-sessions-table-stub.component';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { generateExampleTutorialGroupSession } from '../helpers/tutorialGroupSessionExampleModels';
import dayjs from 'dayjs/esm';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

@Component({ selector: 'jhi-mock-extra-column', template: '' })
class MockExtraColumn {
    @Input() tutorialGroupSession: TutorialGroupSession;
}

@Component({
    selector: 'jhi-mock-wrapper',
    template: `
        <jhi-tutorial-group-sessions-table [sessions]="sessions" [timeZone]="timeZone" [showIdColumn]="true" [tutorialGroup]="tutorialGroup">
            <ng-template let-session>
                <jhi-mock-extra-column [tutorialGroupSession]="session"></jhi-mock-extra-column>
            </ng-template>
        </jhi-tutorial-group-sessions-table>
    `,
})
class MockWrapper {
    @Input()
    tutorialGroup: TutorialGroup;

    @Input()
    sessions: TutorialGroupSession[];

    @Input()
    timeZone: string;

    @ViewChild(TutorialGroupSessionsTableComponent)
    sessionTableInstance: TutorialGroupSessionsTableComponent;

    @ViewChildren(MockExtraColumn)
    mockExtraColumns: QueryList<MockExtraColumn>;
}

describe('TutorialGroupSessionsTableWrapperTest', () => {
    let fixture: ComponentFixture<MockWrapper>;
    let component: MockWrapper;
    let tableInstance: TutorialGroupSessionsTableComponent;
    let mockExtraColumns: MockExtraColumn[];
    let sessionOne: TutorialGroupSession;
    let sessionTwo: TutorialGroupSession;
    let tutorialGroup: TutorialGroup;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupSessionsTableComponent,
                TutorialGroupSessionRowStubComponent,
                MockWrapper,
                MockExtraColumn,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(NgbCollapse),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MockWrapper);
                component = fixture.componentInstance;
                tutorialGroup = generateExampleTutorialGroup({});
                sessionOne = generateExampleTutorialGroupSession({ id: 1 });
                sessionTwo = generateExampleTutorialGroupSession({ id: 2 });
                component.sessions = [sessionOne, sessionTwo];
                component.timeZone = 'Europe/Berlin';
                component.tutorialGroup = tutorialGroup;
                fixture.detectChanges();
                tableInstance = component.sessionTableInstance;
                mockExtraColumns = component.mockExtraColumns.toArray();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should pass the session to the headers', () => {
        expect(tableInstance.sessions).toEqual([sessionOne, sessionTwo]);
        expect(tableInstance.timeZone).toBe('Europe/Berlin');
        expect(mockExtraColumns).toHaveLength(2);
        mockExtraColumns.sort((a, b) => a.tutorialGroupSession!.id! - b.tutorialGroupSession!.id!);
        expect(mockExtraColumns[0].tutorialGroupSession).toEqual(sessionOne);
        expect(mockExtraColumns[1].tutorialGroupSession).toEqual(sessionTwo);
        expect(fixture.nativeElement.querySelectorAll('jhi-mock-extra-column')).toHaveLength(2);
    });

    it('should return the correct number of columns', () => {
        fixture.detectChanges();
        expect(tableInstance.numberOfColumns).toBe(5);
    });
});

describe('TutorialGroupSessionTableComponent', () => {
    let fixture: ComponentFixture<TutorialGroupSessionsTableComponent>;
    let component: TutorialGroupSessionsTableComponent;
    let pastSession: TutorialGroupSession;
    let upcomingSession: TutorialGroupSession;
    let tutorialGroup: TutorialGroup;
    const timeZone = 'Europe/Berlin';
    const currentDate = dayjs(new Date(Date.UTC(2021, 0, 2, 12, 0, 0)));

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupSessionsTableComponent,
                TutorialGroupSessionRowStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(NgbCollapse),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupSessionsTableComponent);
                component = fixture.componentInstance;

                pastSession = generateExampleTutorialGroupSession({
                    id: 1,
                    start: dayjs('2021-01-01T12:00:00.000Z').tz('Europe/Berlin'),
                    end: dayjs('2021-01-01T13:00:00.000Z').tz('Europe/Berlin'),
                    location: 'Room 1',
                });
                upcomingSession = generateExampleTutorialGroupSession({
                    id: 2,
                    start: dayjs('2021-01-03T12:00:00.000Z').tz('Europe/Berlin'),
                    end: dayjs('2021-01-03T13:00:00.000Z').tz('Europe/Berlin'),
                    location: 'Room 1',
                });
                tutorialGroup = generateExampleTutorialGroup({});

                component.sessions = [upcomingSession, pastSession];
                component.tutorialGroup = tutorialGroup;
                component.timeZone = timeZone;
                jest.spyOn(component, 'getCurrentDate').mockReturnValue(currentDate);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should split sessions into upcoming and past', () => {
        fixture.detectChanges();
        const changes = {} as SimpleChanges;
        changes.sessions = new SimpleChange([], component.sessions, true);
        component.ngOnChanges(changes);
        expect(component.upcomingSessions).toHaveLength(1);
        expect(component.upcomingSessions).toEqual([upcomingSession]);
        expect(component.pastSessions).toHaveLength(1);
        expect(component.pastSessions).toEqual([pastSession]);
    });

    it('should return the correct number of columns', () => {
        fixture.detectChanges();
        expect(component.numberOfColumns).toBe(3);
    });
});
