import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ParticipantScoresAverageTableComponent } from 'app/shared/participant-scores/participant-scores-average-table/participant-scores-average-table.component';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { TranslateModule, TranslatePipe } from '@ngx-translate/core';
import { MockDirective, MockPipe } from 'ng-mocks';
import { JhiTranslateDirective } from 'ng-jhipster';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import * as chai from 'chai';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisTestModule } from '../../test.module';
import { ParticipantScoreAverageDTO } from 'app/shared/participant-scores/participant-scores.service';
import { By } from '@angular/platform-browser';

chai.use(sinonChai);
const expect = chai.expect;

describe('ParticipantScoresAverageTable', () => {
    let fixture: ComponentFixture<ParticipantScoresAverageTableComponent>;
    let component: ParticipantScoresAverageTableComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisDataTableModule, NgxDatatableModule, NgbTooltipModule, TranslateModule.forRoot()],
            declarations: [ParticipantScoresAverageTableComponent, MockPipe(TranslatePipe), MockDirective(JhiTranslateDirective)],
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
        participantScoreAverageDTO.teamName = null;

        component.isLoading = false;
        component.participantAverageScores = [participantScoreAverageDTO];

        fixture.detectChanges();

        const cellElements = fixture.debugElement.queryAll(By.css('.datatable-body-cell-label > span'));
        expect(cellElements.length).to.equal(4);
        expect(cellElements[0].nativeElement.innerHTML).to.contain(participantScoreAverageDTO.userName);
        expect(cellElements[1].nativeElement.innerHTML).to.contain('');
        expect(cellElements[2].nativeElement.innerHTML).to.contain(participantScoreAverageDTO.averageScore + '');
        expect(cellElements[3].nativeElement.innerHTML).to.contain(participantScoreAverageDTO.averageRatedScore + '');
    });
});
