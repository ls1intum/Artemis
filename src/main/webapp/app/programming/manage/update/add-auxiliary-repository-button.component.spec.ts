import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { MockComponent } from 'ng-mocks';
import { AddAuxiliaryRepositoryButtonComponent } from 'app/programming/manage/update/add-auxiliary-repository-button.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { provideHttpClient } from '@angular/common/http';

describe('AddAuxiliaryRepositoryButtonComponent', () => {
    let comp: AddAuxiliaryRepositoryButtonComponent;
    let fixture: ComponentFixture<AddAuxiliaryRepositoryButtonComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), MockComponent(ButtonComponent)],
            providers: [provideHttpClient()],
        }).compileComponents();
        fixture = TestBed.createComponent(AddAuxiliaryRepositoryButtonComponent);
        comp = fixture.componentInstance;

        comp.programmingExercise = new ProgrammingExercise(undefined, undefined);
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
