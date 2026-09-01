import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { AlertService } from 'app/foundation/service/alert.service';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { WholeExerciseGenerationWizardComponent } from './whole-exercise-generation-wizard.component';

describe('WholeExerciseGenerationWizardComponent', () => {
    let fixture: ComponentFixture<WholeExerciseGenerationWizardComponent>;
    let component: WholeExerciseGenerationWizardComponent;
    let setup: ReturnType<typeof vi.fn>;
    let generate: ReturnType<typeof vi.fn>;
    let alertError: ReturnType<typeof vi.fn>;

    beforeEach(async () => {
        setup = vi.fn();
        generate = vi.fn();
        alertError = vi.fn();
        await TestBed.configureTestingModule({
            imports: [WholeExerciseGenerationWizardComponent],
            providers: [
                { provide: ProgrammingExerciseService, useValue: { automaticSetup: setup } },
                {
                    provide: HyperionExerciseGenerationService,
                    useValue: { generate, getStatus: vi.fn(() => of(null)), subscribeToStream: vi.fn(), cancel: vi.fn(), revertExerciseGeneration: vi.fn() },
                },
                { provide: AlertService, useValue: { error: alertError } },
                { provide: Router, useValue: { navigate: vi.fn().mockResolvedValue(true) } },
                provideTranslateService(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(WholeExerciseGenerationWizardComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 42);
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
    });

    function enterValidConfiguration(projectType = ProjectType.PLAIN_MAVEN): void {
        component.updateTitle('Bounded Stack');
        component.brief.set('Students implement a generic bounded stack with explicit empty and capacity edge cases.');
        component.projectType.set(projectType);
        component.releaseDate.set(dayjs().add(1, 'day').format('YYYY-MM-DDTHH:mm'));
        component.dueDate.set(dayjs().add(8, 'day').format('YYYY-MM-DDTHH:mm'));
    }

    it('derives an editable valid short name and rejects an underspecified brief', () => {
        component.updateTitle('Bounded Stack!');

        expect(component.shortName()).toBe('BoundedStack');
        expect(component.canGenerate()).toBe(false);
    });

    it.each([ProjectType.PLAIN_MAVEN, ProjectType.PLAIN_GRADLE])('provisions and starts a Java %s exercise without routing through the create page', (projectType) => {
        enterValidConfiguration(projectType);
        const created = new ProgrammingExercise(undefined, undefined);
        created.id = 7;
        setup.mockReturnValue(of(new HttpResponse({ body: created })));
        generate.mockReturnValue(of({ jobId: 'job-1' }));

        component.generate();

        expect(setup).toHaveBeenCalledWith(
            expect.objectContaining({
                title: 'Bounded Stack',
                shortName: 'BoundedStack',
                problemStatement: component.brief(),
                projectType,
                packageName: 'de.artemis.boundedstack',
            }),
            true,
        );
        expect(generate).toHaveBeenCalledWith(7, { mode: 'GENERATE', prompt: component.brief() });
        expect(component.step()).toBe('generating');
    });

    it('does not submit twice while provisioning', () => {
        enterValidConfiguration();
        component.provisioning.set(true);

        component.generate();

        expect(setup).not.toHaveBeenCalled();
    });

    it('keeps the created shell accessible when generation admission fails', () => {
        enterValidConfiguration();
        const created = new ProgrammingExercise(undefined, undefined);
        created.id = 9;
        setup.mockReturnValue(of(new HttpResponse({ body: created })));
        generate.mockReturnValue(throwError(() => new Error('capacity unavailable')));

        component.generate();

        expect(component.createdExercise()?.id).toBe(9);
        expect(component.step()).toBe('generating');
        expect(alertError).toHaveBeenCalledWith('artemisApp.hyperion.wholeExerciseWizard.startFailed');
    });
});
