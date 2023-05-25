import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { NgbPopoverModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTestModule } from '../../test.module';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Subject } from 'rxjs';
import { ResetRepoButtonComponent } from 'app/shared/components/reset-repo-button/reset-repo-button.component';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { MockParticipationService } from '../../helpers/mocks/service/mock-participation.service';

describe('JhiResetRepoButtonComponent', () => {
    let comp: ResetRepoButtonComponent;
    let fixture: ComponentFixture<ResetRepoButtonComponent>;

    let resetRepositoryStub: jest.SpyInstance;

    const gradedParticipation: StudentParticipation = { id: 1, testRun: false };
    const practiceParticipation: StudentParticipation = { id: 2, testRun: true };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbPopoverModule],
            declarations: [ResetRepoButtonComponent, MockComponent(ExerciseActionButtonComponent), MockPipe(ArtemisTranslatePipe), MockDirective(FeatureToggleDirective)],
            providers: [
                { provide: ParticipationService, useClass: MockParticipationService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ResetRepoButtonComponent);
        comp = fixture.componentInstance;
        const programmingExerciseParticipationService = fixture.debugElement.injector.get(ProgrammingExerciseParticipationService);

        resetRepositoryStub = jest.spyOn(programmingExerciseParticipationService, 'resetRepository');
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize correctly', () => {
        comp.participations = [gradedParticipation, practiceParticipation];
        comp.ngOnInit();

        expect(comp.gradedParticipation).toEqual(gradedParticipation);
        expect(comp.practiceParticipation).toEqual(practiceParticipation);
    });

    it.each([
        { participations: [gradedParticipation, practiceParticipation], expectedResetId: practiceParticipation.id, gradedParticipationId: gradedParticipation.id },
        { participations: [gradedParticipation, practiceParticipation], expectedResetId: practiceParticipation.id },
        { participations: [practiceParticipation], expectedResetId: practiceParticipation.id },
        { participations: [gradedParticipation], expectedResetId: gradedParticipation.id },
    ])('should reset repository correctly', ({ participations, expectedResetId, gradedParticipationId }) => {
        const resetSubject = new Subject<void>();

        comp.participations = participations;
        comp.exercise = { id: 3 } as ProgrammingExercise;
        comp.ngOnInit();

        resetRepositoryStub.mockReturnValue(resetSubject);
        comp.resetRepository(gradedParticipationId);
        expect(comp.exercise.loading).toBeTrue();
        resetSubject.next();

        expect(resetRepositoryStub).toHaveBeenCalledWith(expectedResetId, gradedParticipationId);
    });
});
