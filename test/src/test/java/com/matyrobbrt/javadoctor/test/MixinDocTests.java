package com.matyrobbrt.javadoctor.test;

import org.junit.jupiter.api.Test;

import java.io.IOException;

public class MixinDocTests extends BaseDocsTest {
    @Test
    void testRootLevelMixin() throws IOException {
        file("mixins/InsertTest.java")
                .getClassByName("InsertTest")
                .assertDocOfFieldMatches("aThingYouShallDo",
                        "U're funny.")
                .assertDocOfMethodMatches("weirdThingy",
                        "Some funny thing.");
    }

    @Test
    void testInnerMixin() throws IOException {
        file("mixins/InsertTest.java")
                .getClassByName("InsertTest")
                .getInner("Inner1")
                .getInner("Inner2")
                .assertClassDocMatches(
                        "Some interesting mixin injected..."
                )
                .assertDocOfMethodMatches("weirdNr2",
                        "Soo... this returns stuff from the {@code integer} of type {@link java.util.concurrent.atomic.AtomicInteger}.",
                                "@param integer     the param with very much text that goes on and on for a very",
                                "                   long time because the goal is to hopefully trick it into",
                                "                   forcing a multiline tag",
                                "@param replacement and now we're going to see how it handles multiple parameters",
                                "@return the returned magic thingy");
    }
}
