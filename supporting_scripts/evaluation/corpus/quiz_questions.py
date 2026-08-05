"""Source quiz for the evaluation: 10 applied design-pattern questions.

Design constraints from the work order: multiple choice and short answer only (drag-and-drop is
unsupported and rejected server-side), applied questions rather than definition recall, plausible
distractors, and several questions with more than one correct option.

Three properties matter for what the matrix measures and are deliberate:

* **Every question carries a scenario.** A question with no setting gives a domain re-theme (C3, C4,
  C9-C14) nothing to change but decorative framing, so it would read identically across most of the
  matrix and flatten the intent-fidelity signal for reasons unrelated to the pipeline.
* **Several questions embed a short code sketch.** Identifiers inside code re-theme visibly, which is
  the concrete evidence the rubric's intent-fidelity criterion needs; prose-only questions can be
  re-themed by swapping a noun.
* **Every multiple-choice question scores ALL_OR_NOTHING.** For the multi-selects this is the point:
  under PROPORTIONAL_WITHOUT_PENALTY, ticking every option scores full marks whenever wrong options
  cost nothing, so the optimal strategy is fixed and a requested difficulty change (C1, C2) cannot show
  up in them at all. For the single-choice questions it is not a choice — ``MultipleChoiceQuestion``
  ``isValid()`` rejects a single-choice question whose scoring type is anything else, and an invalid
  question fails the quiz half's verification gate.

Short-answer spots are written as ``[-spot N]`` markers in the question text; ``tempID`` values are
stable literals so the payload is reproducible run to run. A spot may map to several accepted
solutions, which is used where a defensible synonym would otherwise fail the 85 % similarity
threshold: ``ScoringStrategyShortAnswerUtil`` iterates every solution mapped to a spot and stops at
the first match, so several mapped solutions genuinely mean "any of these".

One coupling to keep in mind when reading results: Q9 embeds the source *programming* exercise
verbatim (``SortStrategy``, ``BubbleSort``, ``MergeSort``, ``Context``, ``Policy``, ``List<Date>``).
That helps comparability across the two halves, but it also means the halves are not independent
samples — a failure mode tied to that listing, such as the prompts' protection of pattern-role names
interacting badly with a re-theme, can surface in both and must not be read as two independent
confirmations.
"""

from typing import Any, Dict, List, Sequence, Tuple

# Required for single-choice questions and deliberate for the multi-selects (see module docstring).
MULTIPLE_CHOICE_SCORING = "ALL_OR_NOTHING"
# Short answer: partial credit across the spots of one question is wanted, and no validity rule constrains it.
SHORT_ANSWER_SCORING = "PROPORTIONAL_WITHOUT_PENALTY"


def _mc(title: str, text: str, options: Sequence[Tuple[str, bool, str]], hint: str, single: bool = True, points: float = 1.0) -> Dict[str, Any]:
    return {
        "type": "multiple-choice",
        "title": title,
        "text": text,
        "hint": hint,
        "points": points,
        "scoringType": MULTIPLE_CHOICE_SCORING,
        "randomizeOrder": False,
        "singleChoice": single,
        "answerOptions": [{"text": option, "isCorrect": correct, "explanation": explanation} for option, correct, explanation in options],
    }


def _sa(title: str, text: str, spots: Sequence[Tuple[int, int, Sequence[str]]], hint: str, points: float = 1.0) -> Dict[str, Any]:
    """``spots``: list of (spotNr, tempIdBase, accepted solution texts).

    Every accepted solution for a spot is mapped to that spot, so a defensible synonym scores.
    """
    spot_dtos = [{"tempID": 1000 + temp_id, "spotNr": spot_nr, "width": 20} for spot_nr, temp_id, _ in spots]
    solution_dtos: List[Dict[str, Any]] = []
    mappings: List[Dict[str, Any]] = []
    for _, temp_id, solutions in spots:
        for offset, solution in enumerate(solutions):
            solution_temp_id = 2000 + temp_id * 10 + offset
            solution_dtos.append({"tempID": solution_temp_id, "text": solution})
            mappings.append({"spotTempId": 1000 + temp_id, "solutionTempId": solution_temp_id})
    return {
        "type": "short-answer",
        "title": title,
        "text": text,
        "hint": hint,
        "points": points,
        "scoringType": SHORT_ANSWER_SCORING,
        "randomizeOrder": False,
        "spots": spot_dtos,
        "solutions": solution_dtos,
        "correctMappings": mappings,
        "similarityValue": 85,
        "matchLetterCase": False,
    }


QUIZ_QUESTIONS: List[Dict[str, Any]] = [
    _mc(
        "Choosing a pattern for interchangeable algorithms",
        "A billing service must pick between three tax-calculation algorithms at runtime, depending on the "
        "customer's country. The set of algorithms grows every year. Which design is the best fit?",
        [
            ("Define a `TaxCalculator` interface, one implementation per country, and inject the chosen one.", True, "This is the Strategy pattern: the algorithm varies independently of the client."),
            ("Add a `switch` over the country code inside the billing service.", False, "Every new country modifies the billing service, violating the open/closed principle."),
            ("Subclass the billing service once per country.", False, "The variation is one algorithm, not the whole service; this multiplies subclasses."),
            ("Wrap the billing service in a decorator per country.", False, "Decorator adds behaviour around an object; it does not select between alternatives."),
        ],
        hint="Ask which part of the design varies, and how often a new case is added.",
    ),
    _mc(
        "Consequences of the Observer pattern",
        "A stock ticker publishes price updates to many independent dashboard widgets. The ticker keeps a "
        "list of registered listeners and calls them in a loop:\n\n"
        "```java\n"
        "public void publish(Price price) {\n"
        "    for (PriceListener listener : listeners) {\n"
        "        listener.onPrice(price);\n"
        "    }\n"
        "}\n"
        "```\n\n"
        "Which statements about this design are correct?",
        [
            ("The ticker needs no compile-time knowledge of the concrete widget classes.", True, "Listeners are known only through the `PriceListener` interface."),
            ("A widget that throws inside `onPrice` prevents the remaining widgets from being notified.", True, "The loop has no isolation, so the exception propagates out of `publish`."),
            ("Widgets cannot be registered or removed once the ticker has been constructed.", False, "Registration is a runtime operation; that is the point of the pattern."),
            ("The notification order is guaranteed by the pattern to match registration order.", False, "The pattern makes no such guarantee; here it is an accident of the list implementation."),
        ],
        hint="Two claims follow from the loop as written, not from the pattern in the abstract.",
        single=False,
        points=2.0,
    ),
    _mc(
        "Adapter versus Facade",
        "A team integrates a third-party payment SDK whose method names and data types do not match the "
        "application's `PaymentGateway` interface. Only one SDK class is involved, and the application must "
        "keep calling `PaymentGateway`. Which pattern applies?",
        [
            ("Adapter", True, "Adapter converts one existing interface into another the client expects."),
            ("Facade", False, "Facade simplifies a whole subsystem; here a single interface is being converted."),
            ("Proxy", False, "Proxy keeps the same interface and controls access to the subject."),
            ("Bridge", False, "Bridge separates abstraction and implementation up front; it is not a retrofit."),
        ],
        hint="How many classes are behind the new interface, and does the interface stay the same?",
    ),
    _mc(
        "Reviewing a shared-configuration class",
        "A configuration class in a web application is written like this and is read from every request "
        "handler:\n\n"
        "```java\n"
        "public class AppConfig {\n"
        "    private static AppConfig instance;\n"
        "    private AppConfig() { }\n"
        "    public static AppConfig getInstance() {\n"
        "        if (instance == null) {\n"
        "            instance = new AppConfig();\n"
        "        }\n"
        "        return instance;\n"
        "    }\n"
        "}\n"
        "```\n\n"
        "Which criticisms of this code are justified?",
        [
            ("Two request threads can both observe `instance == null` and create two configurations.", True, "The lazy check is a classic unsynchronised race."),
            ("Tests of a request handler cannot easily substitute a different configuration.", True, "The handler reaches for global state instead of receiving a collaborator."),
            ("The private constructor prevents `AppConfig` from implementing an interface.", False, "Constructor visibility and interface implementation are unrelated."),
            ("The design necessarily uses more memory than passing a configuration object around.", False, "One instance exists either way; memory is not the issue."),
        ],
        hint="Consider what happens under concurrent requests, and what a unit test can control.",
        single=False,
        points=2.0,
    ),
    _mc(
        "Picking a creational pattern",
        "A document editor must create shapes whose concrete classes are decided by a plug-in loaded at "
        "startup. The editor itself must not reference the plug-in's classes. Which approach fits best?",
        [
            ("An abstract factory the plug-in implements and registers with the editor.", True, "The editor depends only on the factory and product interfaces."),
            ("A builder with a fluent API in the editor.", False, "Builder assembles a complex object step by step; it does not decouple from concrete classes."),
            ("Direct `new` calls guarded by an `if` on the plug-in name.", False, "This reintroduces the compile-time dependency the requirement forbids."),
            ("A prototype registry cloning shapes the editor constructs itself.", False, "The editor would still have to construct the originals."),
        ],
        hint="The constraint is about compile-time dependencies, not about construction complexity.",
    ),
    _mc(
        "Template Method in practice",
        "A report generator fixes the order of the steps and lets subclasses vary two of them:\n\n"
        "```java\n"
        "public abstract class ReportGenerator {\n"
        "    public final Report generate() {\n"
        "        Data data = load();\n"
        "        Data shaped = transform(data);\n"
        "        return write(render(shaped));\n"
        "    }\n"
        "    protected Data load() { return Data.fromDefaultSource(); }\n"
        "    protected abstract Data transform(Data data);\n"
        "    protected abstract String render(Data data);\n"
        "}\n"
        "```\n\n"
        "Which statements about this design are correct?",
        [
            ("Declaring `generate()` final is what protects the invariant the pattern exists to protect.", True, "The skeleton, not the steps, is the fixed part."),
            ("`transform` and `render` are the hook methods a subclass must supply.", True, "They are the abstract, varying steps."),
            ("The pattern here relies on composition rather than inheritance.", False, "Template Method is inheritance-based; Strategy is the composition-based alternative."),
            ("`load()` would have to be abstract for the pattern to apply.", False, "A step with a sensible default may stay concrete, as `load()` is."),
        ],
        hint="Look at which member is final and which are abstract.",
        single=False,
        points=2.0,
    ),
    _mc(
        "Diagnosing a smell in a report class",
        "During review, a `Report` class is found to look like this, with about forty methods in total and no "
        "field shared between the two halves:\n\n"
        "```java\n"
        "class Report {\n"
        "    private Connection connection;   // used only by the query methods\n"
        "    private StringBuilder output;    // used only by the formatting methods\n"
        "    List<Row> queryRows(...)  { ... }        // ~20 methods\n"
        "    String formatAsHtml(...)  { ... }        // ~20 methods\n"
        "}\n"
        "```\n\n"
        "Which refactoring addresses the underlying problem?",
        [
            ("Split it into two classes along the two responsibilities.", True, "The class violates the single-responsibility principle: two unrelated reasons to change."),
            ("Extract one interface covering all forty methods.", False, "An interface over both responsibilities preserves the coupling."),
            ("Make the formatting methods static.", False, "Static methods do not reduce the responsibility overload."),
            ("Introduce a singleton accessor for the class.", False, "This adds global state without addressing cohesion."),
        ],
        hint="Two fields, two disjoint groups of methods — count the reasons this class would have to change.",
    ),
    _sa(
        "Choosing patterns for a media-streaming client",
        "A media-streaming client is being extended. For each requirement, name the pattern that fits.\n\n"
        "Subtitles, audio normalisation, and bandwidth logging must be switchable per stream and combinable "
        "in any order, each wrapping the stream and keeping the same `Stream` interface. That calls for the "
        "[-spot 1] pattern.\n\n"
        "Premium content must be fetched only when it is first played, and access rights must be checked "
        "before the real stream object is touched, without the player noticing a difference. That calls for "
        "the [-spot 2] pattern.\n\n"
        "A playback session behaves differently while buffering, playing, and paused, and the client should "
        "not be a growing `switch` over a status field. That calls for the [-spot 3] pattern.",
        [(1, 11, ["Decorator"]), (2, 12, ["Proxy"]), (3, 13, ["State"])],
        hint="All three keep the client's interface unchanged; they differ in why.",
        points=1.5,
    ),
    _sa(
        "Roles in a strategy-based sorting application",
        "A scheduling tool sorts appointment dates with interchangeable algorithms:\n\n"
        "```java\n"
        "public interface SortStrategy { void performSort(List<Date> input); }\n"
        "public class BubbleSort implements SortStrategy { ... }\n"
        "public class MergeSort  implements SortStrategy { ... }\n"
        "public class Context { private List<Date> dates; private SortStrategy sortAlgorithm; ... }\n"
        "public class Policy  { private Context context; public void configure() { ... } }\n"
        "```\n\n"
        "Using the class names from this listing: the class that holds the dates and a reference to the "
        "currently selected algorithm is [-spot 1]. The type that both `BubbleSort` and `MergeSort` "
        "implement is [-spot 2]. The class whose `configure()` method decides which algorithm to select "
        "is [-spot 3].",
        # No "Strategy" synonym here: the stem tells the student to answer with the identifiers from the
        # listing, and Strategy is not in it. Accepting both would make the question contradict its own
        # instruction — the pilot's critique gate flagged exactly that, and it was right.
        [(1, 21, ["Context"]), (2, 22, ["SortStrategy"]), (3, 23, ["Policy"])],
        hint="Answer with the identifiers shown in the listing.",
        points=1.5,
    ),
    _sa(
        "Reviewing a notification service",
        "A reviewer rejects the following class:\n\n"
        "```java\n"
        "public class OrderService {\n"
        "    private final SmtpMailer mailer = new SmtpMailer(\"mail.internal\", 25);\n"
        "    public void placeOrder(Order order) { /* ... */ mailer.send(order.customerEmail(), \"...\"); }\n"
        "}\n"
        "```\n\n"
        "The reviewer's first objection is that `OrderService` names a concrete low-level class instead of an "
        "abstraction such as a `Notifier` interface; the principle being violated is abbreviated [-spot 1] "
        "(three letters). The reviewer's fix is to stop constructing the collaborator inside the class and "
        "supply it from outside instead — the general two-word name for that technique is [-spot 2].",
        # A three-letter answer sits awkwardly at 85 % similarity: a trailing period or a dotted spelling
        # would otherwise mark a correct student wrong on a technicality.
        [(1, 31, ["DIP", "D.I.P.", "DIP."]), (2, 32, ["dependency injection", "constructor injection"])],
        hint="The first answer is a three-letter abbreviation from the SOLID set.",
        points=1.0,
    ),
]