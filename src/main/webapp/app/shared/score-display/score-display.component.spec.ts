import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ScoreDisplayComponent', () => {
    let fixture: ComponentFixture<ScoreDisplayComponent>;
    let component: ScoreDisplayComponent;

    const course = { accuracyOfScores: 2 } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ScoreDisplayComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ScoreDisplayComponent);
                component = fixture.componentInstance;

                fixture.componentRef.setInput('course', course);
                fixture.detectChanges();
            });
    });

    it('should set bonus points correctly if score > maxScore', () => {
        fixture.componentRef.setInput('score', 15.3334);
        fixture.componentRef.setInput('maxPoints', 10);
        fixture.componentRef.setInput('maxBonusPoints', 10);
        fixture.detectChanges();

        expect(component.bonusPoints()).toBe(5.33);
        expect(component.maxPointsWithBonus()).toBe(20);
        expect(component.maxPercentage()).toBe(200);
        expect(component.roundedScore()).toBe(15.33);
    });

    it('should set bonus points as undefined if score < maxScore', () => {
        fixture.componentRef.setInput('score', 9.555);
        fixture.componentRef.setInput('maxPoints', 10);
        fixture.componentRef.setInput('maxBonusPoints', 5);
        fixture.detectChanges();

        expect(component.bonusPoints()).toBeUndefined();
        expect(component.maxPointsWithBonus()).toBe(15);
        expect(component.maxPercentage()).toBe(150);
        expect(component.roundedScore()).toBe(9.56);
    });

    it('should set bonus points, max points with bonus and max percentage to undefined if maxBonusPoints is 0', () => {
        fixture.componentRef.setInput('score', 4.722);
        fixture.componentRef.setInput('maxPoints', 10);
        fixture.componentRef.setInput('maxBonusPoints', 0);
        fixture.detectChanges();

        expect(component.bonusPoints()).toBeUndefined();
        expect(component.maxPointsWithBonus()).toBeUndefined();
        expect(component.maxPercentage()).toBeUndefined();
        expect(component.roundedScore()).toBe(4.72);
    });

    it('should set bonus points, max points with bonus and max percentage to undefined if maxPoints is undefined', () => {
        fixture.componentRef.setInput('score', 1.3782);
        fixture.detectChanges();

        expect(component.bonusPoints()).toBeUndefined();
        expect(component.maxPointsWithBonus()).toBeUndefined();
        expect(component.maxPercentage()).toBeUndefined();
        expect(component.roundedScore()).toBe(1.38);
    });
});
