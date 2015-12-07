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

import com.twolinessoftware.android.framework.util.Logger;

import java.util.LinkedList;

public class SendLocationWorkerQueue {

	private LinkedList<SendLocationWorker> queue;
	private boolean running;
	private WorkerThread thread;

	private Object lock = new Object();

	public SendLocationWorkerQueue() {
		queue = new LinkedList<SendLocationWorker>();
		running = false;
	}


	public void addToQueue(SendLocationWorker worker) {
		synchronized (queue) {
			queue.addLast(worker);
		}

	}

	public synchronized void start(long delayTimeOnReplay) {
		running = true;
		thread = new WorkerThread(delayTimeOnReplay);
		thread.start();
	}

	public synchronized void stop() {
		/*
		 * synchronized(lock){ lock.notify(); }
		 */
		running = false;
	}

	public void reset() {
		stop();
		queue = new LinkedList<SendLocationWorker>();

		stopThread();
	}

	public void stopThread(){
		if(thread != null){
			try {
				thread.interrupt();
			} catch (Exception e) {
				Logger.i("SendLocationWorkerQueue.stopThread() - exception","" + e.getMessage());
			}
			this.thread = null;
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

					synchronized (lock) {
						try {
							lock.wait(TIME_BETWEEN_SENDS);

							Logger.i("SendLocationWorkerQueue.running - TIME_BETWEEN_SENDS : " + TIME_BETWEEN_SENDS, " - sent at time : " + System.currentTimeMillis());
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					// Executing each worker in the current thread. Multiple threads NOT created.
                   worker.run();
				}
			}
		}
	}

}
