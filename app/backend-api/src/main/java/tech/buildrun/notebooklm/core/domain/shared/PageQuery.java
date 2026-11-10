package tech.buildrun.notebooklm.core.domain.shared;

import java.util.List;

public record PageQuery(int page, int size, List<Order> orders) {

    public record Order(String property, boolean ascending) {
    }

    public PageQuery(int page, int size) {
        this(page, size, List.of());
    }
}
