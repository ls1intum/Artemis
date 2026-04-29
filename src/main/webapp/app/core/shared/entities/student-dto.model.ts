export class StudentDTO {
    public login: string;
    public firstName: string;
    public lastName: string;
    public registrationNumber: string;
    public email: string;
    /**
     * Optional password used by the admin CSV import when creating internal users in bulk.
     * Ignored by all non-admin import flows (course/exam/tutorial group enrollment).
     */
    public password?: string;
}
