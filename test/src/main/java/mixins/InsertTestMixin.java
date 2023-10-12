package mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(InsertTest.class)
abstract class InsertTestMixin {
    /**
     * U're funny.
     */
    @Deprecated
    @Shadow private String aThingYouShallDo;

    /**
     * Some funny thing.
     */
    @Shadow
    abstract void weirdThingy();
}

@Mixin(InsertTest.Inner1.Inner2.class)
abstract class Inner2Mixin {
    /**
     * Soo... this returns stuff from the {@code integer} of type {@link AtomicInteger}.
     * @param integer the param with very much text that goes on and on for a very long time because the goal is to hopefully trick it into forcing a
     *                multiline tag
     * @param replacement and now we're going to see how it handles multiple parameters
     * @return the returned magic thingy
     */
    @Shadow
    abstract int weirdNr2(AtomicInteger integer, int replacement);
}
