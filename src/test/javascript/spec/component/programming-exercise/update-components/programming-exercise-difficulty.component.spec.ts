import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { DebugElement } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseDifficultyComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-difficulty.component';
import { CheckboxControlValueAccessor, DefaultValueAccessor, NgModel, NumberValueAccessor, SelectControlValueAccessor } from '@angular/forms';
import { DifficultyPickerComponent } from 'app/exercises/shared/difficulty-picker/difficulty-picker.component';
import { TeamConfigFormGroupComponent } from 'app/exercises/shared/team-config-form-group/team-config-form-group.component';
import { programmingExerciseCreationConfigMock } from './programming-exercise-creation-config-mock';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_THEIA } from 'app/app.constants';
import { ArtemisTestModule } from '../../../test.module';

describe('ProgrammingExerciseDifficultyComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseDifficultyComponent>;
    let comp: ProgrammingExerciseDifficultyComponent;
    let debugElement: DebugElement;
    let profileService: ProfileService;
    let getProfileInfoSub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CheckboxControlValueAccessor,
                DefaultValueAccessor,
                SelectControlValueAccessor,
                NumberValueAccessor,
                NgModel,
                ProgrammingExerciseDifficultyComponent,
                MockComponent(DifficultyPickerComponent),
                MockComponent(TeamConfigFormGroupComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseDifficultyComponent);
                comp = fixture.componentInstance;
                comp.programmingExercise = new ProgrammingExercise(undefined, undefined);
                comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;

                debugElement = fixture.debugElement;
                profileService = debugElement.injector.get(ProfileService);
                getProfileInfoSub = jest.spyOn(profileService, 'getProfileInfo');
                getProfileInfoSub.mockReturnValue(of({ inProduction: false, sshCloneURLTemplate: 'ssh://git@testserver.com:1234/' } as ProfileInfo));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    it('should initialize theiaEnabled', () => {
        getProfileInfoSub = jest.spyOn(profileService, 'getProfileInfo');
        getProfileInfoSub.mockReturnValue(of({ activeProfiles: [PROFILE_THEIA] } as ProfileInfo));

        fixture.detectChanges();
        expect(comp.theiaEnabled).toBeTrue();
    });
});
