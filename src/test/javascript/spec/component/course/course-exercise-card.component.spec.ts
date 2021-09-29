import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { CourseExerciseCardComponent } from 'app/course/manage/course-exercise-card.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import * as chai from 'chai';
import { MockDirective, MockProvider } from 'ng-mocks';
import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';

chai.use(sinonChai);
const expect = chai.expect;

describe('Course Exercise Card Component', () => {
    let fixture: ComponentFixture<CourseExerciseCardComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseExerciseCardComponent, MockDirective(NgbCollapse), MockDirective(TranslateDirective)],
            providers: [MockProvider(CourseManagementService)],
        }).compileComponents();
        fixture = TestBed.createComponent(CourseExerciseCardComponent);
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(CourseExerciseCardComponent).to.be.ok;
    });
});
