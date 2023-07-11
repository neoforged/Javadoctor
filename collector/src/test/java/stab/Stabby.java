package stab;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import hello.Bolb;


/**
 * Stab registry.
 *
 * @see Bolb checker of stabs
 * @see #INSTANCE
 */
public class Stabby {
    /**
     * {@summary Chosen by fair dice roll. Guaranteed to be random.}
     */
    public static final long CONSTANT = 4L;

    /**
     * The stab instance. Singleton, obviously, because stabs are universal.
     */
    public static final Stabby INSTANCE = new Stabby();

    /**
     * Self-explanatory by reading the code. Defaults to {@link #CONSTANT}, which is {@value CONSTANT}.
     */
    private static int storage = (int) CONSTANT;
    /**
     * THE AYES HAVE IT, THE AYES HAVE IT. UNLOCK!
     */
    private static volatile ReadWriteLock LOCK = new ReentrantReadWriteLock();

    /**
     * What?
     */
    private Stabby() {
    }

    /**
     * Reads the {@link Stabby stab} counter.
     *
     * @return the amount of stabs
     * @see #write(int) writing counterpart
     */
    public int read() {
        try {
            LOCK.readLock().lock();
            return storage;
        } finally {
            LOCK.readLock().unlock();
        }
    }

    /**
     * Writes the {@linkplain #storage stab counter}.
     *
     * @param value stabb.
     * @see #read() well-educated (reading) counterpart
     */
    private void write(int value) {
        try {
            LOCK.writeLock().lock();
            storage = value;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Registers a new stabb for the universal collection.
     */
    public void increment() {
        write(read() + 1);
    }

    /**
     * We're changing the locks, bois.
     */
    public static void changeLocks() {
        LOCK = new ReentrantReadWriteLock();
    }

    /**
     * Different states of :stab:iness.
     *
     * @see StabProvenance provenance
     */
    public enum StabState {
        /**
         * The implement is readied.
         *
         * @see #READY
         */
        READY,
        /**
         * The implement is aimed at the target.
         *
         * @see #STABBED
         */
        AIMED,
        /**
         * STAB AT WILL, STAB AT WILL.
         */
        STABBED {
            /**
             * {@inheritDoc} This also increments the stab registry. Why? Yes.
             */
            @Override
            public StabProvenance provenance() {
                Stabby.INSTANCE.increment();
                return super.provenance();
            }
        };

        /**
         * Stab provenance.
         *
         * <p>"Why make this a record," you may ask, "if you're restricting who can make it anyway with that {@linkplain
         * Hidden} nonsense?" Great question! The reasoning for this is self-evident, and left as an exercise for the
         * reader.
         *
         * @param state  the state of :stab:iness
         * @param stabs  the stabs at the time of creation
         * @param hidden a hidden thing, so y'all can't make your own provenances
         */
        public record StabProvenance(StabState state, int stabs, Hidden hidden) {
            /**
             * :kekw:
             *
             * @param state  state
             * @param stabs  stabs
             * @param hidden shush
             */
            public StabProvenance {
                if (hidden != Hidden.INSTANCE) throw new Error("No. :kekw:");
            }

            /**
             * Hidden again! Stabs are recorded {@linkplain #read() from the registry}.
             *
             * @param state the stab state
             */
            private StabProvenance(StabState state) {
                this(state, Stabby.INSTANCE.read(), Hidden.INSTANCE);
            }

            /**
             * This literally exists because why not.
             */
            private static final class Hidden {
                /**
                 * Store an instance for comparison, so nobody can get around the unreferenceble type by passing a {@code null}.
                 */
                final static Hidden INSTANCE = new Hidden();
            }
        }

        /**
         * Record some provenance. Mmmmm, provenance.
         *
         * @return provenance.
         */
        public StabProvenance provenance() {
            return new StabProvenance(this);
        }
    }
}
