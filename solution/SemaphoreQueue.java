package cp2022.solution;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.Semaphore;

public class SemaphoreQueue {
    public static class SemaphoreQueueItem {
        private final Semaphore delay = new Semaphore(0);
        private long time = 0;

        public SemaphoreQueueItem(long currentTime) {
            time = currentTime;
        }

        public void await() throws InterruptedException {
            delay.acquire();
        }

        public void signal() {
            delay.release();
        }

        public static class SemaphoreQueueItemComparator implements Comparator<SemaphoreQueueItem> {
            @Override
            public int compare(SemaphoreQueueItem a, SemaphoreQueueItem b) {
                return Long.compare(a.time, b.time);
            }
        }
    }

    private final PriorityQueue<SemaphoreQueueItem> queue;

    public SemaphoreQueue(int initialSize) {
        queue = new PriorityQueue<>(initialSize, new SemaphoreQueueItem.SemaphoreQueueItemComparator());
    }

    public int size() {
        return queue.size();
    }

    public boolean empty() {
        return queue.isEmpty();
    }

    public void logTimes(StringBuilder b) {
        for (var node : queue) {
            b.append(String.format("%s ", node.time));
        }
    }

    public long minTime() {
        assert queue.peek() != null;
        return queue.peek().time;
    }

    public void await(long currentTime) throws InterruptedException {
        var item = new SemaphoreQueueItem(currentTime);
        queue.add(item);

        item.await();
    }

    public void signal() {
        if (queue.isEmpty()) {
            return;
        }
        var item = queue.poll();
        StringBuilder b = new StringBuilder();
        b.append(String.format("[Queue] signaling uid %s, t: %s\ntimes: ", Thread.currentThread().getId(), item.time));
        logTimes(b);
        System.out.println(b);

        item.signal();
    }
}
