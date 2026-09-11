import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { EMPTY, Subject, of, throwError } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { HyperionExerciseGenerationService } from '../hyperion-exercise-generation.service';
import { HyperionRunLinkComponent } from './hyperion-run-link.component';

describe('HyperionRunLinkComponent', () => {
    let fixture: ComponentFixture<HyperionRunLinkComponent>;
    let service: { getStatus: ReturnType<typeof vi.fn>; subscribeToExerciseState: ReturnType<typeof vi.fn> };
    let profile: { isModuleFeatureActive: ReturnType<typeof vi.fn> };
    const exercise = (id = 42, isAtLeastEditor = true) =>
        Object.assign(new ProgrammingExercise(undefined, undefined), {
            id,
            isAtLeastEditor,
            programmingLanguage: ProgrammingLanguage.JAVA,
            projectType: ProjectType.GRADLE_GRADLE,
        });
    const link = () => fixture.nativeElement.querySelector('[data-testid="hyperion-exercise-open-generation"]');
    const render = async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    beforeEach(async () => {
        service = { getStatus: vi.fn(() => of({ jobId: 'job', running: true })), subscribeToExerciseState: vi.fn(() => EMPTY) };
        profile = { isModuleFeatureActive: vi.fn(() => true) };
        await TestBed.configureTestingModule({
            imports: [HyperionRunLinkComponent],
            providers: [
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: HyperionExerciseGenerationService, useValue: service },
                { provide: ProfileService, useValue: profile },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(HyperionRunLinkComponent);
        fixture.componentRef.setInput('exercise', exercise());
    });

    afterEach(() => fixture.destroy());

    it('offers re-entry to an active generation', async () => {
        await render();
        expect(link()?.textContent).toContain('viewRun');
        expect(link()?.getAttribute('href')).toBe('/generation');
        expect(service.getStatus).toHaveBeenCalledWith(42);
    });

    it('offers retained results and removes the link when no result is available', async () => {
        service.getStatus.mockReturnValue(of({ jobId: 'job', running: false }));
        await render();
        expect(link()?.textContent).toContain('viewResults');
        fixture.componentRef.setInput('exercise', exercise(43));
        service.getStatus.mockReturnValue(of(undefined));
        await render();
        expect(link()).toBeNull();
    });

    it('clears the previous exercise link before a new status request completes', async () => {
        await render();
        expect(link()).not.toBeNull();
        service.getStatus.mockReturnValue(new Subject());
        fixture.componentRef.setInput('exercise', exercise(43));
        await render();
        expect(link()).toBeNull();
    });

    it('does not load status for a student or when the feature is disabled', async () => {
        fixture.componentRef.setInput('exercise', exercise(42, false));
        await render();
        expect(service.getStatus).not.toHaveBeenCalled();
        profile.isModuleFeatureActive.mockReturnValue(false);
        fixture.componentRef.setInput('exercise', exercise(43));
        await render();
        expect(service.getStatus).not.toHaveBeenCalled();
        expect(link()).toBeNull();
    });

    it('hides the link on status errors instead of advertising unavailable results', async () => {
        service.getStatus.mockReturnValue(throwError(() => new Error('unavailable')));
        await render();
        expect(link()).toBeNull();
    });
});
