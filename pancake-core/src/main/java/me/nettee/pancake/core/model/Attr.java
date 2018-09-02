package me.nettee.pancake.core.model;

public abstract class Attr implements Comparable<Attr> {

    public abstract byte[] getData();

    public abstract int getLength();

    public static Attr fromBytes(AttrType attrType, byte[] data) {
        if (attrType.isInt()) {
            return IntAttr.fromBytes(data);
        } else if (attrType.isFloat()) {
            return FloatAttr.fromBytes(data);
        } else if (attrType.isString()) {
            return StringAttr.fromBytes(data);
        } else {
            throw new IllegalArgumentException();
        }
    }
}
