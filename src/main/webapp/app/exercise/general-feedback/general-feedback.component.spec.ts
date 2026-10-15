import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GeneralFeedbackComponent } from 'app/exercise/general-feedback/general-feedback.component';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';

describe('GeneralFeedbackComponent', () => {
    let fixture: ComponentFixture<GeneralFeedbackComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [GeneralFeedbackComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(GeneralFeedbackComponent, {
                remove: { imports: [UnifiedFeedbackComponent] },
                add: { imports: [MockComponent(UnifiedFeedbackComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(GeneralFeedbackComponent);
    });

    const section = (): HTMLElement | null => fixture.nativeElement.querySelector('[data-testid="general-feedback"]');

    it('should show the general feedback under its heading with the count', () => {
        const feedbacks: Feedback[] = [
            { id: 1, type: FeedbackType.MANUAL_UNREFERENCED, detailText: 'Well structured.', credits: 2 },
            { id: 2, type: FeedbackType.MANUAL_UNREFERENCED, detailText: 'Missing tests.', credits: -1 },
        ];
        fixture.componentRef.setInput('feedbacks', feedbacks);
        fixture.detectChanges();

        expect(section()?.querySelector('[jhiTranslate="artemisApp.assessment.generalFeedback"]')).not.toBeNull();
        expect(section()?.querySelector('.general-feedback__count')?.textContent?.trim()).toBe('2');
        expect(section()?.querySelectorAll('jhi-unified-feedback')).toHaveLength(2);
    });

    it('should set the section apart with a rule only when asked to', () => {
        fixture.componentRef.setInput('feedbacks', [{ id: 1, type: FeedbackType.MANUAL_UNREFERENCED, detailText: 'Well structured.', credits: 2 }] as Feedback[]);
        fixture.detectChanges();
        expect(section()?.classList).not.toContain('general-feedback--separated');

        fixture.componentRef.setInput('separated', true);
        fixture.detectChanges();
        expect(section()?.classList).toContain('general-feedback--separated');
    });

    it('should leave out its heading when a tab already names it', () => {
        fixture.componentRef.setInput('feedbacks', [{ id: 1, type: FeedbackType.MANUAL_UNREFERENCED, detailText: 'Well structured.', credits: 2 }] as Feedback[]);
        fixture.componentRef.setInput('showTitle', false);
        fixture.detectChanges();

        expect(section()?.querySelector('.general-feedback__title')).toBeNull();
        expect(section()?.querySelectorAll('jhi-unified-feedback')).toHaveLength(1);
    });

    it('should lay the cards out identically whichever options the host sets', () => {
        fixture.componentRef.setInput('feedbacks', [{ id: 1, type: FeedbackType.MANUAL_UNREFERENCED, detailText: 'Well structured.', credits: 2 }] as Feedback[]);
        const listClass = (): string => section()!.querySelector('ul')!.className;

        // The options the four exercise types set: text and file upload separate the section, programming drops the title (its tab
        // names the section), and modeling sets neither. The card layout must not depend on any of them.
        const layouts = new Set<string>();
        for (const options of [{}, { separated: true }, { showTitle: false }, { separated: true, showTitle: false }]) {
            fixture.componentRef.setInput('separated', false);
            fixture.componentRef.setInput('showTitle', true);
            Object.entries(options).forEach(([name, value]) => fixture.componentRef.setInput(name, value));
            fixture.detectChanges();
            layouts.add(listClass());
        }

        expect([...layouts]).toEqual(['general-feedback__list']);
    });

    it('should render nothing without general feedback', () => {
        fixture.componentRef.setInput('feedbacks', []);
        fixture.detectChanges();
        expect(section()).toBeNull();

        fixture.componentRef.setInput('feedbacks', undefined);
        fixture.detectChanges();
        expect(section()).toBeNull();
    });
});
