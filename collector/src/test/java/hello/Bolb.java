package hello;

import stab.Stabby;

/**
 * This is a bolb.
 *
 * @see Stabby
 * @see InnerPeace
 */
public class Bolb {
    /**
     * If ten or more {@linkplain Stabby stabs} have been registered, we invoke {@linkplain System#exit(int)} death by JVM}.
     */
    public void check() {
        if (Stabby.INSTANCE.read() > 10) {
            System.exit(-1);
        }
    }

    /**
     * You better be the right caller, else we're gonna shut this whole operation down.
     *
     * @return bolb.
     */
    public static Bolb getInstance() {
        if (StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass() != Stabby.class) {
            throw new AssertionError("Who do you think you're looking at, eh?!");
        }
        return new Bolb();
    }

    /**
     * Subclass which practices inner peace.
     *
     * @see Bolb
     */
    public class InnerPeace extends Bolb {
        /**
         * If this is true, we do a {@link Bolb#check() stab check}.
         */
        static boolean CHECK = false;

        /**
         * Checks if {@link #CHECK} is {@code true}. If true, stab check timeF.
         */
        @Override
        public void check() {
            if (CHECK) {
                Bolb.this.check();
            }
        }
    }

    /**
     * {@linkplain Stabby#changeLocks() Changes locks}. 'nuff said.
     */
    public static class LockChanger {
        /**
         * "Anything is possible when you have inner peace" - some turtle
         */
        static final InnerPeace PEACE;

        static {
            final Bolb bolb = new Bolb();
            PEACE = bolb.new InnerPeace();
        }

        /**
         * Beware to those who do not hold within their hearts the {@link InnerPeace inner peace} required to
         * resolve themselves to carry out this sentence.
         */
        public static void tryChange() {
            PEACE.check();
            Stabby.changeLocks();
        }
    }
}