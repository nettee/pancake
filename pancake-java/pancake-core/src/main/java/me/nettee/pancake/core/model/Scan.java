package me.nettee.pancake.core.model;

import java.util.Optional;

public interface Scan<E> {

    Optional<E> next();

    void close();

}
