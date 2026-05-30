export enum ValidationStatus {
    VALID = 'VALID',
    INVALID = 'INVALID',
}

export type Validation = { status: ValidationStatus.INVALID; message: string } | { status: ValidationStatus.VALID };
