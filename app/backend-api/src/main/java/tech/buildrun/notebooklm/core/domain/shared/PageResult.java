package tech.buildrun.notebooklm.core.domain.shared;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), page, size, totalElements, totalPages);
    }
}
