package com.openize.drako;


public interface Enumerable<T> {
    Enumerator<T> enumerator();
}
