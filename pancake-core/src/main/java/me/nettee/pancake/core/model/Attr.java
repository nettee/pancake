package me.nettee.pancake.core.model;

public abstract class Attr implements Comparable<Attr> {

    public abstract byte[] getData();

    public abstract int getLength();
}
