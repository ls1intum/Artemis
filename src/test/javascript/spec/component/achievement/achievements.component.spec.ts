import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { AchievementsComponent } from 'app/achievements/achievements.component';
import { Achievement, AchievementRank, AchievementType } from 'app/entities/achievement.model';

chai.use(sinonChai);
const expect = chai.expect;
const achievement = { id: 1, title: 'test title', description: 'test description', icon: 'test icon', rank: AchievementRank.UNRANKED, type: AchievementType.POINT } as Achievement;
const achievementArray: Achievement[] = [achievement];

describe('Achievement Component', () => {
    let achievementsComponent: AchievementsComponent;
    let achievementsComponentFixture: ComponentFixture<AchievementsComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule],
            declarations: [AchievementsComponent],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                achievementsComponentFixture = TestBed.createComponent(AchievementsComponent);
                achievementsComponent = achievementsComponentFixture.componentInstance;
            });
    });

    describe('Initialization', () => {
        it('should initialize the component', () => {
            const fake = function () {
                achievementsComponent.achievements = achievementArray;
            };
            sinon.replace(achievementsComponent, 'ngOnInit', fake);
            expect(achievementsComponent).to.exist;
        });
    });
});
