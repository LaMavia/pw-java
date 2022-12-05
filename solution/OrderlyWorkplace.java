package cp2022.solution;

import cp2022.base.Workplace;

import java.util.concurrent.Semaphore;

public class OrderlyWorkplace extends Workplace {
    private final Semaphore ownership = new Semaphore(1, true);
    public final Semaphore usability = new Semaphore(1, true);
    private int awaiting = 0;
    // state
    private WorkplaceState state = WorkplaceState.Empty;
    private long userId = 0;
    private final Workplace internalWorkplace;
    private final SemaphoreQueue queue;
    // private Semaphore mutex = new Semaphore(1, true);

    public enum WorkplaceState {
        Empty, Before, Done
    }

    public WorkplaceState getState() {
        return state;
    }

    public OrderlyWorkplace(Workplace workplace, SemaphoreQueue queue) {
        super(workplace.getId());
        internalWorkplace = workplace;
        this.queue = queue;
    }

    public boolean isOccupied() {
        if (userId == Thread.currentThread().getId()) {
            return false;
        }
        return state == WorkplaceState.Before;
        // return !(userId == Thread.currentThread().getId() || state == WorkplaceState.Empty);
    }

    private boolean isAwaited() {
        return awaiting > 0;
    }

    public int getAwaiting() {
        return awaiting;
    }

    public void log(StringBuilder builder) {
        builder.append(String.format("uid: %s, pid: %s, state: %s, awaiting: %s", userId, Thread.currentThread().getId(), state, awaiting));
    }

    public void occupy(Semaphore mutex) throws InterruptedException {
        var willAwait = state != WorkplaceState.Before;
        StringBuilder b = new StringBuilder();
        b.append(String.format("occupy[%s->%s]->will await = %s\n", Thread.currentThread().getId(), getId(), willAwait));
        log(b);
        System.out.println(b);

        if (willAwait) {
            awaiting++;
            mutex.release();
        }

        ownership.acquire();

        if (willAwait) {
            awaiting--;
        }

        userId = Thread.currentThread().getId();
        state = WorkplaceState.Before;
    }

    @Override
    public void use() {
        try {
            usability.acquire();
            internalWorkplace.use();
            state = WorkplaceState.Done;

            if (!isAwaited()) {
                queue.signal();
            }
        } catch (InterruptedException e) {
            ErrorHandling.panic();
        }
    }

    /* true is state->Empty */
    public void leave() {
        userId = 0;
        state = WorkplaceState.Empty;
        ownership.release();

        if (!isAwaited()) {
            queue.signal();
        }
    }

    public long getUserId() {
        return userId;
    }
}

/*
switch(wid):
    mutex.P()
    workplace := workplaces.get(wid)
    current := workplaces.getThroughUser(uid())
    time := currentTime++

    if (!queue.isEmpty() && |time - queue.minTime()| >= n): queue.await(time)
    if (!workplace.isAwaited() && workplace.isEmpty()): {isEmpty(): userId == 0 }
        workplace.occupy() { userId = uid() }
        current.leave() { userId = 0 }
        if (current.isAwaited()):
            current.delay.V() // someone's in
        else:
            mutex.V()
        return workplace
    else:
        e := requests.add(current.id(), wid)    // @todo
        if (isInACycle(e)):                     // @todo
            k, Cycle := getCycle(e)             // @todo
            L := countdownLatch(k)
            for (p in Cycle):
                p.giveLatch(L)
                { (latch) => this.latch = latch; }
        else:
            mutex.V()
            workplace.delay.P() { #awaited++; P(); }
        workplace.occupy()
        current.leave()
        if (current.isAwaited()):
            current.delay.V()
        latch.await()
        if (I'm last):
            mutex.V()

        return workplace





* */
