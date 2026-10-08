package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.api.dtos.DemoLectures;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.factories.LectureFactory;
import de.tum.cit.aet.artemis.lecture.repository.ExerciseUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.service.AttachmentVideoUnitService;
import de.tum.cit.aet.artemis.lecture.service.AttachmentVideoUnitSlideSplitJob;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;
import de.tum.cit.aet.artemis.lecture.service.SlideSplitterService;

/**
 * Creates the lectures of the demo course seeded by the {@code demo} profile.
 * <p>
 * Only exists on the node that seeds the demo data, so none of this is instantiated on a regular instance.
 */
@Conditional(LectureEnabled.class)
@Controller
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class LectureDemoApi extends AbstractLectureApi {

    /**
     * Titles of the demo lectures. A title is the idempotency key of its lecture within the demo course, just as the unit names below are the idempotency keys of the units
     * within their lecture, so none of them may change.
     */
    private static final String ARCHITECTURE_LECTURE_TITLE = "Software Architecture";

    private static final String ALGORITHMS_LECTURE_TITLE = "Algorithms and Complexity";

    private static final String MODELING_LECTURE_TITLE = "Object-Oriented Modeling";

    private static final Duration LECTURE_DURATION = Duration.ofMinutes(90);

    private static final String ARCHITECTURE_TEXT = """
            An **architectural style** is a named set of design decisions that has proven itself in many systems. Knowing the common styles lets you describe a system in a few
            words and predict its strengths and weaknesses before writing a single line of code.

            ## Layered architecture

            The system is split into layers such as *presentation*, *business logic* and *data access*, and each layer only uses the one directly below it. A layer can therefore
            be replaced without touching the others, which is why most business applications start out this way.

            ## Client-server

            Clients send requests to a server that owns the data and the business rules. Artemis itself is an example: the Angular client in your browser talks to a Spring Boot
            server, which keeps everything in a database.

            ## Microservices

            The system consists of small services that are deployed independently and talk to each other over the network. Teams gain autonomy and can scale every service on its
            own, but they pay for it with network failures, eventual consistency and a much more demanding operation.

            > There is no best style, only the one that fits the quality attributes your system needs most. You weigh these trade-offs yourself in the essay
            > *Monolith or Microservices?*.
            """;

    /**
     * The slides of the lecture about software architecture. The first line of a slide is its title, every further line one bullet point.
     */
    private static final List<String> ARCHITECTURE_SLIDES = List.of("""
            Software Architecture
            Introduction to Software Engineering
            Structures, styles and the trade-offs between them
            """, """
            Common Architectural Styles
            Layered: presentation, business logic and data access
            Client-server: clients request services from a server
            Pipes and filters: data flows through processing steps
            Microservices: small services, deployed independently
            """, """
            Quality Attributes Drive the Design
            Performance, scalability, security and modifiability
            Every style favours some quality attributes over others
            Make the trade-offs explicit and record the decisions
            """, """
            Monolith or Microservices?
            Monolith: simple to build, test and deploy at first
            Microservices: independent deployment and scaling
            The price: network failures and eventual consistency
            Start modular and extract services where it pays off
            """);

    private static final String ALGORITHMS_TEXT = """
            **Big O notation** describes how the running time or the memory of an algorithm grows with the size *n* of its input. It ignores constant factors and keeps only the
            dominant term, because that is what decides how an algorithm copes with large inputs.

            | Complexity | Name         | Example                                 |
            |------------|--------------|-----------------------------------------|
            | O(1)       | constant     | reading an array element by its index   |
            | O(log n)   | logarithmic  | binary search in a sorted array         |
            | O(n)       | linear       | finding the maximum of an unsorted list |
            | O(n log n) | linearithmic | merge sort                              |
            | O(n²)      | quadratic    | bubble sort and insertion sort          |

            ## Why it matters

            Sorting one million elements takes about 20 million steps with merge sort, but in the order of a trillion with bubble sort. No hardware makes up for the wrong
            complexity class.

            ## Best, average and worst case

            Quicksort needs O(n log n) steps on average, but O(n²) in the worst case, for example when the pivot is always the smallest element. Insertion sort is quadratic in
            general, but linear on input that is already sorted. Always ask which case your input actually is.
            """;

    private static final String MODELING_TEXT = """
            A **class diagram** shows the static structure of a system: its classes, their attributes and operations, and the relationships between them. It is the most widely
            used UML diagram and a good starting point for every object-oriented design.

            ## Finding classes

            Read the requirements and look for nouns. In *"A member borrows books from the library"*, `Member`, `Book` and `Library` are candidates for classes, while the verb
            *borrows* hints at an association between `Member` and `Book`.

            ## Relationships

            - **Association:** a `Member` borrows `Book`s. Multiplicities state how many are involved, for example `0..*` books per member.
            - **Composition:** a `Library` is made up of `Shelf` objects, which cannot exist without their library.
            - **Inheritance:** a `Novel` is a special kind of `Book` and inherits its attributes and operations.

            > Model the problem domain first, not its implementation. You practise this in the exercise *Class Diagram: Library Management System*, which refines this example:
            > there, a member borrows a particular copy of a book.
            """;

    private static final Logger log = LoggerFactory.getLogger(LectureDemoApi.class);

    private final LectureRepository lectureRepository;

    private final ExerciseUnitRepository exerciseUnitRepository;

    private final AttachmentVideoUnitService attachmentVideoUnitService;

    private final SlideSplitterService slideSplitterService;

    private final ChannelService channelService;

    private final LectureUnitService lectureUnitService;

    public LectureDemoApi(LectureRepository lectureRepository, ExerciseUnitRepository exerciseUnitRepository, AttachmentVideoUnitService attachmentVideoUnitService,
            SlideSplitterService slideSplitterService, ChannelService channelService, LectureUnitService lectureUnitService) {
        this.lectureRepository = lectureRepository;
        this.exerciseUnitRepository = exerciseUnitRepository;
        this.attachmentVideoUnitService = attachmentVideoUnitService;
        this.slideSplitterService = slideSplitterService;
        this.channelService = channelService;
        this.lectureUnitService = lectureUnitService;
    }

    /**
     * Creates the lectures of the demo course with their units in the given course, as far as they do not exist yet: one lecture each about software architecture, algorithms
     * and complexity, and object-oriented modeling. Every lecture has a text unit, a link to further reading and an exercise unit per exercise of its topic, and the lecture
     * about software architecture also has its slides.
     * <p>
     * A lecture is identified by its title within the course, a unit by its name within its lecture and an exercise unit by its exercise. A lecture that exists is kept as it
     * is, and only the units missing from it are created again, at its end, so that deleted demo content comes back on the next startup without touching anything else. The
     * lectures and units are created like the production creation paths create them, see the methods below.
     * <p>
     * The lectures took place weekly over the past three weeks at the full hour, so that they show up in the calendar and as past lectures. Their dates are relative to the time
     * the lectures are created and are never revisited, which keeps them in the past for the lifetime of the instance. All units are released immediately.
     *
     * @param course                the demo course the lectures belong to.
     * @param architectureExercises the exercises about software architecture, linked from their lecture in this order.
     * @param algorithmsExercises   the exercises about algorithms and complexity, linked from their lecture in this order.
     * @param modelingExercises     the exercises about object-oriented modeling, linked from their lecture in this order.
     * @return the seeded units of each lecture, so that dependent modules can link to them. Units that users added to the demo lectures are left out, so that seeding never
     *         links them to anything.
     */
    public DemoLectures createDemo(Course course, List<Exercise> architectureExercises, List<Exercise> algorithmsExercises, List<Exercise> modelingExercises) {
        ZonedDateTime fullHour = ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS);
        return new DemoLectures(seedArchitectureLecture(course, fullHour.minusWeeks(3), architectureExercises),
                seedAlgorithmsLecture(course, fullHour.minusWeeks(2), algorithmsExercises), seedModelingLecture(course, fullHour.minusWeeks(1), modelingExercises));
    }

    /**
     * Lets the demo student complete lecture units the way they tick off a unit in the client, which calls {@code LectureUnitResource#completeLectureUnit}: the demo student
     * worked through the lecture about software architecture and read the text of the lecture about algorithms, but has not started on the lecture about object-oriented
     * modeling of last week yet. Exercise units are completed through their exercise, so they are left out. Completing a unit again changes nothing, so only the missing
     * completions are created.
     * <p>
     * The resource also updates the competency progress of the student, which only covers the competencies the unit is linked to already. The demo units are linked
     * afterwards, so the progress is left to the seeding of the competencies, which updates it once all links exist.
     *
     * @param lectures the seeded units of the demo lectures.
     * @param student  the demo student.
     */
    public void completeDemoUnits(DemoLectures lectures, User student) {
        Stream.concat(lectures.architecture().stream().filter(unit -> !(unit instanceof ExerciseUnit)), lectures.algorithms().stream().filter(TextUnit.class::isInstance))
                .forEach(unit -> lectureUnitService.setLectureUnitCompletion(unit, student, true));
    }

    private List<LectureUnit> seedArchitectureLecture(Course course, ZonedDateTime startDate, List<Exercise> exercises) {
        Lecture lecture = findOrCreateLecture(course, ARCHITECTURE_LECTURE_TITLE,
                "How to structure a software system: the common architectural styles, the quality attributes they favour and the trade-offs between them.", startDate);
        List<LectureUnit> units = new ArrayList<>();
        units.add(seedTextUnit(lecture, "Architectural Styles at a Glance", ARCHITECTURE_TEXT));
        units.add(seedSlides(lecture, "Software Architecture Slides",
                "The slides of this lecture: architectural styles, quality attributes and the choice between a monolith and microservices.", ARCHITECTURE_SLIDES));
        units.add(seedOnlineUnit(lecture, "Further Reading: Software Architecture",
                "An overview of the discipline, its history and the most common architectural styles and patterns.", "https://en.wikipedia.org/wiki/Software_architecture"));
        exercises.forEach(exercise -> units.add(seedExerciseUnit(lecture, exercise)));
        return units;
    }

    private List<LectureUnit> seedAlgorithmsLecture(Course course, ZonedDateTime startDate, List<Exercise> exercises) {
        Lecture lecture = findOrCreateLecture(course, ALGORITHMS_LECTURE_TITLE,
                "How to reason about the efficiency of algorithms with Big O notation, illustrated by the classic sorting algorithms.", startDate);
        List<LectureUnit> units = new ArrayList<>();
        units.add(seedTextUnit(lecture, "An Introduction to Big O Notation", ALGORITHMS_TEXT));
        units.add(seedOnlineUnit(lecture, "Further Reading: Sorting Algorithms",
                "A comparison of the common sorting algorithms by their best, average and worst case complexity, their memory use and their stability.",
                "https://en.wikipedia.org/wiki/Sorting_algorithm"));
        exercises.forEach(exercise -> units.add(seedExerciseUnit(lecture, exercise)));
        return units;
    }

    private List<LectureUnit> seedModelingLecture(Course course, ZonedDateTime startDate, List<Exercise> exercises) {
        Lecture lecture = findOrCreateLecture(course, MODELING_LECTURE_TITLE,
                "How to turn requirements into a UML class diagram with classes, attributes, associations, multiplicities and inheritance.", startDate);
        List<LectureUnit> units = new ArrayList<>();
        units.add(seedTextUnit(lecture, "From Requirements to Class Diagrams", MODELING_TEXT));
        units.add(seedOnlineUnit(lecture, "Further Reading: UML Class Diagrams",
                "A reference of the class diagram notation, from visibility and multiplicities to aggregation, composition and generalization.",
                "https://en.wikipedia.org/wiki/Class_diagram"));
        exercises.forEach(exercise -> units.add(seedExerciseUnit(lecture, exercise)));
        return units;
    }

    /**
     * Finds the demo lecture with the given title in the course, or creates it like {@code LectureResource#createLecture} does: the lecture is saved and gets its channel.
     *
     * @return the lecture with its units.
     */
    private Lecture findOrCreateLecture(Course course, String title, String description, ZonedDateTime startDate) {
        Optional<Lecture> existingLecture = lectureRepository.findAllByTitleAndCourseIdWithLectureUnits(title, course.getId()).stream().findFirst();
        if (existingLecture.isPresent()) {
            log.debug("Demo lecture '{}' already exists, skipping its creation", title);
            return existingLecture.get();
        }

        Lecture lecture = lectureRepository.save(LectureFactory.generateLecture(title, description, startDate, startDate.plus(LECTURE_DURATION), course));
        channelService.createLectureChannel(lecture, Optional.empty());
        log.info("Created demo lecture '{}' with id {}", title, lecture.getId());
        return lecture;
    }

    /**
     * Returns the unit of the lecture that the given condition identifies, or creates it if the lecture has no such unit.
     *
     * @param lecture    the lecture with the units it had before seeding.
     * @param identifies whether a unit of the lecture is the unit to seed.
     * @param creation   creates the missing unit.
     * @return the existing or created unit.
     */
    private LectureUnit seedUnit(Lecture lecture, Predicate<LectureUnit> identifies, Supplier<LectureUnit> creation) {
        return lecture.getLectureUnits().stream().filter(identifies).findFirst().orElseGet(() -> {
            LectureUnit unit = creation.get();
            log.info("Created demo lecture unit '{}' with id {} in lecture '{}'", unit.getName(), unit.getId(), lecture.getTitle());
            return unit;
        });
    }

    private static Predicate<LectureUnit> named(String name) {
        return unit -> name.equals(unit.getName());
    }

    /**
     * Mirrors {@code TextUnitResource#createTextUnit}.
     */
    private LectureUnit seedTextUnit(Lecture lecture, String name, String content) {
        return seedUnit(lecture, named(name), () -> persistThroughLecture(lecture.getId(), LectureFactory.generateTextUnit(name, content)));
    }

    /**
     * Mirrors {@code OnlineUnitResource#createOnlineUnit}.
     */
    private LectureUnit seedOnlineUnit(Lecture lecture, String name, String description, String source) {
        return seedUnit(lecture, named(name), () -> {
            OnlineUnit unit = new OnlineUnit();
            unit.setName(name);
            unit.setDescription(description);
            unit.setSource(source);
            return persistThroughLecture(lecture.getId(), unit);
        });
    }

    /**
     * Mirrors {@code AttachmentVideoUnitResource#createAttachmentVideoUnit} for an uploaded PDF: the unit is persisted through the lecture, the file is stored as its
     * attachment, and the PDF is split into its slides in the background.
     */
    private LectureUnit seedSlides(Lecture lecture, String name, String description, List<String> slides) {
        return seedUnit(lecture, named(name), () -> {
            AttachmentVideoUnit unit = new AttachmentVideoUnit();
            unit.setName(name);
            unit.setDescription(description);
            AttachmentVideoUnit persistedUnit = (AttachmentVideoUnit) persistThroughLecture(lecture.getId(), unit);

            Attachment attachment = new Attachment();
            attachment.setName(name);
            attachment.setAttachmentType(AttachmentType.FILE);
            MultipartFile file = FileUtil.convertByteArrayToMultipart(name, ".pdf", renderSlides(name, slides));
            // Keeps the name of the file, like the client requests it.
            AttachmentVideoUnit savedUnit = attachmentVideoUnitService.saveAttachmentVideoUnit(persistedUnit, attachment, file, true);
            slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnitSlideSplitJob.of(savedUnit, null, null));
            return savedUnit;
        });
    }

    /**
     * Mirrors {@code ExerciseUnitResource#createExerciseUnit}, which persists the unit itself rather than through the lecture: saving the lecture would only give the managed
     * copy of the unit an id.
     */
    private LectureUnit seedExerciseUnit(Lecture lecture, Exercise exercise) {
        return seedUnit(lecture,
                unit -> unit instanceof ExerciseUnit exerciseUnit && exerciseUnit.getExercise() != null && exercise.getId().equals(exerciseUnit.getExercise().getId()), () -> {
                    ExerciseUnit unit = new ExerciseUnit();
                    unit.setExercise(exercise);
                    // Appending sets the lecture of the unit and its position in the lecture.
                    lectureRepository.findByIdWithLectureUnitsElseThrow(lecture.getId()).addLectureUnit(unit);
                    return exerciseUnitRepository.saveAndFlush(unit);
                });
    }

    /**
     * Appends the unit to the lecture and persists it through the lecture, because the order of the units is their position in the lecture, see
     * {@code TextUnitResource#createTextUnit}. Like every creation request, this loads the lecture anew, so that the unit is appended behind the units created before it.
     *
     * @return the persisted unit.
     */
    private LectureUnit persistThroughLecture(long lectureId, LectureUnit unit) {
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsElseThrow(lectureId);
        lecture.addLectureUnit(unit);
        return lectureRepository.saveAndFlush(lecture).getLectureUnits().getLast();
    }

    /**
     * Renders slides as a PDF with one 16:9 page per slide.
     *
     * @param title  the title of the document.
     * @param slides the slides, each with its title in the first line and one bullet point in every further line.
     * @return the content of the PDF.
     */
    private static byte[] renderSlides(String title, List<String> slides) {
        PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font bulletFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        try (PDDocument document = new PDDocument()) {
            document.getDocumentInformation().setTitle(title);
            for (String slide : slides) {
                List<String> lines = slide.lines().toList();
                PDPage page = new PDPage(new PDRectangle(960, 540));
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(titleFont, 36);
                    content.newLineAtOffset(60, 450);
                    content.showText(lines.getFirst());
                    content.setFont(bulletFont, 22);
                    // One line per bullet point, so a bullet point has to fit the width of a slide. Wrap the lines once longer bullet points are needed.
                    for (String bullet : lines.subList(1, lines.size())) {
                        content.newLineAtOffset(0, -60);
                        content.showText("•  " + bullet);
                    }
                    content.endText();
                }
            }
            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            document.save(pdf);
            return pdf.toByteArray();
        }
        catch (IOException exception) {
            throw new UncheckedIOException("Could not render the demo slides '" + title + "'", exception);
        }
    }
}
