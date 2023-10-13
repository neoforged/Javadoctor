package com.matyrobbrt.javadoctor.test;

import org.junit.jupiter.api.Test;

public class SimpleDocTests extends BaseDocsTest {
    @Test
    void testSimple() throws Exception {
        file("bolb/Hello.java")
                .getClassByName("Hello")
                .assertClassDocMatches(
                        "This is a bolb.",
                        "@see bolb.Hello.InnerPeace",
                        "@see stab.Stabby"
                )
                .assertDocOfMethodMatches("check",
                        "If ten or more {@linkplain stab.Stabby stabs} have been registered, we invoke {@linkplain java.lang.System#exit(int)} death by JVM}.",
                        "Do not dare to call {@link java.util.concurrent.atomic.AtomicInteger#getAndAccumulate(int, java.util.function.IntBinaryOperator)}.")
                .assertDocOfMethodMatches("getInstance",
                        "You better be the right caller, else we're gonna shut this whole operation down.",
                        "@return bolb.")

                .getInner("InnerPeace")
                .assertDocOfMethodMatches("check",
                        "Checks if {@link bolb.Hello.InnerPeace#CHECK} is {@code true}. If true, stab check timeF.")
                .parent()

                .getInner("LockChanger")
                .assertClassDocMatches("{@linkplain stab.Stabby#locksChange() Changes locks}. 'nuff said.")
                .assertDocOfMethodMatches("tryChange",
                        "Beware to those who do not hold within their hearts the {@link bolb.Hello.InnerPeace inner peace} required to",
                        "resolve themselves to carry out this sentence.")
                .assertDocOfFieldMatches("PEACE",
                        "\"Anything is possible when you have inner peace\" - some turtle");
    }

    @Test
    void testTagOrder() throws Exception {
        file("hello/TagOrdering.java")
                .getClassByName("TagOrdering")
                .assertClassDocMatches(
                        "@author doctor",
                        "@param <T> a parameter",
                        "@see java.util.concurrent.atomic.AtomicInteger for more information",
                        "@since 1.0",
                        "@customtag a custom tag"
                );
    }
}
