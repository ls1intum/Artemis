/**
 * Result of saving exam user images: how many users were not found, how many images were saved,
 * and the matriculation numbers of the users that could not be matched.
 * Mirrors the server-side {@code ExamUsersNotFoundDTO} record.
 */
export interface ExamUsersNotFoundDTO {
    numberOfUsersNotFound: number;
    numberOfImagesSaved: number;
    listOfExamUserRegistrationNumbers?: string[];
}
