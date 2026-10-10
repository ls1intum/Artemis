import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { ParticipationMode } from 'app/exercise/exercise-headers/participation-mode-toggle/participation-mode-toggle.component';
import { TeamPracticeHintComponent } from 'app/course/overview/exercise-details/team-practice-hint/team-practice-hint.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('TeamPracticeHintComponent', () => {
    const TEAM_HINT = 'artemisApp.exerciseActions.practiceMode.teamHint';
    const TEAM_NOTE = 'artemisApp.exerciseActions.practiceMode.teamNote';

    let fixture: ComponentFixture<TeamPracticeHintComponent>;
    let component: TeamPracticeHintComponent;

    const pastDueDate = () => dayjs().subtract(1, 'hour');
    const futureDueDate = () => dayjs().add(1, 'hour');
    const gradedParticipation = { id: 1, testRun: false, initializationState: InitializationState.FINISHED } as StudentParticipation;
    const practiceParticipation = { id: 2, testRun: true, initializationState: InitializationState.INITIALIZED } as StudentParticipation;

    const render = (exercise: Partial<Exercise>, mode: ParticipationMode = 'graded', practice?: StudentParticipation, graded?: StudentParticipation) => {
        fixture.componentRef.setInput('exercise', { id: 7, ...exercise } as Exercise);
        fixture.componentRef.setInput('participationMode', mode);
        fixture.componentRef.setInput('practiceParticipation', practice);
        fixture.componentRef.setInput('gradedParticipation', graded);
        fixture.detectChanges();
    };

    const hint = (): HTMLElement | null => fixture.nativeElement.querySelector('[data-testid="team-practice-hint"]');
    const hintText = () => hint()?.textContent?.trim();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TeamPracticeHintComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(TeamPracticeHintComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        fixture.destroy();
    });

    describe('while the student can start practice', () => {
        it.each([ExerciseType.PROGRAMMING, ExerciseType.TEXT, ExerciseType.MODELING])('should explain for a team %s exercise that practice is individual', (type) => {
            render({ type, teamMode: true, dueDate: pastDueDate() }, 'graded', undefined, gradedParticipation);

            expect(component.messageKey()).toBe(TEAM_HINT);
            expect(hint()).not.toBeNull();
            expect(hintText()).toBe(TEAM_HINT);
            // a status message for assistive technology, not an alert
            expect(hint()!.getAttribute('role')).toBe('status');
            expect(hint()!.getAttribute('data-severity')).toBe('info');
            // the icon is not the only carrier of the message
            expect(hint()!.querySelector('fa-icon')).not.toBeNull();
        });

        it.each([ExerciseType.PROGRAMMING, ExerciseType.TEXT, ExerciseType.MODELING])('should show nothing for an individual %s exercise', (type) => {
            render({ type, teamMode: false, dueDate: pastDueDate() }, 'graded', undefined, gradedParticipation);

            expect(component.messageKey()).toBeUndefined();
            expect(hint()).toBeNull();
            expect(fixture.nativeElement.textContent.trim()).toBe('');
        });

        it('should show nothing for an exercise without team mode flag', () => {
            render({ type: ExerciseType.PROGRAMMING, dueDate: pastDueDate() }, 'graded');

            expect(component.messageKey()).toBeUndefined();
            expect(hint()).toBeNull();
        });

        it('should wait for the due date, because practice cannot be started before it', () => {
            render({ type: ExerciseType.PROGRAMMING, teamMode: true, dueDate: futureDueDate() }, 'graded');

            expect(component.messageKey()).toBeUndefined();
            expect(hint()).toBeNull();
        });

        it('should wait for an extended deadline of the team, which postpones practice as well', () => {
            const extended = { ...gradedParticipation, individualDueDate: futureDueDate() } as StudentParticipation;
            render({ type: ExerciseType.PROGRAMMING, teamMode: true, dueDate: pastDueDate() }, 'graded', undefined, extended);

            expect(hint()).toBeNull();
        });

        it('should disappear once the programming practice repository is set up, because there is no practice to start anymore', () => {
            render({ type: ExerciseType.PROGRAMMING, teamMode: true, dueDate: pastDueDate() }, 'graded', undefined, gradedParticipation);
            expect(hintText()).toBe(TEAM_HINT);

            fixture.componentRef.setInput('practiceParticipation', practiceParticipation);
            fixture.detectChanges();

            expect(hint()).toBeNull();
        });
    });

    describe('in the practice view', () => {
        it.each([ExerciseType.PROGRAMMING, ExerciseType.TEXT, ExerciseType.MODELING])('should remind the student of a team %s exercise that the practice is their own', (type) => {
            render({ type, teamMode: true, dueDate: pastDueDate() }, 'practice', practiceParticipation, gradedParticipation);

            expect(component.messageKey()).toBe(TEAM_NOTE);
            expect(hintText()).toBe(TEAM_NOTE);
            expect(hint()!.getAttribute('role')).toBe('status');
        });

        it('should keep the note while the practice participation is not known yet', () => {
            render({ type: ExerciseType.TEXT, teamMode: true, dueDate: pastDueDate() }, 'practice');

            expect(hintText()).toBe(TEAM_NOTE);
        });

        it('should show nothing for an individual exercise', () => {
            render({ type: ExerciseType.PROGRAMMING, teamMode: false, dueDate: pastDueDate() }, 'practice', practiceParticipation, gradedParticipation);

            expect(component.messageKey()).toBeUndefined();
            expect(hint()).toBeNull();
        });
    });

    it('should switch between the hint and the note when the student toggles the participation mode', () => {
        render({ type: ExerciseType.TEXT, teamMode: true, dueDate: pastDueDate() }, 'graded', undefined, gradedParticipation);
        expect(hintText()).toBe(TEAM_HINT);

        fixture.componentRef.setInput('participationMode', 'practice');
        fixture.componentRef.setInput('practiceParticipation', practiceParticipation);
        fixture.detectChanges();
        expect(hintText()).toBe(TEAM_NOTE);

        fixture.componentRef.setInput('participationMode', 'graded');
        fixture.detectChanges();
        // a text exercise with a practice participation cannot start another practice
        expect(hint()).toBeNull();
    });

    it('should never show a hint for a quiz, which has no team mode', () => {
        render({ type: ExerciseType.QUIZ, teamMode: false, dueDate: pastDueDate() }, 'graded');

        expect(component.messageKey()).toBeUndefined();
        expect(hint()).toBeNull();
    });
});
