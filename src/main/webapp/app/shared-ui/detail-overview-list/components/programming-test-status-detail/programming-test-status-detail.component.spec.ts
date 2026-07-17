import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { By } from '@angular/platform-browser';
import { RouterModule, provideRouter } from '@angular/router';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ProgrammingTestStatusDetailComponent } from 'app/shared-ui/detail-overview-list/components/programming-test-status-detail/programming-test-status-detail.component';
import { ProgrammingTestStatusDetail } from 'app/shared-ui/detail-overview-list/detail.model';
import { DetailType } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import { ProgrammingExerciseParticipationType } from 'app/programming/shared/entities/programming-exercise-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/instructor/programming-exercise-instructor-trigger-build-button.component';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/programming/manage/status/programming-exercise-instructor-status.component';

describe('ProgrammingTestStatusDetailComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<ProgrammingTestStatusDetailComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ProgrammingTestStatusDetailComponent],
            providers: [provideRouter([])],
        })
            .overrideComponent(ProgrammingTestStatusDetailComponent, {
                set: {
                    imports: [
                        RouterModule,
                        MockDirective(TranslateDirective),
                        MockComponent(UpdatingResultComponent),
                        MockComponent(ProgrammingExerciseInstructorTriggerBuildButtonComponent),
                        MockComponent(ProgrammingExerciseInstructorStatusComponent),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ProgrammingTestStatusDetailComponent);
    });

    function detailWith(loading = signal(false)): ProgrammingTestStatusDetail {
        return {
            type: DetailType.ProgrammingTestStatus,
            data: {
                exercise: { isAtLeastEditor: false } as ProgrammingExercise,
                participation: { id: 1 } as TemplateProgrammingExerciseParticipation,
                loading,
                onParticipationChange: () => {},
                type: ProgrammingExerciseParticipationType.TEMPLATE,
                submissionRouterLink: [],
            },
        };
    }

    // Regression test for the zoneless change-detection bug class: the participation results load asynchronously
    // after the detail section has rendered. Flipping the signal-backed loading flag must schedule change detection
    // on its own so the result appears without an unrelated event (e.g. a window resize).
    it('should reveal the result reactively once loading finishes', async () => {
        const loading = signal(true);
        fixture.componentRef.setInput('detail', detailWith(loading));

        await fixture.whenStable();
        // While loading, the updating-result is not rendered.
        expect(fixture.debugElement.query(By.css('jhi-updating-result'))).toBeNull();

        // Finish loading WITHOUT re-assigning the input; rely purely on the zoneless scheduler.
        loading.set(false);
        await fixture.whenStable();

        expect(fixture.debugElement.query(By.css('jhi-updating-result'))).not.toBeNull();
    });

    it('should not render anything when there is no participation', async () => {
        fixture.componentRef.setInput('detail', {
            type: DetailType.ProgrammingTestStatus,
            data: {
                exercise: { isAtLeastEditor: false } as ProgrammingExercise,
                participation: undefined,
                loading: signal(false),
                onParticipationChange: () => {},
                type: ProgrammingExerciseParticipationType.TEMPLATE,
            },
        } as ProgrammingTestStatusDetail);

        await fixture.whenStable();
        expect(fixture.debugElement.query(By.css('jhi-updating-result'))).toBeNull();
    });
});
