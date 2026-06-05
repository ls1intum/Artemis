import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ProgrammingExerciseModeComponent } from 'app/programming/manage/update/update-components/mode/programming-exercise-mode.component';
import { programmingExerciseCreationConfigMock } from 'test/helpers/mocks/programming-exercise-creation-config-mock';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MODULE_FEATURE_THEIA } from 'app/app.constants';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { provideHttpClient } from '@angular/common/http';

describe('ProgrammingExerciseModeComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ProgrammingExerciseModeComponent>;
    let comp: ProgrammingExerciseModeComponent;
    let profileService: ProfileService;
    let getProfileInfoSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: { queryParams: of({}) } },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
            ],
        });

        fixture = TestBed.createComponent(ProgrammingExerciseModeComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('programmingExercise', new ProgrammingExercise(undefined, undefined));
        fixture.componentRef.setInput('programmingExerciseCreationConfig', programmingExerciseCreationConfigMock);
        fixture.componentRef.setInput('isEditFieldDisplayedRecord', {
            difficulty: true,
            participationMode: true,
            allowOfflineIde: true,
            allowOnlineIde: true,
        });

        profileService = TestBed.inject(ProfileService);
        getProfileInfoSpy = vi.spyOn(profileService, 'getProfileInfo');
        getProfileInfoSpy.mockReturnValue({ sshCloneURLTemplate: 'ssh://git@testserver.com:1234/' } as ProfileInfo);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    it('should initialize theiaEnabled', () => {
        getProfileInfoSpy.mockReturnValue({ activeModuleFeatures: [MODULE_FEATURE_THEIA] } as ProfileInfo);

        fixture.detectChanges();
        expect(comp.theiaEnabled).toBe(true);
    });
});
