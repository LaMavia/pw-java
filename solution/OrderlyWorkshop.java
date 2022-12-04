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
                this.workplaces.put(workplace.getId(), new OrderlyWorkplace(workplace, queue));
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

    public OrderlyWorkshop(Collection<Workplace> workplaces) {
        queue = new SemaphoreQueue(workplaces.size() * 2);
        this.workplaces = new WorkplaceMap(workplaces, queue);
    }

    private void leaveCurrentWorkplace(boolean release) {
        var currentWorkplace = workplaces.getThroughUser(uid());
        if (currentWorkplace == null) {
            ErrorHandling.panic();
        }

        logState("leaveCurrentWorkplace->leave");
        currentWorkplace.leave();
        workplaces.updateMapping(currentWorkplace);

        if (!queue.empty()) {
            logState("leaveCurrentWorkplace->queue");
            queue.signal();
        } else if (release) {
            logState("leaveCurrentWorkplace->release");
            mutex.release();
        }
    }

    private long uid() {
        return Thread.currentThread().getId();
    }

    private void logState(String label) {
        var builder = new StringBuilder();
        builder.append("-----------------------------\n")
                .append(String.format("uid: %d, t: %d, %s\n", uid(), currentTime, label));
        workplaces.logState(builder);
        builder.append("-----------------------------\n");
        System.out.println(builder);
    }


    @Override
    public Workplace enter(WorkplaceId wid) {
        try {
            mutex.acquire();
            var myTime = currentTime++;
            var workplace = workplaces.get(wid);

            if (workplace.isOccupied()) {
                logState(String.format("enter[%s]->occupied", wid));
                mutex.release();
                queue.await(myTime);
            }

            logState(String.format("enter[%s]->occupying", wid));
            workplace.occupy(mutex);
            workplaces.updateMapping(workplace);
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
            mutex.acquire();
            var myTime = currentTime++;
            var workplace = workplaces.get(wid);

            if (workplace.isOccupied()) {
                logState(String.format("switchTo[%s]->occupied", wid));
                mutex.release();
                queue.await(myTime);
            }

            logState(String.format("switchTo[%s]->leaving current", wid));
            leaveCurrentWorkplace(false);
            logState(String.format("switchTo[%s]->occupying", wid));
            workplace.occupy(mutex);
            workplaces.updateMapping(workplace);
            logState("switchTo->done");
            mutex.release();

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
            logState("leave");
            leaveCurrentWorkplace(true);
        } catch (InterruptedException e) {
            ErrorHandling.panic();
        }
    }
}

/*
 * q, workplaces
 *
 *
 * */
