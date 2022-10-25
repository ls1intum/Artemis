import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialGroupSessionRowComponent } from 'app/course/tutorial-groups/shared/tutorial-group-sessions-table/tutorial-group-session-row/tutorial-group-session-row.component';
import { generateExampleTutorialGroupSession } from '../../../helpers/tutorialGroupSessionExampleModels';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { By } from '@angular/platform-browser';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';

describe('TutorialGroupSessionRowComponent', () => {
    let component: TutorialGroupSessionRowComponent;
    let fixture: ComponentFixture<TutorialGroupSessionRowComponent>;
    let session: TutorialGroupSession;
    let tutorialGroup: TutorialGroup;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupSessionRowComponent, MockPipe(ArtemisDatePipe), MockPipe(ArtemisTranslatePipe), MockDirective(NgbPopover)],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupSessionRowComponent);
        component = fixture.componentInstance;
        session = generateExampleTutorialGroupSession({});
        tutorialGroup = generateExampleTutorialGroup({});
        component.session = session;
        component.tutorialGroup = tutorialGroup;
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
        component.ngOnChanges();

        // all columns should have the table danger class
        const tableCells = fixture.debugElement.queryAll(By.css('td'));
        tableCells.forEach((tableCell) => {
            expect(tableCell.nativeElement.classList).toContain('table-danger');
        });
    });
});
