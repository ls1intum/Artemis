import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ParticipantScoresAverageTableComponent } from 'app/shared/participant-scores/participant-scores-average-table/participant-scores-average-table.component';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { TranslateModule } from '@ngx-translate/core';
import { MockDirective, MockPipe } from 'ng-mocks';
import { NgbTooltipModule, NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisTestModule } from '../../test.module';
import { ParticipantScoreAverageDTO } from 'app/shared/participant-scores/participant-scores.service';
import { By } from '@angular/platform-browser';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { BaseEntity } from 'app/shared/model/base-entity';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('ParticipantScoresAverageTable', () => {
    let fixture: ComponentFixture<ParticipantScoresAverageTableComponent>;
    let component: ParticipantScoresAverageTableComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule, NgbTooltipModule, TranslateModule.forRoot()],
            declarations: [
                ParticipantScoresAverageTableComponent,
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                DataTableComponent,
                MockDirective(NgbTypeahead),
            ],
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
                fixture = TestBed.createComponent(ParticipantScoresAverageTableComponent);
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
        const participantScoreAverageDTO = new ParticipantScoreAverageDTO();
        participantScoreAverageDTO.userName = 'testUser';
        participantScoreAverageDTO.averageRatedScore = 10;
        participantScoreAverageDTO.averageScore = 5;
        participantScoreAverageDTO.averagePoints = 8;
        participantScoreAverageDTO.averageRatedPoints = 12;
        participantScoreAverageDTO.averageGrade = '2.7';
        participantScoreAverageDTO.averageRatedGrade = '2.0';

        component.isLoading = false;
        component.participantAverageScores = [participantScoreAverageDTO];

        fixture.detectChanges();

        const cellElements = fixture.debugElement.queryAll(By.css('.datatable-body-cell-label > span'));
        expect(cellElements.length).toEqual(8);
        expect(cellElements[0].nativeElement.innerHTML).toContain(participantScoreAverageDTO.userName);
        expect(cellElements[1].nativeElement.innerHTML).toContain('');
        expect(cellElements[2].nativeElement.innerHTML).toContain(participantScoreAverageDTO.averageScore.toString());
        expect(cellElements[3].nativeElement.innerHTML).toContain(participantScoreAverageDTO.averagePoints.toString());
        expect(cellElements[4].nativeElement.innerHTML).toContain(participantScoreAverageDTO.averageRatedScore.toString());
        expect(cellElements[5].nativeElement.innerHTML).toContain(participantScoreAverageDTO.averageRatedPoints.toString());
        expect(cellElements[6].nativeElement.innerHTML).toContain(participantScoreAverageDTO.averageGrade.toString());
        expect(cellElements[7].nativeElement.innerHTML).toContain(participantScoreAverageDTO.averageRatedGrade.toString());
    });

    it('should extract participant name correctly', () => {
        let participantScoreAverageDTO = new ParticipantScoreAverageDTO();
        participantScoreAverageDTO.userName = 'testUser';
        participantScoreAverageDTO.averageRatedScore = 10;
        participantScoreAverageDTO.averageScore = 5;
        participantScoreAverageDTO.averagePoints = 12;
        participantScoreAverageDTO.averageRatedPoints = 20;
        let castedParticipantScoreAverageDTO = participantScoreAverageDTO as BaseEntity;

        expect(component.extractParticipantName(castedParticipantScoreAverageDTO)).toEqual(participantScoreAverageDTO.userName);

        participantScoreAverageDTO = new ParticipantScoreAverageDTO();
        participantScoreAverageDTO.averageRatedScore = 10;
        participantScoreAverageDTO.averageScore = 5;
        participantScoreAverageDTO.teamName = 'testTeam';
        participantScoreAverageDTO.averageRatedPoints = 20;
        participantScoreAverageDTO.averagePoints = 12;
        castedParticipantScoreAverageDTO = participantScoreAverageDTO as BaseEntity;

        expect(component.extractParticipantName(castedParticipantScoreAverageDTO)).toEqual(participantScoreAverageDTO.teamName);
    });
});
