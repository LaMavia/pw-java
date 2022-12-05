package cp2022.solution;

import cp2022.base.Workplace;

import java.util.concurrent.Semaphore;

public class OrderlyWorkplace extends Workplace {
    // private final Semaphore ownership = new Semaphore(1, true);
    private final Semaphore delay = new Semaphore(0, true);
    private final Semaphore mutex = new Semaphore(1, true);
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

    public void await() throws InterruptedException {
        mutex.acquire();
        awaiting++;
        mutex.release();

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

    public void log(StringBuilder builder) {
        builder.append(String.format("uid: %s, pid: %s, state: %s, awaiting: %s", userId, Thread.currentThread().getId(), state, awaiting));
    }

    public void occupy() throws InterruptedException {
        mutex.acquire();
        // ownership.acquire();
        userId = Thread.currentThread().getId();
        state = WorkplaceState.Before;
        mutex.release();
    }

    @Override
    public void use() {
        try {
            mutex.acquire();
            internalWorkplace.use();
            state = WorkplaceState.Done;
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
            // ownership.release();
            mutex.release();
        } catch (InterruptedException e) {
            ErrorHandling.panic();
        }
    }

    public long getUserId() {
        return userId;
    }
}
