import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { NgbTooltipModule, NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { BaseEntity } from 'app/shared/model/base-entity';
import { ParticipantScoresAverageTableComponent } from 'app/shared/participant-scores/participant-scores-average-table/participant-scores-average-table.component';
import { ParticipantScoreAverageDTO } from 'app/shared/participant-scores/participant-scores.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTestModule } from '../../test.module';

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
        participantScoreAverageDTO.name = 'testUser';
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
        expect(cellElements).toHaveLength(7);
        expect(cellElements[0].nativeElement.innerHTML).toContain(participantScoreAverageDTO.name);
        expect(cellElements[1].nativeElement.innerHTML).toContain(participantScoreAverageDTO.averageScore.toString());
        expect(cellElements[2].nativeElement.innerHTML).toContain(participantScoreAverageDTO.averagePoints.toString());
        expect(cellElements[3].nativeElement.innerHTML).toContain(participantScoreAverageDTO.averageRatedScore.toString());
        expect(cellElements[4].nativeElement.innerHTML).toContain(participantScoreAverageDTO.averageRatedPoints.toString());
        expect(cellElements[5].nativeElement.innerHTML).toContain(participantScoreAverageDTO.averageGrade.toString());
        expect(cellElements[6].nativeElement.innerHTML).toContain(participantScoreAverageDTO.averageRatedGrade.toString());
    });

    it('should extract participant name correctly', () => {
        let participantScoreAverageDTO = new ParticipantScoreAverageDTO();
        participantScoreAverageDTO.name = 'testUser';
        participantScoreAverageDTO.averageRatedScore = 10;
        participantScoreAverageDTO.averageScore = 5;
        participantScoreAverageDTO.averagePoints = 12;
        participantScoreAverageDTO.averageRatedPoints = 20;
        let castedParticipantScoreAverageDTO = participantScoreAverageDTO as BaseEntity;

        expect(component.extractParticipantName(castedParticipantScoreAverageDTO)).toEqual(participantScoreAverageDTO.name);

        participantScoreAverageDTO = new ParticipantScoreAverageDTO();
        participantScoreAverageDTO.averageRatedScore = 10;
        participantScoreAverageDTO.averageScore = 5;
        participantScoreAverageDTO.name = 'testTeam';
        participantScoreAverageDTO.averageRatedPoints = 20;
        participantScoreAverageDTO.averagePoints = 12;
        castedParticipantScoreAverageDTO = participantScoreAverageDTO as BaseEntity;

        expect(component.extractParticipantName(castedParticipantScoreAverageDTO)).toEqual(participantScoreAverageDTO.name);
    });
});
