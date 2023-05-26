import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { ProgrammingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/programming-exercise-cell/programming-exercise-group-cell.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { By } from '@angular/platform-browser';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { of } from 'rxjs';

describe('Programming Exercise Group Cell Component', () => {
    let comp: ProgrammingExerciseGroupCellComponent;
    let fixture: ComponentFixture<ProgrammingExerciseGroupCellComponent>;
    const exercise: ProgrammingExercise = {
        id: 1,
        type: ExerciseType.PROGRAMMING,
        shortName: 'test',
        projectKey: 'key',
        templateParticipation: {
            buildPlanId: '1',
            repositoryUrl: 'https://test.com/myrepo',
        },
        solutionParticipation: {
            buildPlanId: '2',
            repositoryUrl: 'https://test.com/myrepo',
        },
        allowOfflineIde: true,
        allowOnlineEditor: true,
    } as any as ProgrammingExercise;

    let mockedProfileService: ProfileService;

    beforeEach(() => {
        mockedProfileService = {
            getProfileInfo: () =>
                // @ts-ignore
                of({
                    buildPlanURLTemplate: 'https://example.com/{buildPlanId}/{projectKey}',
                }),
        };

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseGroupCellComponent, TranslatePipeMock],
            providers: [{ provide: ProfileService, useValue: mockedProfileService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseGroupCellComponent);
                comp = fixture.componentInstance;
                comp.exercise = exercise;
            });
    });

    it('sets buildPlanURLs correctly', () => {
        fixture.detectChanges();
        expect(exercise.templateParticipation!.buildPlanUrl).toBe('https://example.com/1/key');
        expect(exercise.solutionParticipation!.buildPlanUrl).toBe('https://example.com/2/key');
    });

    it('should display short name', () => {
        comp.displayShortName = true;
        fixture.detectChanges();
        const div = fixture.debugElement.query(By.css('div:first-child'));
        expect(div).not.toBeNull();
        expect(div.nativeElement.textContent).toContain(exercise.shortName);
    });

    it('should display respository url', () => {
        comp.displayRepositoryUrl = true;
        fixture.detectChanges();
        const span = fixture.debugElement.query(By.css('a'));
        expect(span).toBeDefined();
        expect(span.nativeElement.textContent).toBe('Template');
        expect(span.nativeElement.href).toBe(exercise.templateParticipation!.repositoryUrl);
    });

    it('should display editor mode flags', () => {
        comp.displayEditorModus = true;
        fixture.detectChanges();

        const div0 = fixture.debugElement.query(By.css('div > div > div:first-child'));
        expect(div0).not.toBeNull();
        expect(div0.nativeElement.textContent?.trim()).toBe('artemisApp.programmingExercise.offlineIde : true');

        const div1 = fixture.debugElement.query(By.css('div > div > div:nth-child(2)'));
        expect(div1).not.toBeNull();
        expect(div1.nativeElement.textContent?.trim()).toBe('artemisApp.programmingExercise.onlineEditor : true');
    });
});
