package cp2022.solution;

import cp2022.base.Workplace;

import java.util.concurrent.Semaphore;

public class OrderlyWorkplace extends Workplace {
    // awaitingUse: State = Done && other[enter(this)]
    private final Semaphore ownership = new Semaphore(1, true);
    // state
    private WorkplaceState state = WorkplaceState.Empty;
    private long userId = 0;
    private final Workplace internalWorkplace;
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

    public boolean isOccupied() {
        return state != WorkplaceState.Empty;
    }

    public void occupy(Semaphore mutex) throws InterruptedException {
        if (isOccupied()) {
            mutex.release();
        }
        ownership.acquire();
        userId = Thread.currentThread().getId();
        state = WorkplaceState.Before;
    }

    @Override
    public void use() {
        internalWorkplace.use();
        state = WorkplaceState.Done;
    }

    /* true is state->Empty */
    public void leave() {
        userId = 0;
        state = WorkplaceState.Empty;
        ownership.release();
    }

    public long getUserId() {
        return userId;
    }
}
