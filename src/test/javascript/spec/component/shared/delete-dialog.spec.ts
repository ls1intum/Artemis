import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { TranslateModule } from '@ngx-translate/core';
import { DebugElement } from '@angular/core';
import { SinonStub, stub } from 'sinon';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { ResultComponent, UpdatingResultComponent } from 'app/entities/result';
import { ArtemisSharedModule, CacheableImageService, CachingStrategy, SecuredImageComponent } from 'app/shared';
import { MockCacheableImageService } from '../../mocks/mock-cacheable-image.service';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { By } from '@angular/platform-browser';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { JhiAlertService } from 'ng-jhipster';

chai.use(sinonChai);
const expect = chai.expect;

describe('SecuredImageComponent', () => {
    let comp: DeleteDialogComponent;
    let fixture: ComponentFixture<DeleteDialogComponent>;
    let debugElement: DebugElement;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule],
            declarations: [DeleteDialogComponent],
            providers: [JhiLanguageHelper, JhiAlertService],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DeleteDialogComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });
    it('Dialog is correctly initialized', fakeAsync(() => {
        const modalTitle = fixture.debugElement.query(By.css('.modal-title'));
        expect(modalTitle).to.exist;
        comp.entityTitle = 'title';
        comp.deleteQuestion = 'artemisApp.exercise.delete.question';
        comp.deleteConfirmationText = 'artemisApp.exercise.delete.typeNameToConfirm';
        fixture.detectChanges();
    }));
});
