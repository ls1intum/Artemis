import { CompetencyRelationDTO, CourseCompetency } from 'app/entities/competency.model';
import { Directive, input, model, output } from '@angular/core';
import { faEdit, faFileImport, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { outputToObservable } from '@angular/core/rxjs-interop';

@Directive({
    standalone: true,
})
export abstract class CourseCompetenciesTableDirective<T extends CourseCompetency> {
    protected readonly faTrash = faTrash;
    protected readonly faEdit = faEdit;
    protected readonly faPlus = faPlus;
    protected readonly faFileImport = faFileImport;

    readonly courseId = input.required<number>();
    readonly courseCompetencies = model.required<T[]>();
    readonly relations = model.required<CompetencyRelationDTO[]>();

    readonly dialogErrorSource = output<string>();
    readonly dialogError = outputToObservable(this.dialogErrorSource);

    protected async deleteCourseCompetency(courseCompetencyId: number, deleteFunction: (courseId: number, courseCompetencyId: number) => Promise<void>): Promise<void> {
        try {
            await deleteFunction(this.courseId(), courseCompetencyId);
            this.dialogErrorSource.emit('');
            this.courseCompetencies.update((courseCompetencies) => courseCompetencies.filter((courseCompetency) => courseCompetency.id !== courseCompetencyId));
            this.relations.update((relations) => {
                return relations.filter((relation) => relation.headCompetencyId !== courseCompetencyId && relation.tailCompetencyId !== courseCompetencyId);
            });
        } catch (error) {
            this.dialogErrorSource.emit(error.message);
        }
    }
}
