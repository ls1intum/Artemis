import { AbstractControl, ValidatorFn } from '@angular/forms';

export function filePatternValidator(): ValidatorFn {
    return (control: AbstractControl): { [key: string]: any } | null => {
        return validateFilePattern(control.value) ? null : { forbidden: { value: control.value } };
    };
}

function validateFilePattern(input: String | null): boolean {
    let allowed: boolean;
    let invalidPatterns = 0;
    const validPatterns: String[] = [
        'doc',
        'docx',
        'dotx',
        'xml',
        'pdf',
        'wps',
        'wpd',
        'wpqxd',
        'ps',
        'pub',
        'tex',
        'vsd',
        'bmp',
        'jpg',
        'jpeg',
        'gif',
        'pgn',
        'png',
        'txt',
        'rtf',
        'wav',
        'mp3',
        'html',
        'odt',
        'xls',
        'xlsx',
        'wks',
        'xlr',
        'csv',
        'ppt',
        'pps',
        'ppsx',
        'zip',
        'tar',
    ];

    if (input !== null && input !== '') {
        const filePatterns: String[] = input.split(',');
        for (const filePattern of filePatterns) {
            if (!validPatterns.includes(filePattern.replace(/\s/g, '').toLowerCase())) {
                invalidPatterns++;
            }
        }
    }
    allowed = invalidPatterns <= 0;
    invalidPatterns = 0;
    return allowed;
}
