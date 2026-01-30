package dev.openize.drako;


public interface Enumerable<T> {
    Enumerator<T> enumerator();
}
