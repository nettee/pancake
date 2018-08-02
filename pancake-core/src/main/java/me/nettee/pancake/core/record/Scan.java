package me.nettee.pancake.core.record;

import java.util.Optional;

public interface Scan<E> {

    Optional<E> next();

    void close();

}
