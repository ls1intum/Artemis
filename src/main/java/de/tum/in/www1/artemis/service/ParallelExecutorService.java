package de.tum.in.www1.artemis.service;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.stereotype.Service;

/**
 * Service for executing Tasks with multiple threads
 */
@Service
public class ParallelExecutorService {

    private static final int THREADS = 10;

    /**
     * Executes the code in the given function for evey object in parallel.
     * Returns an array of CompletableFutures allowing to retrieve the functions return value
     *
     * @param collection Objects for which the task should be executed in parallel
     * @param function   Code to execute
     * @param <T>        input type
     * @param <R>        function output type
     * @return the created futures
     */
    public <T, R> CompletableFuture<R>[] runForAll(Collection<T> collection, Function<T, R> function) {
        ExecutorService threadPool = Executors.newFixedThreadPool(THREADS);

        CompletableFuture<R>[] futures = collection.stream().map(element -> CompletableFuture.supplyAsync(() -> function.apply(element), threadPool))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).thenApply(ignore -> {
            threadPool.shutdown();
            return null;
        });
        return futures;
    }

    public <T> CompletableFuture<Void>[] runForAll(Collection<T> collection, Consumer<T> consumer) {
        return runForAll(collection, t -> {
            consumer.accept(t);
            return null;
        });
    }

}
