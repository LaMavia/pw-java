package cp2022.solution;

import cp2022.base.Workplace;
import cp2022.base.WorkplaceId;
import cp2022.base.Workshop;

import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

public class OrderlyWorkshop implements Workshop {
    private static class WorkplaceMap {
        private final HashMap<WorkplaceId, OrderlyWorkplace> workplaces;
        private final HashMap<Long, WorkplaceId> users;

        public WorkplaceMap(Collection<Workplace> workplaces, SemaphoreQueue queue) {
            this.workplaces = new HashMap<>(workplaces.size());

            for (var workplace : workplaces) {
                this.workplaces.put(workplace.getId(), new OrderlyWorkplace(workplace));
            }

            this.users = new HashMap<>();
        }

        public synchronized OrderlyWorkplace getThroughUser(long uid) {
            var wid = users.get(uid);
            if (wid == null) {
                return null;
            }

            var workplace = workplaces.get(wid);
            if (workplace.getUserId() != uid) {
                users.remove(uid);
                return null;
            }

            return workplace;
        }

        public synchronized OrderlyWorkplace get(WorkplaceId wid) {
            return workplaces.get(wid);
        }

        public synchronized void updateMapping(OrderlyWorkplace workplace) {
            if (workplace.getState() != OrderlyWorkplace.WorkplaceState.Empty) {
                users.put(workplace.getUserId(), workplace.getId());
            }
        }

        public void logState(StringBuilder builder) {
            for (var entry : workplaces.entrySet()) {
                var workplace = entry.getValue();
                builder.append(
                        String.format("%s (%s) -> %s (awaiting: %d)\n",
                                entry.getKey(), workplace.getState(), workplace.getUserId(), workplace.getAwaiting())
                );
            }
        }
    }

    private final SemaphoreQueue queue;
    private final WorkplaceMap workplaces;
    private final Semaphore mutex = new Semaphore(1, true);

    private long currentTime = 0;
    private final long n;

    public OrderlyWorkshop(Collection<Workplace> workplaces) {
        queue = new SemaphoreQueue(workplaces.size() * 2);
        this.workplaces = new WorkplaceMap(workplaces, queue);
        n = workplaces.size();
    }

    private boolean leaveCurrentWorkplace(boolean release) {
        var currentWorkplace = workplaces.getThroughUser(Identification.uid());
        if (currentWorkplace == null) {
            ErrorHandling.panic();
        }

        logState("leaveCurrentWorkplace->leave");
        var isAwaited = currentWorkplace.getAwaiting() > 0;
        currentWorkplace.leave();
        workplaces.updateMapping(currentWorkplace);

        if (!queue.isEmpty()) {
            logState("leaveCurrentWorkplace->queue");
            queue.signal();
        } else if (release) {
            logState("leaveCurrentWorkplace->release");
            mutex.release();
        }

        return isAwaited;
    }

    private void logState(String label) {
        var builder = new StringBuilder();
        builder.append("-----------------------------\n")
                .append(String.format("uid: %d, t: %d, %s\n", Identification.uid(), currentTime, label));
        workplaces.logState(builder);
        builder.append("-----------------------------\n");
        System.out.println(builder);
    }


    private boolean shouldWait(long myTime) {
        if (queue.isEmpty()) return false;
        return Math.abs(myTime - queue.minTime()) >= n - 1;
    }

    @Override
    public Workplace enter(WorkplaceId wid) {
        try {
            mutex.acquire();
            var workplace = workplaces.get(wid);
            var time = currentTime++;

            if (!queue.isEmpty() && shouldWait(time)) {
                logState(String.format("enter[%s->%s] queue.await(%s)", Identification.uid(), wid, time));
                queue.await(time);
            }

            if (workplace.isAwaited() || !workplace.isEmpty()) {
                logState(String.format("enter[%s->%s] workplace.await(%s)", Identification.uid(), wid, time));
                mutex.release();
                workplace.await();
            }

            workplace.occupy();
            workplaces.updateMapping(workplace);
            logState(String.format("enter[%s->%s] workplace occupied", Identification.uid(), wid));
            mutex.release();

            return workplace;
        } catch (InterruptedException e) {
            ErrorHandling.panic();
        }

        return null;
    }


    @Override
    public Workplace switchTo(WorkplaceId wid) {
//        try {
//
//        } catch (InterruptedException e) {
//            ErrorHandling.panic();
//        }

        return null;
    }

    @Override
    public void leave() {
        try {
            mutex.acquire();
            var workplace = workplaces.getThroughUser(Identification.uid());
            assert workplace != null;

            workplace.leave();
            workplaces.updateMapping(workplace);
            if (workplace.isAwaited()) {
                workplace.signal();
            } else if (!queue.isEmpty()) {
                queue.signal();
            } else {
                mutex.release();
            }
        } catch (InterruptedException e) {
            ErrorHandling.panic();
        }
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


leave():
    mutex.P()
    workplace := workplaces.getThroughUser(uid())
    workplace.leave()
    if (workplace.isAwaited()):
        workplace.delay.V()
    else if (!queue.isEmpty()):
        queue.signal()
    else:
        mutex.V()

enter():
    mutex.P()
    workplace := workplaces.get(wid)
    time := currentTime++

    if (!queue.isEmpty() && |time - queue.minTime()| >= n): queue.await(time)
    if (workplace.isAwaited() || workplace.isEmpty()): {isEmpty(): userId == 0 }
        mutex.V()
        workplace.delay.P()
    workplace.occupy()
    mutex.V()
    return workplace

* */
