package de.tum.in.www1.artemis.service.iris;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisHestiaSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisChatSessionRepository;
import de.tum.in.www1.artemis.service.iris.session.IrisChatSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisHestiaSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisSessionSubServiceInterface;

/**
 * Service for managing Iris sessions.
 */
@Service
@Profile("iris")
public class IrisSessionService {

    private final UserRepository userRepository;

    private final IrisChatSessionService irisChatSessionService;

    private final IrisHestiaSessionService irisHestiaSessionService;

    private final IrisChatSessionRepository irisChatSessionRepository;

    public IrisSessionService(UserRepository userRepository, IrisChatSessionService irisChatSessionService, IrisHestiaSessionService irisHestiaSessionService,
            IrisChatSessionRepository irisChatSessionRepository) {
        this.userRepository = userRepository;
        this.irisChatSessionService = irisChatSessionService;
        this.irisHestiaSessionService = irisHestiaSessionService;
        this.irisChatSessionRepository = irisChatSessionRepository;
    }

    /**
     * Checks if the exercise connected to the session has Iris activated
     *
     * @param session the session to check for
     */
    public void checkIsIrisActivated(IrisSession session) {
        getIrisSessionSubService(session).checkIsIrisActivated(session);
    }

    /**
     * Creates a new Iris session for the given exercise and user.
     * If a session already exists, a BadRequestException is thrown.
     *
     * @param exercise The exercise the session belongs to
     * @param user     The user the session belongs to
     * @return The created session
     */
    public IrisSession createChatSessionForProgrammingExercise(ProgrammingExercise exercise, User user) {
        if (irisChatSessionRepository.findByExerciseIdAndUserId(exercise.getId(), user.getId()).isPresent()) {
            throw new BadRequestException("Iris Session already exists for exercise " + exercise.getId() + " and user " + user.getId());
        }

        var irisSession = new IrisChatSession();
        irisSession.setExercise(exercise);
        irisSession.setUser(user);
        return irisChatSessionRepository.save(irisSession);
    }

    /**
     * Checks if the user has access to the Iris session.
     * If the user is null, the user is fetched from the database.
     *
     * @param session The session to check
     * @param user    The user to check
     */
    public void checkHasAccessToIrisSession(IrisSession session, User user) {
        if (user == null) {
            user = userRepository.getUserWithGroupsAndAuthorities();
        }
        getIrisSessionSubService(session).checkHasAccessToIrisSession(session, user);
    }

    /**
     * Sends a request to Iris to get a message for the given session.
     * It decides which Iris subsystem should handle it based on the session type.
     * Currently, only the chat subsystem exists.
     *
     * @param session The session to get a message for
     */
    public void requestMessageFromIris(IrisSession session) {
        getIrisSessionSubService(session).requestAndHandleResponse(session);
    }

    private IrisSessionSubServiceInterface getIrisSessionSubService(IrisSession session) {
        if (session instanceof IrisChatSession) {
            return irisChatSessionService;
        }
        else if (session instanceof IrisHestiaSession) {
            return irisHestiaSessionService;
        }
        else {
            throw new BadRequestException("Unknown Iris session type " + session.getClass().getSimpleName());
        }
    }

    /**
     * Utility class for removing all unnecessary information from problem statements
     */
    private static class ProblemStatementUtils {

        /**
         * Removes all unnecessary information from problem statements and cuts it to 2000 characters max length
         * The maximum length is enforced because we cannot waste too many tokens on the problem statement
         *
         * @param problemStatement The problem statement to be truncated
         * @return The condensed problem statement cut with length <= 2000 characters
         */
        public static String truncateProblemStatement(String problemStatement) {
            if (problemStatement == null || "".equals(problemStatement)) {
                // TODO: Error handling in the future
                return "undefined";
            }
            String temp = problemStatement.replace("\r", " ").replace("\n", " ");
            // removes bold delimiters
            temp = removeControlStructure(temp, "(\\*\\*)(.*?)(\\*\\*)");
            // removes italics delimiters
            temp = removeControlStructure(temp, "(\\*)(.*?)(\\*)");
            // removes image tags (![image](*))
            temp = removeControlStructureReplaceWithSpace(temp, "(!\\[image)(.*?)(]\\()(.*?)(\\))");
            // removes scene tags (![scene](*))
            temp = removeControlStructureReplaceWithSpace(temp, "(!\\[scene)(.*?)(]\\()(.*?)(\\))");
            // removes named tag (![<name>](*)) but keeps <name>
            temp = removeControlStructure(temp, "(!\\[)(.*?)(]\\()(.*?)(\\))");
            // removes named link ([<name>](*)) but keeps <name>
            temp = removeControlStructure(temp, "(\\[)(.*?)(]\\()(.*?)(\\))");
            // removes headers (# <header>) but keeps <header>
            temp = removeHeaders(temp);
            temp = removeMarks(temp);
            // removes @startuml * @enduml blocks
            temp = removeControlStructureReplaceWithSpace(temp, "(@startuml)(.*?)(@enduml)");
            // removes tasks ([task][<name>][*]) but keeps <name>
            temp = removeControlStructure(temp, "(\\[task]\\[)(.*?)(]\\()(.*?)(\\))");
            // removes opening divs (<div *>)
            temp = removeControlStructureReplaceWithSpace(temp, "(<div)(.*?)(>)");
            // removes opening span (<span *>)
            temp = removeControlStructureReplaceWithSpace(temp, "(<span)(.*?)(>)");
            // removes opening color (<color *>)
            temp = removeControlStructureReplaceWithSpace(temp, "(<color)(.*?)(>)");
            // removes opening style (<style *>)
            temp = removeControlStructureReplaceWithSpace(temp, "(<style)(.*?)(>)");
            temp = removeNonParameterHTMLTags(temp);
            temp = removeLineBreaks(temp);
            temp = temp.trim().replaceAll(" +", " ");
            return temp.substring(0, Math.min(temp.length(), 2000));
        }

        /**
         * Removes control structures in the form (<begin> <name> <end>) but keeps the <name>
         *
         * @param problemStatement The problem statement to be truncated
         * @param pString          The String which represents the regex of the control structure
         * @return The problem statement without control structures to be removed
         */
        private static String removeControlStructure(String problemStatement, String pString) {
            String temp = problemStatement;
            Pattern pattern = Pattern.compile(pString, Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(problemStatement);
            while (matcher.find()) {
                temp = temp.replace(matcher.group(0), matcher.group(2));
            }
            return temp;
        }

        /**
         * Removes control structures and replaces them with " "
         *
         * @param problemStatement The problem statement to be truncated
         * @param pString          The String which represents the regex of the control structure
         * @return The problem statement without control structures to be removed
         */
        private static String removeControlStructureReplaceWithSpace(String problemStatement, String pString) {
            String temp = problemStatement;
            Pattern pattern = Pattern.compile(pString, Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(problemStatement);
            while (matcher.find()) {
                temp = temp.replace(matcher.group(0), " ");
            }
            return temp;
        }

        /**
         * Removes all headers (# <text>) from the problem statement but keeps the <text>
         *
         * @param problemStatement The problem statement to be truncated
         * @return Problem statement without headers
         */
        private static String removeHeaders(String problemStatement) {
            String temp = problemStatement;
            String pString = "(#)(.*)";
            Pattern pattern = Pattern.compile(pString, Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(problemStatement);
            while (matcher.find()) {
                String line = matcher.group(0);
                line = line.replace("#", "");
                temp = temp.replace(matcher.group(0), line);
            }
            return temp;
        }

        /**
         * Removes other markdown marks (---, ***, - - - -, ~~~) from the problem statement
         *
         * @param problemStatement The problem statement to be truncated
         * @return Problem statement without specific markdown marks
         */
        private static String removeMarks(String problemStatement) {
            String temp = problemStatement;
            temp = temp.replace("---", "");
            temp = temp.replace("***", "");
            temp = temp.replace("- - - -", "");
            temp = temp.replace("~~~", "");
            return temp;
        }

        /**
         * Removes other non-parameter or closing tags (/span, /color, /style, /div, ins, del) from the problem statement
         *
         * @param problemStatement The problem statement to be truncated
         * @return Problem statement without specific tags
         */
        private static String removeNonParameterHTMLTags(String problemStatement) {
            String temp = problemStatement;
            temp = temp.replace("</span>", "");
            temp = temp.replace("</color>", "");
            temp = temp.replace("</style>", "");
            temp = temp.replace("</div>", "");
            temp = temp.replace("<ins>", "");
            temp = temp.replace("</ins>", "");
            temp = temp.replace("<del>", "");
            temp = temp.replace("</del>", "");
            return temp;
        }

        /**
         * Removes different representations of line breaks from the problem statement
         *
         * @param problemStatement The problem statement to be truncated
         * @return Problem statement without line break characters
         */
        private static String removeLineBreaks(String problemStatement) {
            String temp = problemStatement;
            temp = temp.replace("\\r", " ");
            temp = temp.replace("\\n", " ");
            temp = temp.replace("<br>", " ");
            temp = temp.replace("<br >", " ");
            temp = temp.replace("<br />", " ");
            return temp;
        }
    }
}
