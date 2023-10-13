package com.matyrobbrt.javadoctor.test;

import org.junit.jupiter.api.Test;

import java.io.IOException;

public class RecordDocTests extends BaseDocsTest {
    @Test
    void testRecord() throws IOException {
        file("hello/RecordBolb.java")
                .getClassByName("RecordBolb")
                .assertClassDocMatches(
                        "This is a record.",
                        "@param hiButRenamed you didn't know?!",
                        "@param yes          yes it is",
                        "@param <T> the type of bolbing"
                );
    }

    @Test
    void testRecord2() throws IOException {
        file("stab/Stabby.java")
                .getClassByName("Stabby")
                .getInner("BetterStabbingState")
                .getInner("StabProvenance")
                .assertClassDocMatches(
                        "Stab provenance.",
                        "",
                        "<p>\"Why make this a record,\" you may ask, \"if you're restricting who can make it anyway with that {@linkplain",
                        "stab.Stabby.BetterStabbingState.StabProvenance.Hidden} nonsense?\" Great question! The reasoning for this is self-evident, and left as an exercise for the",
                        "reader.",
                        "@param state  the state of :stab:iness",
                        "@param stabs  the stabs at the time of creation",
                        "@param hidden a hidden thing, so y'all can't make your own provenances"
                );
    }
}
