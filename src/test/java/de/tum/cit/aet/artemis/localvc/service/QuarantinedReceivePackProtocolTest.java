package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.PacketLineIn;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * Pushes through the receive pack of LocalVC, with the real pre-receive hook and the real protocol: the request of a client (the commands, then the pack) is written to
 * bytes and read by the receive pack, as a client sends it over HTTP or SSH.
 * <p>
 * What the tests establish, for every kind of push:
 * <ul>
 * <li>an accepted push ends with its objects in the repository, its reference moved, nothing of the temporary place left, and the history readable;</li>
 * <li>a rejected push, or one that breaks off or fails, leaves the repository exactly as it was: the same objects, the same references, no temporary place;</li>
 * <li>what the commands alone can decide is decided before the objects are read.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class QuarantinedReceivePackProtocolTest {

    private static final String DEFAULT_BRANCH = "main";

    private static final String DEFAULT_REF = Constants.R_HEADS + DEFAULT_BRANCH;

    private static final PersonIdent AUTHOR = new PersonIdent("Ada Lovelace", "ada@example.com");

    @Mock
    private LocalVCServletService localVCServletService;

    @TempDir
    Path directory;

    /** The repository of the client, where commits are made. */
    private Repository client;

    private Path serverPath;

    private LocalVCPrePushHook hook;

    @BeforeEach
    void setUp() throws Exception {
        client = Git.init().setDirectory(directory.resolve("client").toFile()).setInitialBranch(DEFAULT_BRANCH).call().getRepository();
        serverPath = directory.resolve("server.git");
        Git.init().setDirectory(serverPath.toFile()).setBare(true).setInitialBranch(DEFAULT_BRANCH).call().close();
        lenient().when(localVCServletService.isBranchNameAllowedForRepository(any(), anyString())).thenReturn(LocalVCServletService.BranchingStatus.BRANCH_ALLOWED);
        lenient().when(localVCServletService.getMaxRepositorySizeBytes(any())).thenReturn(Long.MAX_VALUE);
        lenient().when(localVCServletService.isUserAllowedToForcePush(any(), any())).thenReturn(false);
        User user = new User();
        user.setId(1L);
        user.setLogin("ge12abc");
        hook = new LocalVCPrePushHook(localVCServletService, user, new GitOperationAudit());
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    // ------------------------------------------------------------------------------------------------------------------------------------------------------------
    // Building and sending a push
    // ------------------------------------------------------------------------------------------------------------------------------------------------------------

    /** Content that does not compress, so that the pack of a commit with it is large. */
    private static byte[] incompressible(int size) {
        byte[] bytes = new byte[size];
        new Random(42).nextBytes(bytes);
        return bytes;
    }

    /** A tree entry of a commit. */
    private record Entry(String name, FileMode mode, byte[] content) {

        static Entry file(String name, String content) {
            return new Entry(name, FileMode.REGULAR_FILE, content.getBytes(StandardCharsets.UTF_8));
        }

        static Entry file(String name, byte[] content) {
            return new Entry(name, FileMode.REGULAR_FILE, content);
        }
    }

    /** Makes a commit on the client with exactly the given entries. Names must be given in the order of Git (plain ASCII, sorted). */
    private ObjectId commit(ObjectId parent, Entry... entries) throws IOException {
        try (ObjectInserter inserter = client.newObjectInserter()) {
            TreeFormatter tree = new TreeFormatter();
            for (Entry entry : entries) {
                ObjectId id = entry.mode() == FileMode.GITLINK ? ObjectId.fromString("0123456789012345678901234567890123456789")
                        : inserter.insert(Constants.OBJ_BLOB, entry.content());
                tree.append(entry.name(), entry.mode(), id);
            }
            CommitBuilder commit = new CommitBuilder();
            commit.setTreeId(inserter.insert(tree));
            if (parent != null) {
                commit.setParentId(parent);
            }
            commit.setAuthor(AUTHOR);
            commit.setCommitter(AUTHOR);
            commit.setMessage("a commit");
            ObjectId id = inserter.insert(commit);
            inserter.flush();
            return id;
        }
    }

    /** The commands, a flush, and the pack with everything that the commits need except what the other side has. */
    private byte[] request(List<ReceiveCommand> commands, Set<ObjectId> haves, boolean thin, boolean withPack) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PacketLineOut out = new PacketLineOut(bytes);
        for (int i = 0; i < commands.size(); i++) {
            ReceiveCommand command = commands.get(i);
            String line = command.getOldId().name() + " " + command.getNewId().name() + " " + command.getRefName();
            out.writeString(i == 0 ? line + "\0 report-status agent=test" : line);
        }
        out.end();
        if (withPack) {
            Set<ObjectId> wants = new HashSet<>();
            commands.stream().map(ReceiveCommand::getNewId).filter(id -> !ObjectId.zeroId().equals(id)).forEach(wants::add);
            try (PackWriter writer = new PackWriter(client)) {
                writer.setThin(thin);
                writer.preparePack(NullProgressMonitor.INSTANCE, wants, haves);
                writer.writePack(NullProgressMonitor.INSTANCE, NullProgressMonitor.INSTANCE, bytes);
            }
        }
        return bytes.toByteArray();
    }

    private byte[] pushOf(ObjectId oldId, ObjectId newId, String ref, Set<ObjectId> haves) throws IOException {
        return request(List.of(new ReceiveCommand(oldId, newId, ref)), haves, true, true);
    }

    /** The answer of the server for one reference. */
    private record RefResult(String ref, boolean ok, String message) {
    }

    /** What happened to a push, as the client reads it in the report of the server. */
    private record Outcome(String unpack, List<RefResult> refs, boolean packWasRead, IOException failure) {

        RefResult only() {
            assertThat(refs).hasSize(1);
            return refs.getFirst();
        }

        /** The push is accepted if the objects were unpacked and every reference was updated. */
        boolean accepted() {
            return failure == null && "ok".equals(unpack) && !refs.isEmpty() && refs.stream().allMatch(RefResult::ok);
        }
    }

    private Repository openServer() throws IOException {
        return new FileRepositoryBuilder().setGitDir(serverPath.toFile()).setBare().setMustExist(true).build();
    }

    private ReceivePack newReceivePack(Repository server, AtomicBoolean reached) throws IOException {
        ReceivePack receivePack = LocalVCReceivePackLimits.apply(QuarantinedReceivePack.create(server, hook::examineBeforeObjects));
        // as over HTTP, where the request holds the commands and the server does not advertise its references again
        receivePack.setBiDirectionalPipe(false);
        receivePack.setPreReceiveHook((pack, commands) -> {
            reached.set(true);
            hook.onPreReceive(pack, commands);
        });
        return receivePack;
    }

    private Outcome receive(byte[] request) throws IOException {
        CountingInputStream counting = new CountingInputStream(new ByteArrayInputStream(request));
        Outcome outcome = receive((InputStream) counting);
        // the pack follows the commands, so a request that was not read to its end did not have its objects read
        return new Outcome(outcome.unpack(), outcome.refs(), counting.count() >= request.length, outcome.failure());
    }

    /** Counts what is read from a request. */
    private static final class CountingInputStream extends FilterInputStream {

        private long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        long count() {
            return count;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                count++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                count += read;
            }
            return read;
        }
    }

    private Outcome receive(InputStream request) throws IOException {
        AtomicBoolean reached = new AtomicBoolean();
        ByteArrayOutputStream report = new ByteArrayOutputStream();
        IOException failure = null;
        try (Repository server = openServer()) {
            ReceivePack receivePack = newReceivePack(server, reached);
            try {
                receivePack.receive(request, report, new ByteArrayOutputStream());
            }
            catch (IOException e) {
                // JGit may pass a failure of the pack on after it told the client, the state of the repository is what counts
                failure = e;
            }
        }
        return parseReport(report.toByteArray(), reached.get(), failure);
    }

    /** Reads the report that the server sends: the result of the unpacking, then one line per reference. */
    private static Outcome parseReport(byte[] report, boolean reached, IOException failure) throws IOException {
        String unpack = null;
        List<RefResult> refs = new ArrayList<>();
        PacketLineIn in = new PacketLineIn(new ByteArrayInputStream(report));
        try {
            String line;
            while (!PacketLineIn.isEnd(line = in.readString())) {
                if (line.startsWith("unpack ")) {
                    unpack = line.substring("unpack ".length());
                }
                else if (line.startsWith("ok ")) {
                    refs.add(new RefResult(line.substring(3), true, ""));
                }
                else if (line.startsWith("ng ")) {
                    String rest = line.substring(3);
                    int space = rest.indexOf(' ');
                    refs.add(new RefResult(space < 0 ? rest : rest.substring(0, space), false, space < 0 ? "" : rest.substring(space + 1)));
                }
            }
        }
        catch (EOFException | RuntimeException e) {
            // no report, as when the request was empty or failed before the server answered
        }
        return new Outcome(unpack, refs, reached, failure);
    }

    // ------------------------------------------------------------------------------------------------------------------------------------------------------------
    // What the repository holds
    // ------------------------------------------------------------------------------------------------------------------------------------------------------------

    private boolean serverHas(ObjectId id) throws IOException {
        try (Repository server = openServer()) {
            return server.getObjectDatabase().has(id);
        }
    }

    private ObjectId serverRef(String ref) throws IOException {
        try (Repository server = openServer()) {
            var found = server.exactRef(ref);
            return found == null ? null : found.getObjectId();
        }
    }

    /** All files below the object directory, as relative names, so that two states of the repository can be compared. */
    private Set<String> serverObjectFiles() throws IOException {
        Path objects = serverPath.resolve("objects");
        try (Stream<Path> files = Files.walk(objects)) {
            return files.filter(Files::isRegularFile).map(file -> objects.relativize(file).toString()).collect(Collectors.toSet());
        }
    }

    private void assertNoPlaceOfAPush() throws IOException {
        try (Stream<Path> entries = Files.list(serverPath)) {
            assertThat(entries.map(entry -> entry.getFileName().toString())).as("the temporary place of the push is removed")
                    .noneMatch(name -> name.startsWith(PushQuarantine.DIRECTORY_PREFIX));
        }
    }

    private void assertNoLeftoverFilesInTheObjectDirectory() throws IOException {
        assertThat(serverObjectFiles()).as("no lock or unfinished file is left in the object directory").noneMatch(name -> name.endsWith(".keep") || name.contains("tmp_"));
    }

    /** Reads every commit, tree and blob that is reachable from the id, which fails if anything is missing or damaged. */
    private int readEverythingReachableFrom(ObjectId commitId) throws IOException {
        int objects = 0;
        try (Repository server = openServer(); RevWalk walk = new RevWalk(server)) {
            walk.markStart(walk.parseCommit(commitId));
            for (RevCommit commit : walk) {
                objects++;
                try (TreeWalk tree = new TreeWalk(server)) {
                    tree.setRecursive(true);
                    tree.addTree(commit.getTree());
                    while (tree.next()) {
                        assertThat(server.open(tree.getObjectId(0)).getSize()).isGreaterThanOrEqualTo(0);
                        objects++;
                    }
                }
            }
        }
        return objects;
    }

    private void assertAccepted(Outcome outcome, ObjectId newCommit, String ref) throws IOException {
        assertThat(outcome.accepted()).as("the push is accepted: %s %s", outcome.unpack(), outcome.refs()).isTrue();
        assertThat(serverRef(ref)).as("the reference points to the pushed commit").isEqualTo(newCommit);
        assertThat(serverHas(newCommit)).isTrue();
        readEverythingReachableFrom(newCommit);
        assertNoPlaceOfAPush();
        assertNoLeftoverFilesInTheObjectDirectory();
    }

    /** The push changed nothing: the same files in the object directory, the same reference, no place. */
    private void assertUntouched(Set<String> filesBefore, ObjectId refBefore, String ref) throws IOException {
        assertThat(serverObjectFiles()).as("the object directory is exactly as before").isEqualTo(filesBefore);
        assertThat(serverRef(ref)).as("the reference is as before").isEqualTo(refBefore);
        assertNoPlaceOfAPush();
    }

    // ------------------------------------------------------------------------------------------------------------------------------------------------------------
    // Accepted pushes
    // ------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void anAcceptedPushStoresItsObjectsMovesTheReferenceAndLeavesNoPlace() throws Exception {
        ObjectId commit = commit(null, Entry.file("Main.java", "class Main {}"));

        Outcome outcome = receive(pushOf(ObjectId.zeroId(), commit, DEFAULT_REF, Set.of()));

        assertAccepted(outcome, commit, DEFAULT_REF);
        assertThat(outcome.packWasRead()).isTrue();
    }

    @Test
    void aPushWithManyObjectsIsStoredAsAPackThatIsReadable() throws Exception {
        // above the number of objects that JGit unpacks into single files, so that the pack itself is kept
        Entry[] entries = new Entry[300];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = Entry.file(String.format("file%03d.txt", i), "content of file " + i);
        }
        ObjectId commit = commit(null, entries);

        Outcome outcome = receive(pushOf(ObjectId.zeroId(), commit, DEFAULT_REF, Set.of()));

        assertAccepted(outcome, commit, DEFAULT_REF);
        try (Stream<Path> packs = Files.list(serverPath.resolve("objects").resolve("pack"))) {
            assertThat(packs.map(pack -> pack.getFileName().toString())).as("the pack and its index are in the repository").anyMatch(name -> name.endsWith(".pack"))
                    .anyMatch(name -> name.endsWith(".idx"));
        }
        assertThat(readEverythingReachableFrom(commit)).isGreaterThan(300);
    }

    @Test
    void aPushThatBuildsOnEarlierPushesIsAcceptedAndKeepsTheHistoryReadable() throws Exception {
        ObjectId first = commit(null, Entry.file("Main.java", "class Main {}"));
        assertAccepted(receive(pushOf(ObjectId.zeroId(), first, DEFAULT_REF, Set.of())), first, DEFAULT_REF);
        ObjectId second = commit(first, Entry.file("Main.java", "class Main { void run() {} }"), Entry.file("Util.java", "class Util {}"));

        Outcome outcome = receive(pushOf(first, second, DEFAULT_REF, Set.of(first)));

        assertAccepted(outcome, second, DEFAULT_REF);
        assertThat(serverHas(first)).as("the earlier push is still there").isTrue();
    }

    @Test
    void aThinPackThatNeedsAnObjectOfTheRepositoryIsCompleted() throws Exception {
        // a large file that changes a little: the new version is sent as a difference to the old one, which only the repository has
        byte[] base = new byte[200_000];
        Arrays.fill(base, (byte) 'a');
        ObjectId first = commit(null, Entry.file("data.txt", base));
        assertAccepted(receive(pushOf(ObjectId.zeroId(), first, DEFAULT_REF, Set.of())), first, DEFAULT_REF);
        byte[] changed = base.clone();
        changed[100_000] = 'b';
        ObjectId second = commit(first, Entry.file("data.txt", changed));

        Outcome outcome = receive(pushOf(first, second, DEFAULT_REF, Set.of(first)));

        assertAccepted(outcome, second, DEFAULT_REF);
        try (Repository server = openServer()) {
            ObjectId blob = ObjectId.fromString(server.resolve(second.name() + ":data.txt").name());
            assertThat(server.open(blob).getBytes()).isEqualTo(changed);
        }
    }

    @Test
    void aPushThatSendsAnObjectTheRepositoryHasAlreadyIsAccepted() throws Exception {
        ObjectId first = commit(null, Entry.file("Main.java", "class Main {}"));
        assertAccepted(receive(pushOf(ObjectId.zeroId(), first, DEFAULT_REF, Set.of())), first, DEFAULT_REF);
        // the client does not say that it knows the first commit, so everything is sent again
        ObjectId second = commit(first, Entry.file("Main.java", "class Main {}"), Entry.file("Other.java", "class Other {}"));

        Outcome outcome = receive(request(List.of(new ReceiveCommand(first, second, DEFAULT_REF)), Set.of(), false, true));

        assertAccepted(outcome, second, DEFAULT_REF);
    }

    @Test
    void aSecondPushAfterAnAcceptedOneAndNoLeftoversInBetween() throws Exception {
        ObjectId first = commit(null, Entry.file("a.txt", "a"));
        ObjectId second = commit(first, Entry.file("a.txt", "a"), Entry.file("b.txt", "b"));
        ObjectId third = commit(second, Entry.file("a.txt", "a"), Entry.file("b.txt", "b"), Entry.file("c.txt", "c"));

        assertAccepted(receive(pushOf(ObjectId.zeroId(), first, DEFAULT_REF, Set.of())), first, DEFAULT_REF);
        assertAccepted(receive(pushOf(first, second, DEFAULT_REF, Set.of(first))), second, DEFAULT_REF);
        assertAccepted(receive(pushOf(second, third, DEFAULT_REF, Set.of(second))), third, DEFAULT_REF);
    }

    // ------------------------------------------------------------------------------------------------------------------------------------------------------------
    // Rejected before the objects are read
    // ------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void aBranchThatIsNotAllowedIsRefusedBeforeTheObjectsAreRead() throws Exception {
        lenient().when(localVCServletService.isBranchNameAllowedForRepository(any(), anyString())).thenReturn(LocalVCServletService.BranchingStatus.BRANCHING_DISABLED);
        ObjectId commit = commit(null, Entry.file("data.bin", incompressible(300_000)));
        Set<String> before = serverObjectFiles();

        Outcome outcome = receive(pushOf(ObjectId.zeroId(), commit, Constants.R_HEADS + "feature", Set.of()));

        assertThat(outcome.accepted()).isFalse();
        assertThat(outcome.only().message()).isEqualTo("You cannot push to a branch other than the default branch.");
        assertThat(outcome.packWasRead()).as("the objects of the push are not even read").isFalse();
        assertThat(serverHas(commit)).isFalse();
        assertUntouched(before, null, Constants.R_HEADS + "feature");
    }

    @Test
    void aBranchNameThatDoesNotMatchTheRegexIsRefusedBeforeTheObjectsAreRead() throws Exception {
        lenient().when(localVCServletService.isBranchNameAllowedForRepository(any(), anyString())).thenReturn(LocalVCServletService.BranchingStatus.NAME_DOES_NOT_MATCH_REGEX);
        ObjectId commit = commit(null, Entry.file("data.bin", incompressible(300_000)));
        Set<String> before = serverObjectFiles();

        Outcome outcome = receive(pushOf(ObjectId.zeroId(), commit, Constants.R_HEADS + "feature", Set.of()));

        assertThat(outcome.accepted()).isFalse();
        assertThat(outcome.packWasRead()).isFalse();
        assertUntouched(before, null, Constants.R_HEADS + "feature");
    }

    @Test
    void anAllowedOtherBranchIsAccepted() throws Exception {
        ObjectId commit = commit(null, Entry.file("Main.java", "class Main {}"));

        Outcome outcome = receive(pushOf(ObjectId.zeroId(), commit, Constants.R_HEADS + "feature", Set.of()));

        assertAccepted(outcome, commit, Constants.R_HEADS + "feature");
    }

    @Test
    void aPushOfSeveralRefsIsRefusedAsAWholeBeforeTheObjectsAreRead() throws Exception {
        ObjectId commit = commit(null, Entry.file("data.bin", incompressible(300_000)));
        Set<String> before = serverObjectFiles();
        List<ReceiveCommand> commands = List.of(new ReceiveCommand(ObjectId.zeroId(), commit, DEFAULT_REF),
                new ReceiveCommand(ObjectId.zeroId(), commit, Constants.R_HEADS + "other"));

        Outcome outcome = receive(request(commands, Set.of(), true, true));

        assertThat(outcome.refs()).hasSize(2).allSatisfy(result -> {
            assertThat(result.ok()).isFalse();
            assertThat(result.message()).isEqualTo("You cannot push multiple refs at once.");
        });
        assertThat(outcome.packWasRead()).isFalse();
        assertThat(serverRef(DEFAULT_REF)).isNull();
        assertThat(serverRef(Constants.R_HEADS + "other")).isNull();
        assertThat(serverObjectFiles()).isEqualTo(before);
        assertNoPlaceOfAPush();
    }

    @Test
    void deletingABranchIsRefused() throws Exception {
        ObjectId commit = commit(null, Entry.file("Main.java", "class Main {}"));
        assertAccepted(receive(pushOf(ObjectId.zeroId(), commit, DEFAULT_REF, Set.of())), commit, DEFAULT_REF);
        Set<String> before = serverObjectFiles();

        Outcome outcome = receive(request(List.of(new ReceiveCommand(commit, ObjectId.zeroId(), DEFAULT_REF)), Set.of(), true, false));

        assertThat(outcome.accepted()).isFalse();
        assertUntouched(before, commit, DEFAULT_REF);
    }

    // ------------------------------------------------------------------------------------------------------------------------------------------------------------
    // Rejected after the objects are read: nothing stays in the repository
    // ------------------------------------------------------------------------------------------------------------------------------------------------------------

    private static Stream<Arguments> contentThatIsRefused() {
        Supplier<Entry[]> symlink = () -> new Entry[] { new Entry("link", FileMode.SYMLINK, "target".getBytes(StandardCharsets.UTF_8)) };
        Supplier<Entry[]> submodule = () -> new Entry[] { new Entry("module", FileMode.GITLINK, new byte[0]) };
        Supplier<Entry[]> largeFile = () -> new Entry[] { Entry.file("big.bin", new byte[11 * 1024 * 1024]) };
        return Stream.of(Arguments.of("a symbolic link", symlink), Arguments.of("a submodule", submodule), Arguments.of("a file above 10 MB", largeFile));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contentThatIsRefused")
    void contentThatIsRefusedLeavesNothingInTheRepository(String description, Supplier<Entry[]> entries) throws Exception {
        ObjectId commit = commit(null, entries.get());
        Set<String> before = serverObjectFiles();

        Outcome outcome = receive(pushOf(ObjectId.zeroId(), commit, DEFAULT_REF, Set.of()));

        assertThat(outcome.accepted()).isFalse();
        assertThat(outcome.packWasRead()).as("the content can only be judged once it is read").isTrue();
        assertThat(serverHas(commit)).as("the commit of a rejected push is not in the repository").isFalse();
        assertUntouched(before, null, DEFAULT_REF);
    }

    @Test
    void aNameThatDenotesTheMetadataDirectoryLeavesNothingInTheRepository() throws Exception {
        ObjectId inner;
        try (ObjectInserter inserter = client.newObjectInserter()) {
            TreeFormatter inside = new TreeFormatter();
            inside.append("config", FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, "[core]".getBytes(StandardCharsets.UTF_8)));
            ObjectId insideId = inserter.insert(inside);
            TreeFormatter root = new TreeFormatter();
            root.append(".git", FileMode.TREE, insideId);
            CommitBuilder commit = new CommitBuilder();
            commit.setTreeId(inserter.insert(root));
            commit.setAuthor(AUTHOR);
            commit.setCommitter(AUTHOR);
            commit.setMessage("metadata");
            inner = inserter.insert(commit);
            inserter.flush();
        }
        Set<String> before = serverObjectFiles();

        Outcome outcome = receive(pushOf(ObjectId.zeroId(), inner, DEFAULT_REF, Set.of()));

        assertThat(outcome.accepted()).isFalse();
        assertThat(serverHas(inner)).isFalse();
        assertUntouched(before, null, DEFAULT_REF);
    }

    @Test
    void aPushThatMakesTheRepositoryTooLargeLeavesNothingInTheRepository() throws Exception {
        lenient().when(localVCServletService.getMaxRepositorySizeBytes(any())).thenReturn(1024L);
        ObjectId commit = commit(null, Entry.file("data.bin", new byte[100_000]));
        Set<String> before = serverObjectFiles();

        Outcome outcome = receive(pushOf(ObjectId.zeroId(), commit, DEFAULT_REF, Set.of()));

        assertThat(outcome.accepted()).isFalse();
        assertUntouched(before, null, DEFAULT_REF);
    }

    @Test
    void aForcePushThatIsNotAllowedLeavesNothingInTheRepository() throws Exception {
        ObjectId first = commit(null, Entry.file("Main.java", "class Main {}"));
        assertAccepted(receive(pushOf(ObjectId.zeroId(), first, DEFAULT_REF, Set.of())), first, DEFAULT_REF);
        // a commit that does not build on the first one, so that moving the branch to it rewrites history
        ObjectId unrelated = commit(null, Entry.file("Other.java", "class Other {}"));
        Set<String> before = serverObjectFiles();

        Outcome outcome = receive(pushOf(first, unrelated, DEFAULT_REF, Set.of()));

        assertThat(outcome.accepted()).isFalse();
        assertThat(outcome.only().message()).isEqualTo("You cannot force push.");
        assertThat(serverHas(unrelated)).isFalse();
        assertUntouched(before, first, DEFAULT_REF);
        readEverythingReachableFrom(first);
    }

    @Test
    void aForcePushThatIsAllowedIsAccepted() throws Exception {
        lenient().when(localVCServletService.isUserAllowedToForcePush(any(), any())).thenReturn(true);
        ObjectId first = commit(null, Entry.file("Main.java", "class Main {}"));
        assertAccepted(receive(pushOf(ObjectId.zeroId(), first, DEFAULT_REF, Set.of())), first, DEFAULT_REF);
        ObjectId unrelated = commit(null, Entry.file("Other.java", "class Other {}"));

        Outcome outcome = receive(pushOf(first, unrelated, DEFAULT_REF, Set.of()));

        assertAccepted(outcome, unrelated, DEFAULT_REF);
    }

    @Test
    void aRejectedPushDoesNotHarmTheNextAcceptedPushOfTheSameCommit() throws Exception {
        ObjectId commit = commit(null, Entry.file("Main.java", "class Main {}"));
        lenient().when(localVCServletService.isBranchNameAllowedForRepository(any(), anyString())).thenReturn(LocalVCServletService.BranchingStatus.BRANCHING_DISABLED);
        assertThat(receive(pushOf(ObjectId.zeroId(), commit, Constants.R_HEADS + "feature", Set.of())).accepted()).isFalse();

        Outcome outcome = receive(pushOf(ObjectId.zeroId(), commit, DEFAULT_REF, Set.of()));

        assertAccepted(outcome, commit, DEFAULT_REF);
    }

    @Test
    void aRejectedContentPushDoesNotHarmTheCorrectedPush() throws Exception {
        ObjectId bad = commit(null, new Entry("link", FileMode.SYMLINK, "target".getBytes(StandardCharsets.UTF_8)));
        assertThat(receive(pushOf(ObjectId.zeroId(), bad, DEFAULT_REF, Set.of())).accepted()).isFalse();
        ObjectId good = commit(null, Entry.file("Main.java", "class Main {}"));

        Outcome outcome = receive(pushOf(ObjectId.zeroId(), good, DEFAULT_REF, Set.of()));

        assertAccepted(outcome, good, DEFAULT_REF);
        assertThat(serverHas(bad)).as("the rejected commit never arrived").isFalse();
    }

    @Test
    void aRejectedPushDoesNotRemoveObjectsOfEarlierPushes() throws Exception {
        ObjectId first = commit(null, Entry.file("Main.java", "class Main {}"));
        assertAccepted(receive(pushOf(ObjectId.zeroId(), first, DEFAULT_REF, Set.of())), first, DEFAULT_REF);
        // the rejected push sends the same file again, next to a link
        ObjectId bad = commit(first, Entry.file("Main.java", "class Main {}"), new Entry("zlink", FileMode.SYMLINK, "target".getBytes(StandardCharsets.UTF_8)));

        Outcome outcome = receive(request(List.of(new ReceiveCommand(first, bad, DEFAULT_REF)), Set.of(), false, true));

        assertThat(outcome.accepted()).isFalse();
        assertThat(serverHas(first)).isTrue();
        readEverythingReachableFrom(first);
        assertThat(serverRef(DEFAULT_REF)).isEqualTo(first);
        assertNoPlaceOfAPush();
    }

    // ------------------------------------------------------------------------------------------------------------------------------------------------------------
    // Pushes that fail or break off
    // ------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void aPackThatEndsInTheMiddleLeavesNothingInTheRepository() throws Exception {
        Entry[] entries = new Entry[200];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = Entry.file(String.format("file%03d.txt", i), "content of file " + i);
        }
        ObjectId commit = commit(null, entries);
        byte[] complete = pushOf(ObjectId.zeroId(), commit, DEFAULT_REF, Set.of());
        byte[] cutOff = Arrays.copyOf(complete, complete.length * 2 / 3);
        Set<String> before = serverObjectFiles();

        receive(cutOff);

        assertThat(serverHas(commit)).isFalse();
        assertUntouched(before, null, DEFAULT_REF);
    }

    @Test
    void aConnectionThatFailsWhileThePackIsReadLeavesNothingInTheRepository() throws Exception {
        Entry[] entries = new Entry[200];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = Entry.file(String.format("file%03d.txt", i), "content of file " + i);
        }
        ObjectId commit = commit(null, entries);
        byte[] complete = pushOf(ObjectId.zeroId(), commit, DEFAULT_REF, Set.of());
        Set<String> before = serverObjectFiles();
        InputStream failing = new InputStream() {

            private final ByteArrayInputStream delegate = new ByteArrayInputStream(complete);

            private int read;

            @Override
            public int read() throws IOException {
                if (++read > complete.length / 2) {
                    throw new IOException("connection reset");
                }
                return delegate.read();
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                int count = 0;
                while (count < length) {
                    int next = read();
                    if (next < 0) {
                        break;
                    }
                    buffer[offset + count++] = (byte) next;
                }
                return count == 0 ? -1 : count;
            }
        };

        try {
            receive(failing);
        }
        catch (IOException expected) {
            // JGit may pass the failure on or report it to the client, the repository has to be untouched either way
        }

        assertThat(serverHas(commit)).isFalse();
        assertUntouched(before, null, DEFAULT_REF);
    }

    @Test
    void aPackWithoutTheObjectsItsCommitNeedsLeavesNothingInTheRepository() throws Exception {
        ObjectId first = commit(null, Entry.file("a.txt", "a"));
        ObjectId second = commit(first, Entry.file("a.txt", "a"), Entry.file("b.txt", "b"));
        // the client claims that the repository has the first commit, which it does not, so the pack lacks its objects
        byte[] request = pushOf(ObjectId.zeroId(), second, DEFAULT_REF, Set.of(first));
        Set<String> before = serverObjectFiles();

        Outcome outcome = receive(request);

        assertThat(outcome.accepted()).isFalse();
        assertThat(serverHas(second)).isFalse();
        assertUntouched(before, null, DEFAULT_REF);
    }

    @Test
    void anObjectAboveTheLimitLeavesNothingInTheRepository() throws Exception {
        // 21 MB of the same byte compresses to a few kilobytes, but the object is larger than the limit for a single object
        ObjectId commit = commit(null, Entry.file("huge.bin", new byte[(int) LocalVCReceivePackLimits.MAX_OBJECT_SIZE_BYTES + 1024 * 1024]));
        Set<String> before = serverObjectFiles();

        Outcome outcome = receive(pushOf(ObjectId.zeroId(), commit, DEFAULT_REF, Set.of()));

        assertThat(outcome.accepted()).isFalse();
        assertThat(serverHas(commit)).isFalse();
        assertUntouched(before, null, DEFAULT_REF);
    }

    @Test
    void anEmptyRequestChangesNothing() throws Exception {
        Set<String> before = serverObjectFiles();

        receive(new byte[0]);

        assertUntouched(before, null, DEFAULT_REF);
    }

    @Test
    void aFailureToMoveTheObjectsIntoTheRepositoryRejectsThePushAndLeavesTheReferenceAlone() throws Exception {
        File objects = serverPath.resolve("objects").toFile();
        File packs = serverPath.resolve("objects").resolve("pack").toFile();
        assumeThat(System.getProperty("user.name")).as("a read-only directory stops only users that are not root").isNotEqualTo("root");
        ObjectId commit = commit(null, Entry.file("Main.java", "class Main {}"));
        Set<String> before = serverObjectFiles();
        // the objects arrive as a pack or as single files, which go to one of these two directories
        assertThat(objects.setWritable(false)).isTrue();
        assertThat(packs.setWritable(false)).isTrue();
        try {
            Outcome outcome = receive(pushOf(ObjectId.zeroId(), commit, DEFAULT_REF, Set.of()));

            assertThat(outcome.accepted()).isFalse();
            assertThat(outcome.only().message()).isEqualTo("An error occurred while storing the push.");
        }
        finally {
            assertThat(objects.setWritable(true)).isTrue();
            assertThat(packs.setWritable(true)).isTrue();
        }
        assertThat(serverRef(DEFAULT_REF)).isNull();
        assertThat(serverObjectFiles()).isEqualTo(before);
        assertNoPlaceOfAPush();
    }

    // ------------------------------------------------------------------------------------------------------------------------------------------------------------
    // Several pushes at the same time
    // ------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void aRejectedPushDoesNotAffectAnAcceptedPushToTheSameRepositoryAtTheSameTime() throws Exception {
        // many rounds, as the interleaving of the two pushes differs each time
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            ObjectId parent = null;
            for (int round = 0; round < 15; round++) {
                ObjectId good = commit(parent, Entry.file("a.txt", "round " + round), Entry.file("b.txt", "second " + round));
                ObjectId bad = commit(null, new Entry("link", FileMode.SYMLINK, ("target" + round).getBytes(StandardCharsets.UTF_8)));
                byte[] goodRequest = pushOf(parent == null ? ObjectId.zeroId() : parent, good, DEFAULT_REF, parent == null ? Set.of() : Set.of(parent));
                byte[] badRequest = pushOf(ObjectId.zeroId(), bad, Constants.R_HEADS + "bad" + round, Set.of());
                lenient().when(localVCServletService.isBranchNameAllowedForRepository(any(), anyString())).thenReturn(LocalVCServletService.BranchingStatus.BRANCH_ALLOWED);
                CountDownLatch start = new CountDownLatch(1);
                Future<Outcome> goodPush = executor.submit(() -> {
                    start.await();
                    return receive(goodRequest);
                });
                Future<Outcome> badPush = executor.submit(() -> {
                    start.await();
                    return receive(badRequest);
                });
                start.countDown();

                assertThat(goodPush.get(60, TimeUnit.SECONDS).accepted()).as("round %d: the good push", round).isTrue();
                assertThat(badPush.get(60, TimeUnit.SECONDS).accepted()).as("round %d: the bad push", round).isFalse();
                assertThat(serverRef(DEFAULT_REF)).isEqualTo(good);
                assertThat(serverHas(bad)).isFalse();
                readEverythingReachableFrom(good);
                parent = good;
            }
        }
        finally {
            executor.shutdownNow();
        }
        assertNoPlaceOfAPush();
        assertNoLeftoverFilesInTheObjectDirectory();
    }

    @Test
    void severalAcceptedPushesToDifferentBranchesAtTheSameTimeAreAllStored() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<ObjectId> commits = new ArrayList<>();
            List<Future<Outcome>> pushes = new ArrayList<>();
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < 4; i++) {
                ObjectId commit = commit(null, Entry.file("file.txt", "branch " + i));
                commits.add(commit);
                byte[] request = pushOf(ObjectId.zeroId(), commit, Constants.R_HEADS + "branch" + i, Set.of());
                pushes.add(executor.submit(() -> {
                    start.await();
                    return receive(request);
                }));
            }
            start.countDown();
            for (int i = 0; i < 4; i++) {
                assertThat(pushes.get(i).get(60, TimeUnit.SECONDS).accepted()).isTrue();
            }
            for (int i = 0; i < 4; i++) {
                assertThat(serverRef(Constants.R_HEADS + "branch" + i)).isEqualTo(commits.get(i));
                readEverythingReachableFrom(commits.get(i));
            }
        }
        finally {
            executor.shutdownNow();
        }
        assertNoPlaceOfAPush();
    }

    @Test
    void twoPushesWithTheSameFileToDifferentBranchesAtTheSameTimeAreBothAccepted() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 15; round++) {
                // both commits hold the same file, so both pushes carry the same blob
                ObjectId first = commit(null, Entry.file("a-shared.txt", "shared content of round " + round), Entry.file("b-first.txt", "first " + round));
                ObjectId second = commit(null, Entry.file("a-shared.txt", "shared content of round " + round), Entry.file("b-second.txt", "second " + round));
                byte[] firstRequest = pushOf(ObjectId.zeroId(), first, Constants.R_HEADS + "first" + round, Set.of());
                byte[] secondRequest = pushOf(ObjectId.zeroId(), second, Constants.R_HEADS + "second" + round, Set.of());
                CountDownLatch start = new CountDownLatch(1);
                Future<Outcome> firstPush = executor.submit(() -> {
                    start.await();
                    return receive(firstRequest);
                });
                Future<Outcome> secondPush = executor.submit(() -> {
                    start.await();
                    return receive(secondRequest);
                });
                start.countDown();

                assertThat(firstPush.get(60, TimeUnit.SECONDS).accepted()).as("round %d: first push", round).isTrue();
                assertThat(secondPush.get(60, TimeUnit.SECONDS).accepted()).as("round %d: second push", round).isTrue();
                readEverythingReachableFrom(first);
                readEverythingReachableFrom(second);
            }
        }
        finally {
            executor.shutdownNow();
        }
        assertNoPlaceOfAPush();
    }

    @Test
    void aPushWhoseOldIdDoesNotMatchTheReferenceStoresNothing() throws Exception {
        ObjectId first = commit(null, Entry.file("Main.java", "class Main {}"));
        assertAccepted(receive(pushOf(ObjectId.zeroId(), first, DEFAULT_REF, Set.of())), first, DEFAULT_REF);
        ObjectId second = commit(first, Entry.file("Main.java", "class Main { }"), Entry.file("New.java", "class New {}"));
        // the client believes that the branch is at a commit that it is not at, so JGit refuses the update itself
        ObjectId wrongOld = commit(null, Entry.file("Other.java", "class Other {}"));
        Set<String> before = serverObjectFiles();

        Outcome outcome = receive(pushOf(wrongOld, second, DEFAULT_REF, Set.of(first)));

        assertThat(outcome.accepted()).isFalse();
        assertThat(serverHas(second)).as("the objects of a push that JGit refused are not stored").isFalse();
        assertUntouched(before, first, DEFAULT_REF);
    }

    // ------------------------------------------------------------------------------------------------------------------------------------------------------------
    // Repositories without a directory of objects
    // ------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void aRepositoryThatKeepsItsObjectsElsewhereIsReceivedIntoDirectly() throws Exception {
        try (InMemoryRepository inMemory = new InMemoryRepository(new DfsRepositoryDescription("memory"))) {
            ReceivePack receivePack = QuarantinedReceivePack.create(inMemory, hook::examineBeforeObjects);

            assertThat(receivePack).as("such a repository has no place to quarantine").isNotInstanceOf(QuarantinedReceivePack.class);
        }
    }

    @Test
    void aReceivePackThatIsNeverUsedLeavesNoPlaceOfAPush() throws Exception {
        // as when a filter refuses the request after the receive pack was created
        try (Repository server = openServer()) {
            QuarantinedReceivePack.create(server, hook::examineBeforeObjects);
        }

        assertNoPlaceOfAPush();
    }

    @Test
    void theQuarantinedRepositoryIsAFileRepositoryThatSharesTheReferencesOfTheRealOne() throws Exception {
        ObjectId commit = commit(null, Entry.file("Main.java", "class Main {}"));
        assertAccepted(receive(pushOf(ObjectId.zeroId(), commit, DEFAULT_REF, Set.of())), commit, DEFAULT_REF);

        try (Repository server = openServer()) {
            ReceivePack receivePack = QuarantinedReceivePack.create(server, hook::examineBeforeObjects);

            assertThat(receivePack.getRepository()).isInstanceOf(FileRepository.class);
            assertThat(receivePack.getRepository().exactRef(DEFAULT_REF).getObjectId()).as("the references are those of the real repository").isEqualTo(commit);
            assertThat(receivePack.getRepository().getObjectDatabase().has(commit)).as("the existing objects can be read").isTrue();
            // the place was created for this receive pack, and it is removed with it
            receivePack.receive(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), new ByteArrayOutputStream());
        }
        assertNoPlaceOfAPush();
    }
}
