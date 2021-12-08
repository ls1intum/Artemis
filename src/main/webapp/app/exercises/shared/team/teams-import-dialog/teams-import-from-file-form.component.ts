import { ChangeDetectorRef, Component, EventEmitter, Output } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { User } from 'app/core/user/user.model';
import { StudentWithTeam } from 'app/entities/team.model';
import { Team } from 'app/entities/team.model';
import { shortNamePattern } from 'app/shared/constants/input.constants';
import { parse } from 'papaparse';

const csvColumns = Object.freeze({
    registrationNumber: 'registrationnumber',
    matrikelNummer: 'matrikelnummer',
    matriculationNumber: 'matriculationnumber',
    name: 'name',
    vorname: 'vorname',
    nachname: 'nachname',
    firstName: 'firstname',
    familyName: 'familyname',
    lastName: 'lastname',
    surname: 'surname',
    login: 'login',
    username: 'username',
    user: 'user',
    benutzer: 'benutzer',
    benutzerName: 'benutzername',
    team: 'team',
    teamName: 'teamname',
    gruppe: 'gruppe',
});

type CsvEntry = object;

@Component({
    selector: 'jhi-teams-import-from-file-form',
    templateUrl: './teams-import-from-file-form.component.html',
    styleUrls: ['./teams-import-from-file-form.component.scss'],
})
export class TeamsImportFromFileFormComponent {
    @Output() teamsChanged = new EventEmitter<Team[]>();
    sourceTeams?: Team[];
    importedTeams: StudentWithTeam[] = [];
    importFile?: File;
    importFileName: string;
    loading: boolean;

    constructor(private changeDetector: ChangeDetectorRef, private translate: TranslateService) {}

    /**
     * Move file reader creation to separate function to be able to mock
     * https://fromanegg.com/post/2015/04/22/easy-testing-of-code-involving-native-methods-in-javascript/
     */
    generateFileReader() {
        return new FileReader();
    }

    /**
     * Converts teams from file to expected team type
     * @param {FileReader} $fileReader object that is generated by generateFileReader
     */
    async onFileLoadImport(fileReader: FileReader) {
        try {
            // Read the file and get list of teams from the file
            if (this.importFile?.type === 'application/json') {
                this.importedTeams = JSON.parse(fileReader.result as string) as StudentWithTeam[];
            } else if (this.importFile?.type === 'text/csv') {
                const csvEntries = await this.parseCSVFile(fileReader.result as string);
                this.importedTeams = this.convertCsvEntries(csvEntries);
            } else {
                throw new Error(this.translate.instant('artemisApp.team.invalidFileType', { fileType: this.importFile?.type }));
            }
            this.sourceTeams = this.convertTeams(this.importedTeams);
            this.teamsChanged.emit(this.sourceTeams);
            this.loading = false;
            // Clearing html elements,
            this.importFile = undefined;
            this.importFileName = '';
            const control = document.getElementById('importFileInput') as HTMLInputElement;
            if (control) {
                control.value = '';
            }
        } catch (e) {
            this.loading = false;
            const message = `${this.translate.instant('artemisApp.team.errors.importFailed')} ${e}`;
            alert(message);
        }
    }

    /**
     * Assigns the uploaded import file
     * @param event object containing the uploaded file
     */
    setImportFile(event: any): void {
        if (event.target.files.length) {
            const fileList: FileList = event.target.files;
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
     * Parses a csv file and returns a promise with a list of rows
     * @param csv File content that should be parsed
     */
    private parseCSVFile(csv: string): Promise<CsvEntry[]> {
        return new Promise((resolve, reject) => {
            parse(csv, {
                download: false,
                header: true,
                transformHeader: (header: string) => header.toLowerCase().replace(' ', '').replace('_', ''),
                skipEmptyLines: true,
                complete: (results) => resolve(results.data as CsvEntry[]),
                error: (error: any) => reject(error),
            });
        });
    }

    /**
     * Parse the content of a csv file to students with teams
     * @param entries All entries of the csv file
     */
    convertCsvEntries(entries: CsvEntry[]): StudentWithTeam[] {
        return entries.map(
            (entry) =>
                ({
                    registrationNumber: entry[csvColumns.registrationNumber] || entry[csvColumns.matrikelNummer] || entry[csvColumns.matriculationNumber] || undefined,
                    username:
                        entry[csvColumns.login] ||
                        entry[csvColumns.username] ||
                        entry[csvColumns.user] ||
                        entry[csvColumns.benutzer] ||
                        entry[csvColumns.benutzerName] ||
                        undefined,
                    firstName: entry[csvColumns.firstName] || entry[csvColumns.vorname] || undefined,
                    lastName:
                        entry[csvColumns.lastName] ||
                        entry[csvColumns.familyName] ||
                        entry[csvColumns.surname] ||
                        entry[csvColumns.name] ||
                        entry[csvColumns.nachname] ||
                        undefined,
                    teamName: entry[csvColumns.teamName] || entry[csvColumns.team] || entry[csvColumns.gruppe] || undefined,
                } as StudentWithTeam),
        );
    }

    /**
     * Convert imported team list to normal teams
     */
    convertTeams(importTeam: StudentWithTeam[]): Team[] {
        const teams: Team[] = [];
        importTeam.forEach((student, index) => {
            const newStudent = new User();
            newStudent.firstName = student.firstName ?? '';
            newStudent.lastName = student.lastName ?? '';
            newStudent.visibleRegistrationNumber = student.registrationNumber;
            newStudent.login = student.username;
            const entryNr = index + 1;

            if ((typeof student.username !== 'string' || !student.username.trim()) && (typeof student.registrationNumber !== 'string' || !student.registrationNumber.trim())) {
                throw new Error(this.translate.instant('artemisApp.team.missingUserNameOrId', { entryNr }));
            }
            newStudent.name = `${newStudent.firstName} ${newStudent.lastName}`.trim();

            if (typeof student.teamName !== 'string' || !student.teamName.trim()) {
                throw new Error(this.translate.instant('artemisApp.team.teamName.missingTeamName', { entryNr, studentName: newStudent.name }));
            }

            const shortName = student.teamName.replace(/[^0-9a-z]/gi, '').toLowerCase();
            if (!shortName.match(shortNamePattern)) {
                throw new Error(this.translate.instant('artemisApp.team.teamName.pattern', { entryNr, teamName: shortName }));
            }

            const teamIndex = teams.findIndex((team) => team.name === student.teamName);
            if (teamIndex === -1) {
                const newTeam = new Team();
                newTeam.name = student.teamName;
                newTeam.shortName = shortName;
                newTeam.students = [newStudent];
                teams.push(newTeam);
            } else {
                teams[teamIndex].students = [...teams[teamIndex].students!, newStudent];
            }
        });
        return teams;
    }
}
