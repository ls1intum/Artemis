import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ScoreDisplayComponent', () => {
    let fixture: ComponentFixture<ScoreDisplayComponent>;
    let component: ScoreDisplayComponent;

    const course = { accuracyOfScores: 2 } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ScoreDisplayComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ScoreDisplayComponent);
                component = fixture.componentInstance;

                fixture.detectChanges();
                component.course = course;
            });
    });

    it('should set bonus points correctly if score > maxScore', () => {
        component.score = 15.3334;
        component.maxPoints = 10;
        component.maxBonusPoints = 10;

        component.ngOnChanges();

        expect(component.bonusPoints).toBe(5.33);
        expect(component.maxPointsWithBonus).toBe(20);
        expect(component.maxPercentage).toBe(200);
        expect(component.score).toBe(15.33);
    });

    it('should set bonus points as undefined if score < maxScore', () => {
        component.score = 9.555;
        component.maxPoints = 10;
        component.maxBonusPoints = 5;

        component.ngOnChanges();

        expect(component.bonusPoints).toBeUndefined();
        expect(component.maxPointsWithBonus).toBe(15);
        expect(component.maxPercentage).toBe(150);
        expect(component.score).toBe(9.56);
    });

    it('should set bonus points, max points with bonus and max percentage to undefined if maxBonusPoints is 0', () => {
        component.score = 4.722;
        component.maxPoints = 10;
        component.maxBonusPoints = 0;

        component.ngOnChanges();

        expect(component.bonusPoints).toBeUndefined();
        expect(component.maxPointsWithBonus).toBeUndefined();
        expect(component.maxPercentage).toBeUndefined();
        expect(component.score).toBe(4.72);
    });

    it('should set bonus points, max points with bonus and max percentage to undefined if maxPoints is undefined', () => {
        component.score = 1.3782;

        component.ngOnChanges();

        expect(component.bonusPoints).toBeUndefined();
        expect(component.maxPointsWithBonus).toBeUndefined();
        expect(component.maxPercentage).toBeUndefined();
        expect(component.score).toBe(1.38);
    });
});
