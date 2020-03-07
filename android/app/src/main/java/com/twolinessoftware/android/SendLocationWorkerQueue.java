/*
 * Copyright (c) 2011 2linessoftware.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twolinessoftware.android;

import com.twolinessoftware.android.framework.service.comms.gpx.GpxTrackPoint;
import com.twolinessoftware.android.framework.util.Logger;

import java.util.LinkedList;

public class SendLocationWorkerQueue {
    private static final String TAG = SendLocationWorkerQueue.class.getSimpleName();

    private LinkedList<SendLocationWorker> queue;
    private boolean running;
    private boolean pause;
    private WorkerThread thread;
    private GpxTrackPoint currentPointWorker;

    private Object lock = new Object();

    public SendLocationWorkerQueue() {
        queue = new LinkedList<>();
        running = false;
        pause = false;
        currentPointWorker = null;
    }


    public void addToQueue(SendLocationWorker worker) {
        synchronized (queue) {
            queue.addLast(worker);
        }

    }

    public synchronized void start(long delayTimeOnReplay) {
        running = true;
        pause = false;
        thread = new WorkerThread(delayTimeOnReplay);
        thread.start();
    }

    public synchronized void stop() {
        /*
         * synchronized(lock){ lock.notify(); }
         */
        running = false;
    }

    public synchronized void pause() {
        synchronized (lock) {
            pause = true;
        }
    }

    public synchronized void resume() {
        synchronized (lock) {
            pause = false;
            lock.notifyAll();
        }

    }

    public void reset() {
        stop();
        queue.clear();// = new LinkedList<>();
        stopThread();
    }

    public void stopThread() {
        if (thread != null) {
            try {
                thread.interrupt();
            } catch (Exception e) {
                Logger.i(TAG, "SendLocationWorkerQueue.stopThread() - exception " + e.getMessage());
            }
            this.thread = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getQueueSize() {
        return queue.size();
    }

    public GpxTrackPoint getCurrentPointWorker() {
        return currentPointWorker;
    }

    public synchronized void updateDelayTime(long timeInMilliseconds){
        synchronized (lock){
            if(thread != null)
                thread.updateDelayTimeOnReplay(timeInMilliseconds);
        }
    }

    private class WorkerThread extends Thread {

        private long TIME_BETWEEN_SENDS = 1000; // milliseconds

        WorkerThread(long delayTimeOnReplay) {
            TIME_BETWEEN_SENDS = delayTimeOnReplay;
        }

        public void run() {
            while (running) {
                if (queue.size() > 0) {
                    SendLocationWorker worker = queue.pop();
                    currentPointWorker = worker.getPoint();
                    synchronized (lock) {
                        while (pause) {
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        try {
                            lock.wait(TIME_BETWEEN_SENDS);
                            Logger.i(TAG, "SendLocationWorkerQueue.running - TIME_BETWEEN_SENDS : " + TIME_BETWEEN_SENDS + " - sent at time : " + System.currentTimeMillis());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    // Executing each worker in the current thread. Multiple threads NOT created.
                    worker.run();
                }
            }
        }

        public void updateDelayTimeOnReplay(long timeInMilliseconds ) {
            TIME_BETWEEN_SENDS = timeInMilliseconds;
        }
    }
}
