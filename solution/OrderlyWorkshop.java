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

        public WorkplaceMap(Collection<Workplace> workplaces) {
            this.workplaces = new HashMap<>(workplaces.size());

            for (var workplace : workplaces) {
                this.workplaces.put(workplace.getId(), new OrderlyWorkplace(workplace));
            }

            this.users = new HashMap<>();
        }

        public boolean hasUser(long uid) {
            return users.containsKey(uid);
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

        public void updateMappings() {
            for (var workplace : workplaces.values()) {
                updateMapping(workplace);
            }
        }

        public void logState() {
            for (var entry : workplaces.entrySet()) {
                var workplace = entry.getValue();
                System.out.printf("%s (%s) -> %s (awaiting: %s)\n",
                        entry.getKey(), workplace.getState(), workplace.getUserId(), workplace.isAwaited());
            }
        }
    }

    private SemaphoreQueue queue;
    private WorkplaceMap workplaces;
    private Semaphore mutex = new Semaphore(1, true);

    private long currentTime = 0;

    public OrderlyWorkshop(Collection<Workplace> workplaces) {
        queue = new SemaphoreQueue(workplaces.size() * 2);
        this.workplaces = new WorkplaceMap(workplaces);
    }

    private synchronized void leaveCurrentWorkplace(boolean release) {
        var currentWorkplace = workplaces.getThroughUser(uid());
        if (currentWorkplace == null) {
            ErrorHandling.panic();
        }

        currentWorkplace.leave();
        workplaces.updateMapping(currentWorkplace);

        if (currentWorkplace.isAwaited()) { // someone's waiting
            currentWorkplace.signal();
        } else if (!queue.empty()) {
            queue.signal();
        } else if (release) {
            mutex.release();
        }
    }

    private long uid() {
        return Thread.currentThread().getId();
    }

//    private Workplace switchTo(Workplace workplace, Semaphore mutex, boolean hasMutex, long originalTime) {
//
//    }

    private void occupy(OrderlyWorkplace workplace) {
        workplace.occupy();
        workplaces.updateMapping(workplace);
    }

    private void logState(String label) {
        System.out.println("-----------------------------");
        System.out.printf("uid: %d, t: %d, %s\n", uid(), currentTime, label);
        workplaces.logState();
        System.out.println("-----------------------------");
    }


    @Override
    public Workplace enter(WorkplaceId wid) {
        logState("enter");

        try {
            mutex.acquire();
            currentTime++;

            var workplace = workplaces.get(wid);
            switch (workplace.getState()) {
                case Empty: {
                    occupy(workplace);
                }; break;
                case Before: {
                    mutex.release();
                    queue.await(currentTime);
                    mutex.release();
                    return enter(wid);
                }
                case Done: {
                    if (workplace.isAwaited()) {
                        mutex.release();
                        queue.await(currentTime);
                        occupy(workplace);
                    } else {
                        mutex.release();
                        workplace.await();
                        occupy(workplace);
                    }
                }; break;
                default: {
                    assert false;
                }
            }

            mutex.release();
            return workplace;
        } catch (InterruptedException e) {
            ErrorHandling.panic();
        }

        return null;
    }

    private Workplace switchTo(OrderlyWorkplace workplace, boolean hasMutex) throws InterruptedException {
        if (!hasMutex) {
            mutex.acquire();
        }

        if (workplace.getUserId() != uid()) {
            switch (workplace.getState()) {
                case Empty: {
                    logState("switch_to->Empty->leaving");
                    leaveCurrentWorkplace(false);
                    logState("switch_to->Empty->occupying");
                    occupy(workplace);
                }; break;
                case Before: {
                    logState("switch_to->Before->awaiting");
                    if (workplace.isAwaited()) {
                        mutex.release();
                        queue.await(currentTime);
                        logState("switch_to->Before->awaited->recurring");
                        return switchTo(workplace, true);
                    } else {
                        leaveCurrentWorkplace(true);
                        workplace.await();
                        logState("switch_to->Before->awaiting->occupying");
                        occupy(workplace);
                    }
                }
                case Done: {
                    if (workplace.isAwaited()) {
                        logState("switch_to->Done->awaited");
                        mutex.release();
                        queue.await(currentTime);
                        return switchTo(workplace, true);
                    } else {
                        logState("switch_to->Done->not_awaited->leaving");
                        leaveCurrentWorkplace(false);
                        logState("switch_to->Done->not_awaited->awaiting");
                        mutex.release();
                        workplace.await();
                        occupy(workplace);
                    }
                }; break;
            }
        }

        mutex.release();
        return workplace;
    }

    @Override
    public Workplace switchTo(WorkplaceId wid) {
        try {
            mutex.acquire();
            logState("switch_to");
            currentTime++;

            return switchTo(workplaces.get(wid), true);
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
