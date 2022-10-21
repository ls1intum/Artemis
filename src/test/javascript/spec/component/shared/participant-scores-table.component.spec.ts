import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { MockDirective, MockPipe } from 'ng-mocks';
import { TranslateModule } from '@ngx-translate/core';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisTestModule } from '../../test.module';
import { ParticipantScoreDTO } from 'app/shared/participant-scores/participant-scores.service';
import { By } from '@angular/platform-browser';
import { ParticipantScoresTableComponent } from 'app/shared/participant-scores/participant-scores-table/participant-scores-table.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('ParticipantScoresTable', () => {
    let fixture: ComponentFixture<ParticipantScoresTableComponent>;
    let component: ParticipantScoresTableComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule, TranslateModule.forRoot()],
            declarations: [ParticipantScoresTableComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective), DataTableComponent, MockDirective(NgbTypeahead)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                {
                    provide: SessionStorageService,
                    useClass: MockSyncStorage,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ParticipantScoresTableComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should render the data in a row', () => {
        const participantScoreDTO = new ParticipantScoreDTO();
        participantScoreDTO.id = 1;
        participantScoreDTO.userId = 42;
        participantScoreDTO.userName = 'testUser';
        participantScoreDTO.exerciseId = 99;
        participantScoreDTO.exerciseTitle = 'testExercise';
        participantScoreDTO.lastResultId = 12;
        participantScoreDTO.lastResultScore = 50;
        participantScoreDTO.lastRatedResultId = 20;
        participantScoreDTO.lastRatedResultScore = 100;
        participantScoreDTO.lastPoints = 13.3;
        participantScoreDTO.lastRatedPoints = 44.4;
        component.isLoading = false;
        component.participantScores = [participantScoreDTO];

        fixture.detectChanges();

        const cellElements = fixture.debugElement.queryAll(By.css('.datatable-body-cell-label > span'));
        expect(cellElements).toHaveLength(13);
        expect(cellElements[0].nativeElement.innerHTML).toContain(participantScoreDTO.id.toString());
        expect(cellElements[1].nativeElement.innerHTML).toContain(participantScoreDTO.userId.toString());
        expect(cellElements[2].nativeElement.innerHTML).toContain(participantScoreDTO.userName);
        expect(cellElements[3].nativeElement.innerHTML).toContain('');
        expect(cellElements[4].nativeElement.innerHTML).toContain('');
        expect(cellElements[5].nativeElement.innerHTML).toContain(participantScoreDTO.exerciseId.toString());
        expect(cellElements[6].nativeElement.innerHTML).toContain(participantScoreDTO.exerciseTitle);
        expect(cellElements[7].nativeElement.innerHTML).toContain(participantScoreDTO.lastResultId.toString());
        expect(cellElements[8].nativeElement.innerHTML).toContain(participantScoreDTO.lastResultScore.toString());
        expect(cellElements[9].nativeElement.innerHTML).toContain(participantScoreDTO.lastPoints.toString());
        expect(cellElements[10].nativeElement.innerHTML).toContain(participantScoreDTO.lastRatedResultId.toString());
        expect(cellElements[11].nativeElement.innerHTML).toContain(participantScoreDTO.lastRatedResultScore.toString());
        expect(cellElements[12].nativeElement.innerHTML).toContain(participantScoreDTO.lastRatedPoints.toString());
    });

    it('should extract participant name correctly', () => {
        let participantScoreDTO = new ParticipantScoreDTO();
        participantScoreDTO.userName = 'testUser';

        expect(component.extractParticipantName(participantScoreDTO)).toEqual(participantScoreDTO.userName);

        participantScoreDTO = new ParticipantScoreDTO();
        participantScoreDTO.teamName = 'testTeam';
        expect(component.extractParticipantName(participantScoreDTO)).toEqual(participantScoreDTO.teamName);
    });
});
