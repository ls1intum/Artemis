import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import { MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../test.module';

describe('ScoreDisplayComponent', () => {
    let fixture: ComponentFixture<ScoreDisplayComponent>;
    let component: ScoreDisplayComponent;

    const course = { accuracyOfScores: 2 } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ScoreDisplayComponent, MockPipe(ArtemisTranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ScoreDisplayComponent);
                component = fixture.componentInstance;

                component.ngOnInit();
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
