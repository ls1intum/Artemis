import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { DebugElement } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseModeComponent } from 'app/exercises/programming/manage/update/update-components/mode/programming-exercise-mode.component';
import { DifficultyPickerComponent } from 'app/exercises/shared/difficulty-picker/difficulty-picker.component';
import { TeamConfigFormGroupComponent } from 'app/exercises/shared/team-config-form-group/team-config-form-group.component';
import { programmingExerciseCreationConfigMock } from './programming-exercise-creation-config-mock';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_THEIA } from 'app/app.constants';
import { ProgrammingExerciseDifficultyComponent } from 'app/exercises/programming/manage/update/update-components/difficulty/programming-exercise-difficulty.component';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ProgrammingExerciseModeComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseModeComponent>;
    let comp: ProgrammingExerciseModeComponent;
    let debugElement: DebugElement;
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
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
                {
                    provide: ProfileService,
                    useClass: MockProfileService,
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
            schemas: [],
        }).compileComponents();
        // .then(() => {
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

        debugElement = fixture.debugElement;
        profileService = debugElement.injector.get(ProfileService);
        getProfileInfoSub = jest.spyOn(profileService, 'getProfileInfo');
        getProfileInfoSub.mockReturnValue(
            of({
                inProduction: false,
                sshCloneURLTemplate: 'ssh://git@testserver.com:1234/',
            } as ProfileInfo),
        );
        // });
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
