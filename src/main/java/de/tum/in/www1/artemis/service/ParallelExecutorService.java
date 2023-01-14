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

    public <T, R> CompletableFuture<R>[] runForAll(Collection<T> collection, Function<T, R> consumer) {
        ExecutorService threadPool = Executors.newFixedThreadPool(THREADS);

        CompletableFuture<R>[] futures = collection.stream().map(element -> CompletableFuture.supplyAsync(() -> consumer.apply(element), threadPool))
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
