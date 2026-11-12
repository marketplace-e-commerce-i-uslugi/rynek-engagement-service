package pl.rynek.engagement.api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.engagement.api.dto.CursorPage;

/** Paginacja kursorowa (§1): kursor to zakodowane przesunięcie, {@code nextCursor} jest pusty na ostatniej stronie. */
final class Cursors {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;

    interface Fetch<E> {
        List<E> fetch(int offset, int limit);
    }

    private Cursors() {
    }

    static <E, T> CursorPage<T> page(String cursor, Integer limit, Fetch<E> fetch, Function<E, T> view) {
        int offset = offset(cursor);
        int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        List<E> rows = fetch.fetch(offset, size + 1);
        boolean more = rows.size() > size;
        String next = more ? Base64.getUrlEncoder().withoutPadding().encodeToString(("o:" + (offset + size)).getBytes(StandardCharsets.UTF_8)) : null;
        return new CursorPage<>((more ? rows.subList(0, size) : rows).stream().map(view).toList(), next);
    }

    private static int offset(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int offset = decoded.startsWith("o:") ? Integer.parseInt(decoded.substring(2)) : -1;
            if (offset < 0) {
                throw new IllegalArgumentException();
            }
            return offset;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowy kursor.");
        }
    }
}
