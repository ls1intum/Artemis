import { Team } from 'app/exercise/entities/team.model';

/**
 * Formats a team for displaying it in an autocomplete search
 *
 * Examples:
 * Team 1 (team1) ⟹ Max Mustermann (ga12abc), Erika Musterfrau (ga34def)
 * Team 2 (team2)
 *
 * @param team
 */
export function formatTeamAsSearchResult(team: Team) {
    const { name, shortName, students } = team;
    const studentsFormatted = students?.map((s) => `${s.name} (${s.login})`).join(', ');
    return `${name} (${shortName})` + (students && students.length > 0 ? ` ⟹ ${studentsFormatted}` : '');
}
