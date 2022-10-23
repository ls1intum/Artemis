import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialGroupSessionRowComponent } from 'app/course/tutorial-groups/shared/tutorial-group-sessions-table/tutorial-group-session-row/tutorial-group-session-row.component';
import { generateExampleTutorialGroupSession } from '../../../helpers/tutorialGroupSessionExampleModels';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { By } from '@angular/platform-browser';

describe('TutorialGroupSessionRowComponent', () => {
    let component: TutorialGroupSessionRowComponent;
    let fixture: ComponentFixture<TutorialGroupSessionRowComponent>;
    let session: TutorialGroupSession;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupSessionRowComponent, MockPipe(ArtemisDatePipe), MockPipe(ArtemisTranslatePipe)],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupSessionRowComponent);
        component = fixture.componentInstance;
        session = generateExampleTutorialGroupSession({});
        component.session = session;
        component.timeZone = 'Europe/Berlin';
        component.showIdColumn = true;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set class of cancelled sessions correctly', () => {
        component.session = { ...session, status: TutorialGroupSessionStatus.CANCELLED };
        fixture.detectChanges();

        const thirdColumn = fixture.debugElement.query(By.css('td:nth-child(4)'));
        expect(thirdColumn.nativeElement.classList).toContain('table-danger');
    });

    it('should set class of sessions without schedule correctly', () => {
        component.session = { ...session, tutorialGroupSchedule: undefined };
        fixture.detectChanges();

        const thirdColumn = fixture.debugElement.query(By.css('td:nth-child(3)'));
        expect(thirdColumn.nativeElement.classList).toContain('table-warning');
    });
});
