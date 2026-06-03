import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgModel } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { Exercise, ExerciseMode } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TeamAssignmentConfig } from 'app/exercise/shared/entities/team/team-assignment-config.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { TeamConfigFormGroupComponent } from 'app/exercise/team-config-form-group/team-config-form-group.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('Team Config Form Group Component', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TeamConfigFormGroupComponent>;
    let component: TeamConfigFormGroupComponent;
    let exercise: Exercise;
    let teamAssignmentConfig: TeamAssignmentConfig;
    let minTeamSizeSubject: Subject<boolean>;
    let maxTeamSizeSubject: Subject<boolean>;

    const setInputExercise = () => {
        fixture.componentRef.setInput('exercise', exercise);
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideTemplate(TeamConfigFormGroupComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(TeamConfigFormGroupComponent);
        component = fixture.componentInstance;

        exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 1;
        teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.id = 12;
        teamAssignmentConfig.exercise = exercise;
        teamAssignmentConfig.maxTeamSize = 5;
        teamAssignmentConfig.minTeamSize = 2;
        exercise.teamAssignmentConfig = teamAssignmentConfig;
        setInputExercise();
    });

    it('should set config correctly onNgInit with given input', () => {
        component.ngOnInit();
        expect(component.config).toEqual(teamAssignmentConfig);
    });

    it('should set config correctly onNgInit without given input', () => {
        exercise.teamAssignmentConfig = undefined;
        component.ngOnInit();
        expect(component.config).toEqual(new TeamAssignmentConfig());
    });

    it('should emit valid changes correctly', () => {
        const calculateValidSpy = vi.spyOn(component, 'calculateFormValid');
        const formValidChangesSpy = vi.spyOn(component, 'calculateFormValid');
        minTeamSizeSubject = new Subject<boolean>();
        maxTeamSizeSubject = new Subject<boolean>();
        const minTeamSizeField = { valueChanges: minTeamSizeSubject, valid: true } as any as NgModel;
        const maxTeamSizeField = { valueChanges: maxTeamSizeSubject, valid: true } as any as NgModel;
        vi.spyOn(component, 'minTeamSizeField').mockReturnValue(minTeamSizeField);
        vi.spyOn(component, 'maxTeamsizeField').mockReturnValue(maxTeamSizeField);
        exercise.mode = ExerciseMode.TEAM;
        component.ngAfterViewChecked();
        expect(component.inputFieldSubscriptions).not.toHaveLength(0);
        expect(component.inputFieldSubscriptions).toHaveLength(2);
        minTeamSizeSubject.next(false);
        expect(calculateValidSpy).toHaveBeenCalledOnce();
        expect(formValidChangesSpy).toHaveBeenCalledOnce();
        component.ngOnDestroy();
        for (const subscription of component.inputFieldSubscriptions) {
            expect(subscription?.closed).toBe(true);
        }
    });

    it('should set config to undefined when exercise mode changed to INDIVIDUAL', () => {
        component.onExerciseModeChange(ExerciseMode.INDIVIDUAL);
        expect(exercise.teamAssignmentConfig).toBeUndefined();
    });

    it('should apply config when exercise mode changed to TEAM', () => {
        component.config = teamAssignmentConfig;
        component.onExerciseModeChange(ExerciseMode.TEAM);
        // check for object equality and not reference equality
        expect(exercise.teamAssignmentConfig).toEqual(teamAssignmentConfig);
        expect(exercise.teamAssignmentConfig).not.toBe(teamAssignmentConfig);
    });

    it('should not change maxTeamSize if the new value for minTeamSize is lower', () => {
        component.config = teamAssignmentConfig;
        component.config.minTeamSize = 2;
        component.updateMinTeamSize(3);
        expect(component.config.maxTeamSize).toBe(5);
        expect(exercise.teamAssignmentConfig?.maxTeamSize).toBe(5);
    });

    it('should change maxTeamSize if the new value for minTeamSize is greater', () => {
        component.config = teamAssignmentConfig;
        component.config.minTeamSize = 6;
        component.updateMinTeamSize(6);
        expect(component.config.maxTeamSize).toBe(6);
        expect(exercise.teamAssignmentConfig?.maxTeamSize).toBe(6);
    });

    it('should not change minTeamSize if the new value for maxTeamSize is greater', () => {
        component.config = teamAssignmentConfig;
        component.config.maxTeamSize = 7;
        component.updateMaxTeamSize(7);
        expect(component.config.minTeamSize).toBe(2);
        expect(exercise.teamAssignmentConfig?.minTeamSize).toBe(2);
        expect(component.config.maxTeamSize).toBe(7);
        expect(exercise.teamAssignmentConfig?.maxTeamSize).toBe(7);
    });

    it('should change maxTeamSize if the new value for maxTeamSize is lower', () => {
        component.config = teamAssignmentConfig;
        component.config.maxTeamSize = 1;
        component.updateMaxTeamSize(1);
        expect(component.config.minTeamSize).toBe(1);
        expect(exercise.teamAssignmentConfig?.minTeamSize).toBe(1);
        expect(component.config.maxTeamSize).toBe(1);
        expect(exercise.teamAssignmentConfig?.maxTeamSize).toBe(1);
    });

    it('exercise mode should be editable for existing non-exam exercise', () => {
        fixture.componentRef.setInput('isImport', false);
        expect(component.changeExerciseModeDisabled).toBe(true);
    });

    it('exercise mode should be editable for existing exam exercise', () => {
        fixture.componentRef.setInput('isImport', false);
        exercise.exerciseGroup = new ExerciseGroup();
        expect(component.changeExerciseModeDisabled).toBe(true);
    });

    it('exercise mode should be non-editable for non-exam and imported exercise', () => {
        fixture.componentRef.setInput('isImport', true);
        expect(component.changeExerciseModeDisabled).toBe(false);
    });
});
