import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { faBan, faDownload } from '@fortawesome/free-solid-svg-icons';

export enum CsvFieldSeparator {
    TAB = '\t',
    COMMA = ',',
    SEMICOLON = ';',
    SPACE = ' ',
    PERIOD = '.',
}

export enum CsvQuoteStrings {
    DOUBLE_QUOTES = '\x22',
    SINGLE_QUOTES = '\x27',
    NONE = '',
}

export interface CsvExportOptions {
    fieldSeparator: CsvFieldSeparator;
    quoteStrings: CsvQuoteStrings;
}

@Component({
    selector: 'jhi-csv-export-modal',
    templateUrl: './csv-export-modal.component.html',
    styleUrls: ['./csv-export-modal.component.scss'],
})
export class CsvExportModalComponent implements OnInit {
    readonly CsvFieldSeparator = CsvFieldSeparator;
    readonly CsvQuoteStrings = CsvQuoteStrings;

    options: CsvExportOptions;
    locale: string;

    // Icons
    faBan = faBan;
    faDownload = faDownload;

    constructor(private activeModal: NgbActiveModal, private translateService: TranslateService) {
        this.locale = translateService.getBrowserLang() ?? 'en';
    }

    ngOnInit(): void {
        // set default csv export options not based on Artemis locale but on the user's system
        switch (this.locale) {
            case 'de':
                this.options = {
                    fieldSeparator: CsvFieldSeparator.SEMICOLON,
                    quoteStrings: CsvQuoteStrings.DOUBLE_QUOTES,
                };
                break;
            default:
                this.options = {
                    fieldSeparator: CsvFieldSeparator.COMMA,
                    quoteStrings: CsvQuoteStrings.DOUBLE_QUOTES,
                };
        }
    }

    setCsvFieldSeparator(separator: CsvFieldSeparator) {
        this.options.fieldSeparator = separator;
    }

    setCsvQuoteString(quoteString: CsvQuoteStrings) {
        this.options.quoteStrings = quoteString;
    }

    cancel() {
        this.activeModal.dismiss();
    }

    onFinish() {
        this.activeModal.close(this.options);
    }
}
