import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialGroupSessionRowComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-session-row/tutorial-group-session-row.component';
import { generateExampleTutorialGroupSession } from '../../../helpers/tutorialGroupSessionExampleModels';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockPipe } from 'ng-mocks';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { By } from '@angular/platform-browser';

@Component({ selector: 'jhi-tutorial-group-session-row-buttons-component', template: '' })
class TutorialGroupSessionRowButtonsStubComponent {
    @Input() courseId: number;
    @Input() tutorialGroupId: number;
    @Input() tutorialGroupSession: TutorialGroupSession;

    @Output() tutorialGroupSessionDeleted = new EventEmitter<void>();
    @Output() cancelOrActivatePressed = new EventEmitter<void>();
}

describe('TutorialGroupSessionRowComponent', () => {
    let component: TutorialGroupSessionRowComponent;
    let fixture: ComponentFixture<TutorialGroupSessionRowComponent>;
    let session: TutorialGroupSession;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TutorialGroupSessionRowComponent, MockPipe(ArtemisDatePipe), MockPipe(ArtemisTranslatePipe), TutorialGroupSessionRowButtonsStubComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupSessionRowComponent);
        component = fixture.componentInstance;
        session = generateExampleTutorialGroupSession();
        component.session = session;
        component.courseId = 1;
        component.tutorialGroupId = 1;
        component.timeZone = 'Europe/Berlin';
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set class of cancelled sessions correctly', () => {
        component.session.status = TutorialGroupSessionStatus.CANCELLED;
        component.ngOnChanges();
        fixture.detectChanges();
        const thirdColumn = fixture.debugElement.query(By.css('td:nth-child(3)'));
        expect(thirdColumn.nativeElement.classList).toContain('table-danger');
    });

    it('should set class of sessions without schedule correctly', () => {
        component.session.tutorialGroupSchedule = undefined;
        component.ngOnChanges();
        fixture.detectChanges();

        const secondColumn = fixture.debugElement.query(By.css('td:nth-child(2)'));
        expect(secondColumn.nativeElement.classList).toContain('table-warning');
    });

    it('should emit actionPerformed event when cancel or activate is performed', () => {
        const rowButtons = fixture.debugElement.query(By.directive(TutorialGroupSessionRowButtonsStubComponent)).componentInstance as TutorialGroupSessionRowButtonsStubComponent;
        const emitSpy = jest.spyOn(component.actionPerformed, 'emit');
        rowButtons.cancelOrActivatePressed.emit();
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should emit actionPerformed event when delete is performed', () => {
        const rowButtons = fixture.debugElement.query(By.directive(TutorialGroupSessionRowButtonsStubComponent)).componentInstance as TutorialGroupSessionRowButtonsStubComponent;
        const emitSpy = jest.spyOn(component.actionPerformed, 'emit');
        rowButtons.tutorialGroupSessionDeleted.emit();
        expect(emitSpy).toHaveBeenCalled();
    });
});
