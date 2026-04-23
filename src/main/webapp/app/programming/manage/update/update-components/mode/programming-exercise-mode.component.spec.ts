import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseModeComponent } from 'app/programming/manage/update/update-components/mode/programming-exercise-mode.component';
import { TeamConfigFormGroupComponent } from 'app/exercise/team-config-form-group/team-config-form-group.component';
import { programmingExerciseCreationConfigMock } from 'test/helpers/mocks/programming-exercise-creation-config-mock';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MODULE_FEATURE_THEIA } from 'app/app.constants';
import { ProgrammingExerciseDifficultyComponent } from 'app/programming/manage/update/update-components/difficulty/programming-exercise-difficulty.component';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { DifficultyPickerComponent } from 'app/exercise/difficulty-picker/difficulty-picker.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

describe('ProgrammingExerciseModeComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseModeComponent>;
    let comp: ProgrammingExerciseModeComponent;
    let profileService: ProfileService;
    let getProfileInfoSub: jest.SpyInstance;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [
                ProgrammingExerciseModeComponent,
                MockComponent(DifficultyPickerComponent),
                MockComponent(TeamConfigFormGroupComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ProgrammingExerciseDifficultyComponent),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: { queryParams: of({}) } },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseModeComponent);
        comp = fixture.componentInstance;
        comp.programmingExercise = new ProgrammingExercise(undefined, undefined);
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;

        fixture.componentRef.setInput('isEditFieldDisplayedRecord', {
            difficulty: true,
            participationMode: true,
            allowOfflineIde: true,
            allowOnlineIde: true,
        });

        profileService = TestBed.inject(ProfileService);
        getProfileInfoSub = jest.spyOn(profileService, 'getProfileInfo');
        getProfileInfoSub.mockReturnValue({ sshCloneURLTemplate: 'ssh://git@testserver.com:1234/' } as ProfileInfo);
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
        getProfileInfoSub.mockReturnValue({ activeModuleFeatures: [MODULE_FEATURE_THEIA] } as ProfileInfo);

        fixture.detectChanges();
        expect(comp.theiaEnabled).toBeTrue();
    });
});
