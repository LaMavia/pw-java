package cp2022.solution;

import cp2022.base.Workplace;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public class OrderlyWorkplace extends Workplace {
    private final Semaphore ownership = new Semaphore(1, true);
    private final Semaphore delay = new Semaphore(0, true);
    private final Semaphore mutex = new Semaphore(1, true);
    private CountDownLatch latch, doneLatch, useLatch;
    private int awaiting = 0;
    // state
    private WorkplaceState state = WorkplaceState.Empty;
    private long userId = 0;
    private final Workplace internalWorkplace;
    // private final SemaphoreQueue queue;
    // private Semaphore mutex = new Semaphore(1, true);

    public enum WorkplaceState {
        Empty, Before, Done
    }

    public WorkplaceState getState() {
        return state;
    }

    public OrderlyWorkplace(Workplace workplace) {
        super(workplace.getId());
        internalWorkplace = workplace;
    }

    public boolean isEmpty() {
        return state == WorkplaceState.Empty;
    }

    public boolean isAwaited() {
        return awaiting > 0;
    }

    public void await(Semaphore foreignMutex) throws InterruptedException {
        mutex.acquire();
        awaiting++;
        mutex.release();

        foreignMutex.release();
        delay.acquire();

        mutex.acquire();
        awaiting--;
        mutex.release();
    }

    public void signal() {
        delay.release();
    }

    public int getAwaiting() {
        return awaiting;
    }

    public void giveLatch(CountDownLatch l, CountDownLatch dl, CountDownLatch ul) throws InterruptedException {
        mutex.acquire();
        latch = l;
        doneLatch = dl;
        useLatch = ul;
        mutex.release();
    }

    public void awaitLatch() throws InterruptedException {
        latch.countDown();
        System.out.printf("awaitingLatch[%s]: casc: %s, done: %s\n", Identification.uid(), latch.getCount(), doneLatch.getCount());
        latch.await();
    }

    public boolean hasLatch() {
        return latch != null && latch.getCount() > 0;
    }

    public void awaitDoneLatch() throws InterruptedException {
        doneLatch.await();
    }

    public void bumpDoneLatch() throws InterruptedException {
        doneLatch.countDown();
    }

    public void log(StringBuilder builder) {
        builder.append(
                String.format("%s (%s) -> %s (awaiting: %d, delay: %s, mutex: %s)\n",
                        getId(), getState(), getUserId(), getAwaiting(), delay.availablePermits(), mutex.availablePermits() )
        );
    }

    public void occupy() throws InterruptedException {
        mutex.acquire();
        userId = Thread.currentThread().getId();
        state = WorkplaceState.Before;
        mutex.release();
    }

    @Override
    public void use() {
        try {
            mutex.acquire();
            if (useLatch != null) {
                useLatch.countDown();
                useLatch.await();
            }
//            ownership.acquire();
            internalWorkplace.use();
            state = WorkplaceState.Done;
            var b = new StringBuilder();
            log(b);
            System.out.println("use: " + b.toString());
            mutex.release();
        } catch (InterruptedException e) {
            ErrorHandling.panic();
        }
    }

    public void leave() {
        try {
            mutex.acquire();
            userId = 0;
            state = WorkplaceState.Empty;
//            ownership.release();
            mutex.release();
        } catch (InterruptedException e) {
            ErrorHandling.panic();
        }
    }

    public long getUserId() {
        return userId;
    }
}
