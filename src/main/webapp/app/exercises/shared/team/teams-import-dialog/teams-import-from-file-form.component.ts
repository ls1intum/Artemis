import { ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output, ViewEncapsulation } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { Team, TeamList } from 'app/entities/team.model';

@Component({
    selector: 'jhi-teams-import-from-file-form',
    templateUrl: './teams-import-from-file-form.component.html',
    styleUrls: ['./teams-import-from-file-form.component.scss'],
    // encapsulation: ViewEncapsulation.None,
})
export class TeamsImportFromFileFormComponent implements OnInit {
    @Input() isSourceTeamFreeOfAnyConflicts: Function;
    @Input() studentLoginsAlreadyExistingInExercise: string[];
    @Input() teamShortNamesAlreadyExistingInExercise: string[];
    @Output() teamsChanged = new EventEmitter<Team[]>();
    sourceTeams?: Team[];
    importedTeams: TeamList = { students: [] };
    importFile?: Blob;
    importFileName: string;
    loading: boolean;

    constructor(private changeDetector: ChangeDetectorRef) {}

    ngOnInit(): void {}

    /**
     * Move file reader creation to separate function to be able to mock
     * https://fromanegg.com/post/2015/04/22/easy-testing-of-code-involving-native-methods-in-javascript/
     */
    generateFileReader() {
        return new FileReader();
    }

    onFileLoadImport(fileReader: FileReader) {
        try {
            // Read the file and get list of questions from the file
            this.importedTeams = JSON.parse(fileReader.result as string) as TeamList;
            this.sourceTeams = this.convertTeams(this.importedTeams);
            this.teamsChanged.emit(this.sourceTeams);
            this.loading = false;
            // this.verifyAndImportQuestions(questions);
            // Clearing html elements,
            this.importFile = undefined;
            this.importFileName = '';
            const control = document.getElementById('importFileInput') as HTMLInputElement;
            if (control) {
                control.value = '';
            }
        } catch (e) {
            alert(`Import Quiz Failed! Invalid quiz file. ${e}`);
        }
    }

    /**
     * Assigns the uploaded import file
     * @param $event object containing the uploaded file
     */
    setImportFile($event: any): void {
        if ($event.target.files.length) {
            const fileList: FileList = $event.target.files;
            this.importFile = fileList[0];
            this.importFileName = this.importFile['name'];
            this.loading = true;
        }
        if (!this.importFile) {
            return;
        }
        const fileReader = this.generateFileReader();
        fileReader.onload = () => this.onFileLoadImport(fileReader);
        fileReader.readAsText(this.importFile);
        this.changeDetector.detectChanges();
    }

    /**
     * Convert imported team list to normal teams
     */
    convertTeams(importTeam: TeamList): Team[] {
        const teams: Team[] = [];
        importTeam.students!.forEach((student) => {
            const index = teams.findIndex((team) => team.name === student.team);
            const newStudent = new User();
            newStudent.firstName = student.firstName;
            newStudent.lastName = student.lastName;
            newStudent.visibleRegistrationNumber = student.registrationNumber;
            newStudent.name = `${student.firstName} ${student.lastName}`;
            newStudent.login = student.login;
            if (index === -1) {
                const newTeam = new Team();
                newTeam.name = student.team;
                newTeam.shortName = student.team.replace(/[^0-9a-z]/gi, '').toLowerCase();
                newTeam.students = [newStudent];
                teams.push(newTeam);
            } else {
                teams[index].students = [...teams[index].students!, newStudent];
            }
        });
        return teams;
    }

    isTeamFreeOfAnyConflicts(team: Team) {
        if (!this.isSourceTeamFreeOfAnyConflicts(team)) {
            console.log('not free', team);
            return false;
        }

        if (this.sourceTeams!.filter((te) => te.shortName === team.shortName).length > 1) {
            console.log('same short name', team);
            return false;
        }

        if (
            this.importedTeams &&
            team.students?.some((student) => {
                return (
                    this.importedTeams!.students!.filter((importedStudent) => {
                        console.log(importedStudent.registrationNumber, student.visibleRegistrationNumber);
                        return importedStudent.registrationNumber === student.visibleRegistrationNumber;
                    }).length > 1
                );
            })
        ) {
            console.log('same student', team);
            return false;
        }

        // // Short name of source team already exists among teams of destination exercise
        // if (this.teamShortNamesAlreadyExistingInExercise.includes(sourceTeam.shortName!)) {
        //     return false;
        // }
        // // One of the students of the source team is already part of a team in the destination exercise
        // if (sourceTeam.students!.some((student) => this.studentLoginsAlreadyExistingInExercise.includes(student.login!))) {
        //     return false;
        // }
        // // This source team can be imported without any issues
        // return true;
        return true;
    }
}
