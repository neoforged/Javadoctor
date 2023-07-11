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
     * @param integer the param
     * @return the returned magic thingy
     */
    @Shadow
    abstract int weirdNr2(AtomicInteger integer);
}
