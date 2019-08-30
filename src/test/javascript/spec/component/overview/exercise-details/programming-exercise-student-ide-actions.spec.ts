import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisOverviewModule, ProgrammingExerciseStudentIdeActionsComponent } from 'app/overview';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';
import { CourseExerciseService } from 'app/entities/course';
import { SinonSpy, SinonStub } from 'sinon';
import { Exercise } from 'app/entities/exercise';
import { StudentParticipation } from 'app/entities/participation';
import { ArtemisTestModule } from '../../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { MockJavaBridgeService } from '../../../mocks/mock-java-bridge.service';
import { MockCourseExerciseService } from '../../../mocks/mock-course-exercise.service';
import { BrowserDynamicTestingModule } from '@angular/platform-browser-dynamic/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IntelliJState } from 'app/intellij/intellij';
import { BehaviorSubject } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';
import { MockAlertService } from '../../../helpers/mock-alert.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseStudentIdeActionsComponent', () => {
    let comp: ProgrammingExerciseStudentIdeActionsComponent;
    let fixture: ComponentFixture<ProgrammingExerciseStudentIdeActionsComponent>;
    let debugElement: DebugElement;
    let javaBridge: JavaBridgeService;
    let courseExerciseService: CourseExerciseService;

    let startExerciseStub: SinonStub;
    let ideStateStub: SinonStub;
    let cloneSpy: SinonSpy;
    let submitSpy: SinonSpy;

    const participation = { id: 1 } as StudentParticipation;
    const exercise = { id: 42 } as Exercise;
    const ideState = { opened: 40 } as IntelliJState;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), NgbModule, ArtemisOverviewModule],
            providers: [
                { provide: JavaBridgeService, useClass: MockJavaBridgeService },
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: JhiAlertService, useClass: MockAlertService },
            ],
        })
            .overrideModule(BrowserDynamicTestingModule, { set: { entryComponents: [FaIconComponent] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseStudentIdeActionsComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                javaBridge = debugElement.injector.get(JavaBridgeService);
                courseExerciseService = debugElement.injector.get(CourseExerciseService);
                startExerciseStub = stub(courseExerciseService, 'startExercise');
                cloneSpy = spy(javaBridge, 'clone');
                submitSpy = spy(javaBridge, 'submit');
                ideStateStub = stub(javaBridge, 'state');
            });
    });

    afterEach(() => {
        startExerciseStub.restore();
        cloneSpy.restore();
        submitSpy.restore();
    });

    it('should not reflect that the represented exercise is opened if another exercise has been opened', fakeAsync(() => {
        const stateObservable = new BehaviorSubject(ideState);
        comp.exercise = exercise;
        ideStateStub.returns(stateObservable);

        comp.ngOnInit();
        fixture.detectChanges();
        tick();

        expect(comp.isOpenedInIntelliJ).to.be.false;

        fixture.destroy();
        flush();
    }));
});
