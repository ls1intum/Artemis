import { Component, input } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faFileImport } from '@fortawesome/free-solid-svg-icons';
import { CourseCompetency } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-course-competencies-relation-graph',
    standalone: true,
    imports: [FontAwesomeModule],
    templateUrl: './course-competencies-relation-graph.component.html',
    styleUrl: './course-competencies-relation-graph.component.scss',
})
export class CourseCompetenciesRelationGraphComponent {
    protected readonly faFileImport = faFileImport;

    readonly courseCompetencies = input.required<CourseCompetency[]>();
}
