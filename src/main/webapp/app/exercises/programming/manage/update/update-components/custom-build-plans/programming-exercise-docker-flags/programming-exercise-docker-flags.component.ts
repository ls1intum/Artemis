import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { CommonModule } from '@angular/common';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';

const ALLOWED_DOCKER_FLAGS: { [key: string]: string[] } = {
    ram: ['512MB', '1GB', '2GB', '4GB'],
    cpu: ['0.5', '1', '2', '4'],
    'memory-swap': ['0', '1GB', '2GB', '4GB'],
};

@Component({
    selector: 'jhi-programming-exercise-docker-flags',
    standalone: true,
    templateUrl: './programming-exercise-docker-flags.component.html',
    imports: [HelpIconComponent, NgxDatatableModule, CommonModule, FontAwesomeModule, FormsModule, TranslateModule],
})
export class ProgrammingExerciseDockerFlagsComponent implements OnInit {
    @Input() programmingExercise: any;
    @Output() isValidEmitter = new EventEmitter<boolean>();

    displayEditDockerFlags: boolean = false;
    dockerFlags: [string, string][] = [];
    allowedDockerFlagOptions: string[] = Object.keys(ALLOWED_DOCKER_FLAGS);
    isValid = true;

    readonly faPlus = faPlus;
    readonly faTrash = faTrash;

    ngOnInit(): void {
        // Load existing flags
        let existingFlags = JSON.parse(this.programmingExercise?.buildConfig?.dockerFlags || '[]') as [string, string][];
        existingFlags = existingFlags.filter(([key, _]) => this.allowedDockerFlagOptions.includes(key));
        this.dockerFlags = existingFlags;
    }

    onDisplayEditDockerFlags() {
        this.displayEditDockerFlags = !this.displayEditDockerFlags;
    }

    updateFlagKey(event: any, rowIndex: number) {
        this.dockerFlags[rowIndex][0] = event.target.value;
        this.dockerFlags[rowIndex][1] = ''; // Reset value when flag changes
        this.checkDockerFlagValidity();
    }

    updateFlagValue(event: any, rowIndex: number) {
        this.dockerFlags[rowIndex][1] = event.target.value;
        this.checkDockerFlagValidity();
    }

    addDockerFlag() {
        this.dockerFlags.push(['', '']);
        this.dockerFlags = [...this.dockerFlags];
    }

    removeDockerFlag(rowIndex: number) {
        this.dockerFlags.splice(rowIndex, 1);
        this.dockerFlags = [...this.dockerFlags];
        this.checkDockerFlagValidity();
    }

    getAllowedValues(flag: string): string[] {
        return ALLOWED_DOCKER_FLAGS[flag] || [];
    }

    checkDockerFlagValidity() {
        const nonEmptyFlags = this.dockerFlags.every(([key, value]) => key.trim() !== '' && value.trim() !== '');
        const noDuplicateKeys = this.dockerFlags.map(([key, _]) => key).filter((key, index, self) => self.indexOf(key) === index).length === this.dockerFlags.length;
        this.isValid = nonEmptyFlags && noDuplicateKeys;
        this.emitValidationState();
    }

    private emitValidationState() {
        this.isValidEmitter.emit(this.isValid);
    }
}
