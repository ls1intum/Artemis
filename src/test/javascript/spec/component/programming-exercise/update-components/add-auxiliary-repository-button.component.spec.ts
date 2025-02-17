import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockComponent } from 'ng-mocks';
import { AddAuxiliaryRepositoryButtonComponent } from '../../../../../../main/webapp/app/exercises/programming/manage/update/add-auxiliary-repository-button.component';
import { ProgrammingExercise } from '../../../../../../main/webapp/app/entities/programming/programming-exercise.model';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from '../../../helpers/mocks/service/mock-feature-toggle.service';

describe('AddAuxiliaryRepositoryButtonComponent', () => {
    let comp: AddAuxiliaryRepositoryButtonComponent;
    let fixture: ComponentFixture<AddAuxiliaryRepositoryButtonComponent>;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), MockComponent(ButtonComponent)],
            providers: [{ provide: FeatureToggleService, useClass: MockFeatureToggleService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AddAuxiliaryRepositoryButtonComponent);
                comp = fixture.componentInstance;

                comp.programmingExercise = new ProgrammingExercise(undefined, undefined);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).toBeDefined();
    });

    it('should add auxiliary repository', () => {
        const initialLength = comp.programmingExercise.auxiliaryRepositories?.length || 0;
        const onRefreshSpy = jest.spyOn(comp.onRefresh, 'emit');

        comp.addAuxiliaryRepositoryRow();

        expect(comp.programmingExercise.auxiliaryRepositories?.length).toBe(initialLength + 1);
        expect(onRefreshSpy).toHaveBeenCalledOnce();
    });
});
