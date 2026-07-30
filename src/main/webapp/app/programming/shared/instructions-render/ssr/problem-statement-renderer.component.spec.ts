import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { Component, input, output } from '@angular/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { ProblemStatementRendererComponent } from 'app/programming/shared/instructions-render/ssr/problem-statement-renderer.component';
import { SsrLiveUpdates } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr.component';
import { FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';

// The stubs must declare every input and output the wrapper template binds, otherwise template compilation fails.
@Component({ selector: 'jhi-programming-exercise-instructions', template: '<span class="legacy"></span>' })
class LegacyStubComponent {
    readonly exercise = input<ProgrammingExercise>();
    readonly participation = input<Participation>();
    readonly personalParticipation = input(false);
    readonly onNoInstructionsAvailable = output<void>();
}

@Component({ selector: 'jhi-programming-exercise-instruction-ssr', template: '<span class="ssr"></span>' })
class SsrStubComponent {
    readonly exercise = input<ProgrammingExercise>();
    readonly participation = input<Participation>();
    readonly result = input<Result>();
    readonly liveUpdates = input<SsrLiveUpdates>('none');
    readonly onNoInstructionsAvailable = output<void>();
}

describe('ProblemStatementRendererComponent', () => {
    let fixture: ComponentFixture<ProblemStatementRendererComponent>;
    let toggle: BehaviorSubject<boolean>;

    beforeEach(async () => {
        // getFeatureToggleActive returns Observable<boolean>, not an array of active features.
        toggle = new BehaviorSubject<boolean>(false);
        await TestBed.configureTestingModule({
            imports: [ProblemStatementRendererComponent],
            providers: [{ provide: FeatureToggleService, useValue: { getFeatureToggleActive: () => toggle.asObservable() } }],
        })
            .overrideComponent(ProblemStatementRendererComponent, { set: { imports: [LegacyStubComponent, SsrStubComponent] } })
            .compileComponents();
        fixture = TestBed.createComponent(ProblemStatementRendererComponent);
        fixture.componentRef.setInput('exercise', { id: 1, problemStatement: '# Hi' } as ProgrammingExercise);
    });

    it('renders the legacy component while the toggle is off', () => {
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.legacy')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('.ssr')).toBeFalsy();
    });

    it('renders the SSR component when the toggle is active', () => {
        toggle.next(true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.ssr')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('.legacy')).toBeFalsy();
    });
});
