import { Component } from '@angular/core';
import { CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { CompetencyManagementTableComponent } from 'app/atlas/manage/competency-management/competency-management-table.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { RouterModule } from '@angular/router';

@Component({
    selector: 'jhi-competency-management',
    templateUrl: './competency-management.component.html',
    imports: [CompetencyManagementTableComponent, TranslateDirective, FontAwesomeModule, RouterModule],
})
export class CompetencyManagementComponent {
    readonly CourseCompetencyType = CourseCompetencyType;
}
