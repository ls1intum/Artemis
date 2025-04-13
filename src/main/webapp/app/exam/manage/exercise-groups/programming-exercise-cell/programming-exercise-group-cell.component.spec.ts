import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProgrammingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/programming-exercise-cell/programming-exercise-group-cell.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { By } from '@angular/platform-browser';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { of } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { PROFILE_THEIA } from 'app/app.constants';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('Programming Exercise Group Cell Component', () => {
    let fixture: ComponentFixture<ProgrammingExerciseGroupCellComponent>;
    const exercise: ProgrammingExercise = {
        id: 1,
        type: ExerciseType.PROGRAMMING,
        shortName: 'test',
        projectKey: 'key',
        templateParticipation: {
            buildPlanId: '1',
            repositoryUri: 'https://test.com/myrepo',
        },
        solutionParticipation: {
            buildPlanId: '2',
            repositoryUri: 'https://test.com/myrepo',
        },
        allowOfflineIde: true,
        allowOnlineEditor: true,
        allowOnlineIde: false,
    } as any as ProgrammingExercise;

    let mockedProfileService: ProfileService;

    beforeEach(() => {
        mockedProfileService = {
            getProfileInfo: () =>
                // @ts-ignore
                of({
                    buildPlanURLTemplate: 'https://example.com/{buildPlanId}/{projectKey}',
                    activeProfiles: [PROFILE_THEIA],
                }),
        };

        TestBed.configureTestingModule({
            providers: [
                { provide: ProfileService, useValue: mockedProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(AlertService),
                provideHttpClient(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseGroupCellComponent);
                fixture.componentRef.setInput('exercise', exercise);
            });
    });

    it('sets buildPlanURLs correctly', () => {
        fixture.detectChanges();
        expect(exercise.templateParticipation!.buildPlanUrl).toBe('https://example.com/1/key');
        expect(exercise.solutionParticipation!.buildPlanUrl).toBe('https://example.com/2/key');
    });

    it('should display short name', () => {
        fixture.componentRef.setInput('displayShortName', true);
        fixture.detectChanges();
        const div = fixture.debugElement.query(By.css('div:first-child'));
        expect(div).not.toBeNull();
        expect(div.nativeElement.textContent).toContain(exercise.shortName);
    });

    it('should display respository url', () => {
        fixture.componentRef.setInput('displayRepositoryUri', true);
        fixture.detectChanges();
        const span = fixture.debugElement.query(By.css('a'));
        expect(span).toBeDefined();
        expect(span.nativeElement.textContent).toBe('Template');
        expect(span.nativeElement.href).toBe(exercise.templateParticipation!.repositoryUri);
    });

    it('should display editor mode flags', () => {
        fixture.componentRef.setInput('displayEditorModus', true);
        fixture.detectChanges();

        const div0 = fixture.debugElement.query(By.css('div > div > div:first-child'));
        expect(div0).not.toBeNull();
        expect(div0.nativeElement.textContent?.trim()).toBe('artemisApp.programmingExercise.offlineIdeartemisApp.exercise.yes');

        const div1 = fixture.debugElement.query(By.css('div > div > div:nth-child(2)'));
        expect(div1).not.toBeNull();
        expect(div1.nativeElement.textContent?.trim()).toBe('artemisApp.programmingExercise.onlineEditorartemisApp.exercise.yes');

        const div2 = fixture.debugElement.query(By.css('div > div > div:nth-child(3)'));
        expect(div2).not.toBeNull();
        expect(div2.nativeElement.textContent?.trim()).toBe('artemisApp.programmingExercise.onlineIdeartemisApp.exercise.no');
    });
});
