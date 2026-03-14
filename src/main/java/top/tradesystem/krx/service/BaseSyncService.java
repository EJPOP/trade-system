package top.tradesystem.krx.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.tradesystem.krx.common.DateConstants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * Base class for KRX sync services, providing shared helpers for
 * date range generation, validation, market normalization, and
 * reactive DB calls on the bounded-elastic scheduler.
 */
public abstract class BaseSyncService {

    protected static final DateTimeFormatter BAS_DD_FMT = DateConstants.BAS_DD_FMT;

    /**
     * Generate a Flux of date strings (yyyyMMdd) from start to end inclusive.
     */
    protected Flux<String> generateDateRange(LocalDate start, LocalDate end) {
        return Flux.create(sink -> {
            LocalDate d = start;
            while (!d.isAfter(end)) {
                sink.next(d.format(BAS_DD_FMT));
                d = d.plusDays(1);
            }
            sink.complete();
        });
    }

    /**
     * Validate that end >= start and delayMs >= 0.
     * Returns Mono.empty() on success or Mono.error() on failure.
     */
    protected Mono<Void> validateRange(LocalDate start, LocalDate end, long delayMs) {
        if (end.isBefore(start)) {
            return Mono.error(new IllegalArgumentException("to must be >= from"));
        }
        if (delayMs < 0) {
            return Mono.error(new IllegalArgumentException("delayMs must be >= 0"));
        }
        return Mono.empty();
    }

    /**
     * Normalize a market string: if null/blank, use the provided default; otherwise uppercase.
     */
    protected String normalizeMarket(String market, String defaultMarket) {
        return (market == null || market.isBlank()) ? defaultMarket : market.toUpperCase(Locale.ROOT);
    }

    /**
     * Wrap a blocking callable in Mono on the bounded-elastic scheduler.
     */
    protected <T> Mono<T> dbCall(Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}
