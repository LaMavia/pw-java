package cp2022.solution;

import cp2022.base.Workplace;
import cp2022.base.WorkplaceId;

import java.util.concurrent.Semaphore;

public class OrderlyWorkplace extends Workplace {
    // awaitingUse: State = Done && other[enter(this)]
    private boolean isAwaiting = false;
    private Semaphore awaitingDelay = new Semaphore(0); // fairness doesn't matter
    // state
    private WorkplaceState state = WorkplaceState.Empty;
    private long userId = 0;
    private final Workplace internalWorkplace;
    // private Semaphore mutex = new Semaphore(1, true);

    public static enum WorkplaceState {
        Empty, Before, Done
    }

    public WorkplaceState getState() {
        return state;
    }

    public OrderlyWorkplace(Workplace workplace) {
        super(workplace.getId());
        internalWorkplace = workplace;
    }

    public boolean isAwaited() {
        return isAwaiting;
    }

    public void await() throws InterruptedException {
        isAwaiting = true;
        awaitingDelay.acquire();
        isAwaiting = false;
    }

    public synchronized void occupy() {
        userId = Thread.currentThread().getId();
        state = WorkplaceState.Before;
    }

    @Override
    public synchronized void use() {
        internalWorkplace.use();
        state = WorkplaceState.Done;
    }

    /* true is state->Empty */
    public synchronized boolean leave() {
        userId = 0;
        state = WorkplaceState.Empty;

        return isAwaiting;
    }

    public void signal() {
        awaitingDelay.release();
    }

    public long getUserId() {
        return userId;
    }
}
