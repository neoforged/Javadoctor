package mixins;

import java.util.concurrent.atomic.AtomicInteger;

class InsertTest {
    public final String aThingYouShallDo = "abcd";
    void weirdThingy() {

    }

    static class Inner1 {
        static class Inner2 {
            int weirdNr2(AtomicInteger integer) {
                return integer.get();
            }
        }
    }
}
