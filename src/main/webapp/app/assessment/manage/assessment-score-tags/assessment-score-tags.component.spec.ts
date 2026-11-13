import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AssessmentScoreTagsComponent } from 'app/assessment/manage/assessment-score-tags/assessment-score-tags.component';
import { Course } from 'app/course/shared/entities/course.model';

describe('AssessmentScoreTagsComponent', () => {
    let fixture: ComponentFixture<AssessmentScoreTagsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AssessmentScoreTagsComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(AssessmentScoreTagsComponent);
        // 100 points split across 13 equally weighted tests leave a fraction with every digit
        fixture.componentRef.setInput('score', { awarded: 100 / 13, deducted: -1, total: 100 / 13 - 1 });
        fixture.componentRef.setInput('maxPoints', 100);
    });

    const roundedScore = () => (fixture.componentInstance as any).roundedScore();

    it('should show the awarded, deducted and final points as tags', () => {
        fixture.detectChanges();

        const tags = ['assessment-awarded-points', 'assessment-deducted-points', 'assessment-final-points'];
        tags.forEach((testId) => expect(fixture.nativeElement.querySelector(`[data-testid="${testId}"]`)).not.toBeNull());
    });

    it('should round the points to two decimals without a course', () => {
        expect(roundedScore()).toEqual({ awarded: 7.69, deducted: -1, total: 6.69 });
    });

    it('should round the points like the course', () => {
        fixture.componentRef.setInput('course', { accuracyOfScores: 1 } as Course);

        expect(roundedScore()).toEqual({ awarded: 7.7, deducted: -1, total: 6.7 });
    });
});
