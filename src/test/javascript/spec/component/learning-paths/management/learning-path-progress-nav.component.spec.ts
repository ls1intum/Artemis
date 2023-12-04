import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { By } from '@angular/platform-browser';
import { LearningPathProgressNavComponent } from 'app/course/learning-paths/progress-modal/learning-path-progress-nav.component';
import { LearningPathInformationDTO } from 'app/entities/competency/learning-path.model';
import { UserNameAndLoginDTO } from 'app/core/user/user.model';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltipMocksModule } from '../../../helpers/mocks/directive/ngbTooltipMocks.module';

describe('LearningPathProgressNavComponent', () => {
    let fixture: ComponentFixture<LearningPathProgressNavComponent>;
    let comp: LearningPathProgressNavComponent;
    let onRefreshStub: jest.SpyInstance;
    let onCenterViewStub: jest.SpyInstance;
    let onCloseStub: jest.SpyInstance;
    let learningPath: LearningPathInformationDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbTooltipMocksModule],
            declarations: [LearningPathProgressNavComponent, MockPipe(ArtemisTranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathProgressNavComponent);
                comp = fixture.componentInstance;
                onRefreshStub = jest.spyOn(comp.onRefresh, 'emit');
                onCenterViewStub = jest.spyOn(comp.onCenterView, 'emit');
                onCloseStub = jest.spyOn(comp.onClose, 'emit');
                learningPath = new LearningPathInformationDTO();
                learningPath.user = new UserNameAndLoginDTO();
                learningPath.user.name = 'some arbitrary name';
                learningPath.user.login = 'somearbitrarylogin';
                comp.learningPath = learningPath;
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(fixture).toBeTruthy();
        expect(comp).toBeTruthy();
    });

    it('should emit refresh on click', () => {
        const button = fixture.debugElement.query(By.css('#refresh-button'));
        expect(button).not.toBeNull();

        button.nativeElement.click();
        expect(onRefreshStub).toHaveBeenCalledOnce();
    });

    it('should emit center view on click', () => {
        const button = fixture.debugElement.query(By.css('#center-button'));
        expect(button).not.toBeNull();

        button.nativeElement.click();
        expect(onCenterViewStub).toHaveBeenCalledOnce();
    });

    it('should emit close on click', () => {
        const button = fixture.debugElement.query(By.css('#close-button'));
        expect(button).not.toBeNull();

        button.nativeElement.click();
        expect(onCloseStub).toHaveBeenCalledOnce();
    });
});
