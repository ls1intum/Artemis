import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';

import { PresentationAssessmentInstanceDetailsComponent } from 'app/presentation/manage/presentation-assessment-instance-details.component';
import { PresentationAssessmentMode } from 'app/presentation/shared/entities/presentation-assessment.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('PresentationAssessmentInstanceDetailsComponent', () => {
    let fixture: ComponentFixture<PresentationAssessmentInstanceDetailsComponent>;

    const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
    const meetingLink = () => (fixture.nativeElement as HTMLElement).querySelector('a');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentInstanceDetailsComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PresentationAssessmentInstanceDetailsComponent);
    });

    it('should not show the presentation description and exercise without a presentation', () => {
        fixture.componentRef.setInput('instance', { language: 'German', mode: PresentationAssessmentMode.IN_PERSON, location: 'Room 1' });
        fixture.detectChanges();

        expect(text()).toContain('German');
        expect(text()).not.toContain('artemisApp.presentationAssessment.description');
        expect(text()).not.toContain('artemisApp.presentationAssessment.exercise');
    });

    it('should show the presentation description and exercise first when a presentation is given', () => {
        fixture.componentRef.setInput('instance', { language: 'German', mode: PresentationAssessmentMode.IN_PERSON });
        fixture.componentRef.setInput('presentationAssessment', { title: 'Final', maxPoints: 20, description: 'Ten minutes', exerciseTitle: 'Sorting' });
        fixture.detectChanges();

        expect(text()).toContain('Ten minutes');
        expect(text()).toContain('Sorting');
        expect(text().indexOf('Ten minutes')).toBeLessThan(text().indexOf('German'));
    });

    it('should link to the meeting and omit the location for an online presentation', () => {
        fixture.componentRef.setInput('instance', {
            mode: PresentationAssessmentMode.ONLINE,
            meetingLink: 'https://example.org/meeting',
            location: 'Room 1',
        });
        fixture.detectChanges();

        expect(meetingLink()?.getAttribute('href')).toBe('https://example.org/meeting');
        expect(meetingLink()?.getAttribute('rel')).toBe('noopener noreferrer');
        expect(text()).not.toContain('Room 1');
    });

    it('should show the location and no link for an in-person presentation', () => {
        fixture.componentRef.setInput('instance', {
            mode: PresentationAssessmentMode.IN_PERSON,
            meetingLink: 'https://example.org/meeting',
            location: 'Room 1',
        });
        fixture.detectChanges();

        expect(text()).toContain('Room 1');
        expect(meetingLink()).toBeNull();
    });
});
