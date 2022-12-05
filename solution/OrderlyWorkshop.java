package cp2022.solution;

import cp2022.base.Workplace;
import cp2022.base.WorkplaceId;
import cp2022.base.Workshop;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public class OrderlyWorkshop implements Workshop {
    private static class WorkplaceMap {
        private final ConcurrentHashMap<WorkplaceId, OrderlyWorkplace> workplaces;
        private final ConcurrentHashMap<Long, WorkplaceId> users;

        public WorkplaceMap(Collection<Workplace> workplaces, SemaphoreQueue queue) {
            this.workplaces = new ConcurrentHashMap<>(workplaces.size());

            for (var workplace : workplaces) {
                this.workplaces.put(workplace.getId(), new OrderlyWorkplace(workplace));
            }

            this.users = new ConcurrentHashMap<>();
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
                workplace.log(builder);
            }
        }
    }

    private static class Requests {
        public static class Edge {
            private final WorkplaceId from;
            private final WorkplaceId to;

            public Edge(WorkplaceId f, WorkplaceId t) {
                from = f;
                to = t;
            }

            public WorkplaceId getFrom() {
                return from;
            }

            public WorkplaceId getTo() {
                return to;
            }
        }

        private final ConcurrentHashMap<WorkplaceId, WorkplaceId> matrix;

        public Requests(long n) {
            matrix = new ConcurrentHashMap<>((int) n);
        }

        public Edge add(WorkplaceId from, WorkplaceId to) {
            matrix.put(from, to);

            return new Edge(from, to);
        }

        public void remove(Edge e) {
            matrix.remove(e.getFrom());
            // matrix.remove(e.getFrom(), e.getTo());
        }

        // Cycle := getCycle(e)
        public Set<WorkplaceId> getCycle(Edge e) {
            var seenNodes = new HashSet<WorkplaceId>();
            var currentNode = e.getTo();

            while (matrix.containsKey(currentNode)) {
                if (seenNodes.contains(currentNode)) {
                    break;
                }

                seenNodes.add(currentNode);
                currentNode = matrix.get(currentNode);
            }

            return seenNodes.contains(e.getFrom()) ? seenNodes : null;
        }
    }

    private final SemaphoreQueue queue;
    private final Requests requests;
    private final WorkplaceMap workplaces;
    private final Semaphore mutex = new Semaphore(1, true);

    private long currentTime = 0;
    private final long n;

    public OrderlyWorkshop(Collection<Workplace> workplaces) {
        queue = new SemaphoreQueue(workplaces.size() * 2);
        this.workplaces = new WorkplaceMap(workplaces, queue);
        n = workplaces.size();
        requests = new Requests(n);
    }

    private void logState(String label) {
        var builder = new StringBuilder();
        builder.append("-----------------------------\n")
                .append(String.format("uid: %d, t: %d, mutex: %s, %s\n", Identification.uid(), currentTime, mutex.availablePermits(), label));
        workplaces.logState(builder);
        builder.append("-----------------------------\n");
        System.out.println(builder);
    }


    private boolean shouldWait(long myTime) {
        return !queue.isEmpty() && Math.abs(myTime - queue.minTime()) >= n - 1;
    }

    @Override
    public Workplace enter(WorkplaceId wid) {
        try {
            mutex.acquire();
            var workplace = workplaces.get(wid);
            var time = currentTime++;

            if (shouldWait(time)) {
                // logState(String.format("enter[%s->%s] queue.await(%s)", Identification.uid(), wid, time));
                mutex.release();
                queue.await(time);
            }

            if (workplace.isAwaited() || !workplace.isEmpty()) {
                // logState(String.format("enter[%s->%s] workplace.await(%s)", Identification.uid(), wid, time));
                mutex.release();
                workplace.await();
            }

            workplace.occupy();
            workplaces.updateMapping(workplace);
            // logState(String.format("enter[%s->%s] workplace occupied", Identification.uid(), wid));
            mutex.release();

            return workplace;
        } catch (InterruptedException e) {
            ErrorHandling.panic();
        }

        return null;
    }


    @Override
    public Workplace switchTo(WorkplaceId wid) {
        try {
            logState(String.format("switch_to[%s->%s]->getting mutex", Identification.uid(), wid));
            mutex.acquire();
            logState(String.format("switch_to[%s->%s]->got mutex", Identification.uid(), wid));
            var workplace = workplaces.get(wid);
            var uid = Identification.uid();
            var current = workplaces.getThroughUser(uid);
            assert current != null;
            var time = currentTime++;

            if (shouldWait(time)) {
                logState(String.format("switch_to[%s->%s]->queue.await(%s)", Identification.uid(), wid, time));
                mutex.release();
                queue.await(time);
            }
            if (!workplace.isAwaited() && workplace.isEmpty()) {
                logState(String.format("switch_to[%s->%s]->free->occupying", Identification.uid(), wid));

                workplace.occupy();
                current.leave();
                workplaces.updateMapping(workplace);
                workplaces.updateMapping(current);

                if (current.isAwaited()) {
                    logState(String.format("switch_to[%s->%s]->free->signal", Identification.uid(), wid));
                    current.signal();
                } else {
                    mutex.release();
                }

                return workplace;
            }

            var e = requests.add(current.getId(), wid);
            var cycle = requests.getCycle(e);
            CountDownLatch doneLatch = null;

            if (cycle != null) {
                StringBuilder b = new StringBuilder();
                b.append(String.format("switch_to[%s->%s]->occupied->cycle", Identification.uid(), wid))
                        .append(Arrays.toString(cycle.toArray()));
                logState(b.toString());

                doneLatch = new CountDownLatch(cycle.size());
                var latch = new CountDownLatch(cycle.size());
                for (var p : cycle) {
                    workplaces.get(p).giveLatch(latch, doneLatch);
                }
            } else {
                logState(String.format("switch_to[%s->%s]->occupied->no cycle", Identification.uid(), wid));
                mutex.release();
                workplace.await();
            }

            var cascades = current.hasLatch();

            logState(String.format("switch_to[%s->%s]->occupied->occupying[cas:%s]", Identification.uid(), wid, cascades));
            workplace.occupy();
            workplaces.updateMapping(workplace);
            if (!cascades) {
                current.leave();
                workplaces.updateMapping(current);
            }
            requests.remove(e);
            if (current.isAwaited()) {
                logState(String.format("switch_to[%s->%s]->occupied->signaling", Identification.uid(), wid));
                current.signal();
            } else if (!cascades) {
                mutex.release();
            }

            if (cascades) {
                logState(String.format("switch_to[%s->%s]->occupied->cyclic->latching", Identification.uid(), wid));
                current.bumpDoneLatch();
                current.awaitLatch();
                if (doneLatch != null) {
                    current.awaitDoneLatch();
                    mutex.release();
                }
            }

            return workplace;
        } catch (InterruptedException e) {
            ErrorHandling.panic();
        }

        return null;
    }

    @Override
    public void leave() {
        try {
            mutex.acquire();
            logState(String.format("leave[%s]->workplace", Identification.uid()));
            var workplace = workplaces.getThroughUser(Identification.uid());
            assert workplace != null;

            workplace.leave();
            workplaces.updateMapping(workplace);
            if (workplace.isAwaited()) {
                logState(String.format("leave[%s:%s]->workplace", Identification.uid(), workplace.getId()));
                workplace.signal();
            } else if (!queue.isEmpty()) {
                logState(String.format("leave[%s:%s]->queue", Identification.uid(), workplace.getId()));
                queue.signal();
            } else {
                logState(String.format("leave[%s:%s]->mutex.V", Identification.uid(), workplace.getId()));
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
        e := requests.add(current.id(), wid)
        Cycle := getCycle(e)
        if (Cycle):
            L := countdownLatch(Cycle.size())
            for (p in Cycle):
                p.giveLatch(L)
                { (latch) => this.latch = latch; }
        else:
            mutex.V()
            current.delay.P() { #awaited++; P(); }
        workplace.occupy()
        current.leave()
        requests.remove(e)
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
