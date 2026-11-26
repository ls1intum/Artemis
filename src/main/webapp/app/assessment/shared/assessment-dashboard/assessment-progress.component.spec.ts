import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AssessmentProgressComponent } from 'app/assessment/shared/assessment-dashboard/assessment-progress.component';

describe('AssessmentProgressComponent', () => {
    let fixture: ComponentFixture<AssessmentProgressComponent>;
    let component: AssessmentProgressComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AssessmentProgressComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(AssessmentProgressComponent);
        component = fixture.componentInstance;
    });

    function setCounts(completed: number, inProgress: number, open: number, total = completed + inProgress + open) {
        fixture.componentRef.setInput('completed', completed);
        fixture.componentRef.setInput('inProgress', inProgress);
        fixture.componentRef.setInput('open', open);
        fixture.componentRef.setInput('total', total);
    }

    it('floors both percentages to whole numbers', () => {
        setCounts(5, 1, 2);
        expect(component.assessedPercentage()).toBe(62);
        expect(component.inProgressPercentage()).toBe(12);
    });

    it('keeps the bar within 100 percent', () => {
        setCounts(3, 2, 0, 4);
        expect(component.assessedPercentage()).toBe(75);
        expect(component.inProgressPercentage()).toBe(25);
    });

    it('reports no progress without assessment slots', () => {
        setCounts(0, 0, 0);
        expect(component.assessedPercentage()).toBe(0);
        expect(component.inProgressPercentage()).toBe(0);
    });

    it.each([true, false])('shows the in-progress legend only when requested (%s)', (showInProgress) => {
        setCounts(1, 1, 1);
        fixture.componentRef.setInput('showInProgress', showInProgress);
        fixture.detectChanges();
        const element: HTMLElement = fixture.nativeElement;
        expect(element.querySelector('[jhiTranslate="artemisApp.assessmentDashboard.inProgress"]') !== null).toBe(showInProgress);
        expect(element.querySelector('[data-testid="remaining-help-icon"]') !== null).toBe(!showInProgress);
        expect((element.querySelector('progress') as HTMLProgressElement).value).toBe(33);
    });
});
