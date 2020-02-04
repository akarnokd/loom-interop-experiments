package hu.akarnokd.javaflow.loom;

import java.util.concurrent.locks.*;

public class ResumableLock {

    final ReentrantLock lock;

    final Condition condition;

    boolean flag;

    public ResumableLock() {
        this.lock = new ReentrantLock();
        this.condition = lock.newCondition();
    }

    public void await() throws InterruptedException {
        lock.lock();
        try {
            while (!flag) {
                condition.await();
            }
            flag = false;
        } finally {
            lock.unlock();
        }
    }

    public void resume() {
        lock.lock();
        try {
            flag = true;
            condition.signal();
        } finally {
            lock.unlock();
        }
    }
}
