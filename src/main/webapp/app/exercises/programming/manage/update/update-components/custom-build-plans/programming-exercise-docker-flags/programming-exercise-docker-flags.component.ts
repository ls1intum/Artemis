import { Component, input, OnInit} from '@angular/core';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisTableModule } from 'app/shared/table/table.module';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';

const ALLOWED_DOCKER_FLAG_OPTIONS = [
    'network',
    'dns',
    'dns-search',
    'dns-option',
    'env',
    'hostname',
    'ip',
    'ipv6',
    'user',
];

@Component({
    selector: 'jhi-programming-exercise-docker-flags',
    standalone: true,
    imports: [ArtemisSharedComponentModule, ArtemisSharedModule, ArtemisTableModule, NgbDropdownModule, NgxDatatableModule],
    templateUrl: './programming-exercise-docker-flags.component.html',
})
export class ProgrammingExerciseDockerFlagsComponent implements OnInit {
    programmingExercise = input.required<ProgrammingExercise>();
    displayEditDockerFlags: boolean = false;
    dockerFlags: [string, string][] = [];
    allowedDockerFlagOptions: string[] = ALLOWED_DOCKER_FLAG_OPTIONS;

    readonly faPlus = faPlus;
    readonly faTrash = faTrash;

    ngOnInit() {
        let existingFlags = JSON.parse(this.programmingExercise().buildConfig?.dockerFlags || '[]') as [string, string][];
        if (!existingFlags || existingFlags.length === 0) {
            return;
        }
        existingFlags = existingFlags.filter(([key, _]) => ALLOWED_DOCKER_FLAG_OPTIONS.includes(key));
        this.dockerFlags = existingFlags;
    }

    onDisplayEditDockerFlags() {
        this.displayEditDockerFlags = !this.displayEditDockerFlags;
    }

    updateFlagValue(rowIndex: number) {
        return (newValue: any) => {
            this.dockerFlags[rowIndex][1] = newValue;
            if (this.dockerFlags[rowIndex][0].trim() !== '' && this.dockerFlags[rowIndex][1].trim() !== '') {
                this.programmingExercise().buildConfig!.dockerFlags = JSON.stringify(this.dockerFlags);
            }
        }
    }

    addDockerFlag() {
        this.dockerFlags.push(['', '']);
        this.dockerFlags = [...this.dockerFlags];
    }

    removeDockerFlag(rowIndex: number) {
        this.dockerFlags.splice(rowIndex, 1);
        this.dockerFlags = [...this.dockerFlags];
        this.programmingExercise().buildConfig!.dockerFlags = JSON.stringify(this.dockerFlags);
    }
}
