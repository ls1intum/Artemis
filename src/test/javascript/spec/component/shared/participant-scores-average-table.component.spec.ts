import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ParticipantScoresAverageTableComponent } from 'app/shared/participant-scores/participant-scores-average-table/participant-scores-average-table.component';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { TranslateModule } from '@ngx-translate/core';
import { MockDirective, MockPipe } from 'ng-mocks';
import { JhiTranslateDirective } from 'ng-jhipster';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import * as chai from 'chai';
import { NgbTooltipModule, NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisTestModule } from '../../test.module';
import { ParticipantScoreAverageDTO } from 'app/shared/participant-scores/participant-scores.service';
import { By } from '@angular/platform-browser';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { BaseEntity } from 'app/shared/model/base-entity';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('ParticipantScoresAverageTable', () => {
    let fixture: ComponentFixture<ParticipantScoresAverageTableComponent>;
    let component: ParticipantScoresAverageTableComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule, NgbTooltipModule, TranslateModule.forRoot()],
            declarations: [
                ParticipantScoresAverageTableComponent,
                MockDirective(JhiTranslateDirective),
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

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
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
        expect(cellElements.length).to.equal(8);
        expect(cellElements[0].nativeElement.innerHTML).to.contain(participantScoreAverageDTO.userName);
        expect(cellElements[1].nativeElement.innerHTML).to.contain('');
        expect(cellElements[2].nativeElement.innerHTML).to.contain(participantScoreAverageDTO.averageScore);
        expect(cellElements[3].nativeElement.innerHTML).to.contain(participantScoreAverageDTO.averagePoints);
        expect(cellElements[4].nativeElement.innerHTML).to.contain(participantScoreAverageDTO.averageRatedScore);
        expect(cellElements[5].nativeElement.innerHTML).to.contain(participantScoreAverageDTO.averageRatedPoints);
        expect(cellElements[6].nativeElement.innerHTML).to.contain(participantScoreAverageDTO.averageGrade);
        expect(cellElements[7].nativeElement.innerHTML).to.contain(participantScoreAverageDTO.averageRatedGrade);
    });

    it('should extract participant name correctly', () => {
        let participantScoreAverageDTO = new ParticipantScoreAverageDTO();
        participantScoreAverageDTO.userName = 'testUser';
        participantScoreAverageDTO.averageRatedScore = 10;
        participantScoreAverageDTO.averageScore = 5;
        participantScoreAverageDTO.averagePoints = 12;
        participantScoreAverageDTO.averageRatedPoints = 20;
        let castedParticipantScoreAverageDTO = participantScoreAverageDTO as BaseEntity;

        expect(component.extractParticipantName(castedParticipantScoreAverageDTO)).to.equal(participantScoreAverageDTO.userName);

        participantScoreAverageDTO = new ParticipantScoreAverageDTO();
        participantScoreAverageDTO.averageRatedScore = 10;
        participantScoreAverageDTO.averageScore = 5;
        participantScoreAverageDTO.teamName = 'testTeam';
        participantScoreAverageDTO.averageRatedPoints = 20;
        participantScoreAverageDTO.averagePoints = 12;
        castedParticipantScoreAverageDTO = participantScoreAverageDTO as BaseEntity;

        expect(component.extractParticipantName(castedParticipantScoreAverageDTO)).to.equal(participantScoreAverageDTO.teamName);
    });
});
