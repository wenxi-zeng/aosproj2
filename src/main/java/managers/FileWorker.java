package managers;

import commonmodels.PhysicalNode;
import commonmodels.transport.Request;
import ring.LookupTable;
import util.Config;
import util.FileHelper;
import util.SimpleLog;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileWorker implements Runnable{

    private final Queue<Request> queue;

    private final Semaphore semaphore;

    private final AtomicBoolean working;

    private Map<String, Long> clocks;

    public FileWorker() {
        this.queue = new PriorityQueue<>((a, b) ->
                (int)(a.getTimestamp() - b.getTimestamp() != 0 ?
                        a.getTimestamp() - b.getTimestamp() :
                        a.getSender().compareTo(b.getSender())));
        this.semaphore = new Semaphore(0);
        this.working = new AtomicBoolean(true);
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {

                SimpleLog.v("run 1.........................");
                while (queue.isEmpty() ||
                        !queue.peek().getSender().equals(Config.getInstance().getAddress()) ||
                        !ackFromAll(queue.peek())) {
                    if (queue.isEmpty()) SimpleLog.v("run semaphore sender check, queue is empty");
                    else
                        SimpleLog.v("run semaphore sender check, sender: " + queue.peek().getSender() + ", self: " + Config.getInstance().getAddress());
                    semaphore.acquire();
                    SimpleLog.v("run semaphore release");
                }

                SimpleLog.v("run 2.........................");
                if (!queue.isEmpty()) {
                    if (queue.peek().getSender().equals(Config.getInstance().getAddress()))
                        operateFile(queue.poll());
                    else
                        SimpleLog.v("run sender check, sender: " + queue.peek().getSender() + ", self:" + Config.getInstance().getAddress());
                }
                else
                    SimpleLog.v("queue is empty");

                SimpleLog.v("run 3.........................");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        SimpleLog.v("thread stop working");
    }

    private void operateFile(Request request) {
        SimpleLog.v("operateFile " + request);
        try {
            FileHelper.append(Config.getInstance().getId(), request.getHeader(), request.getAttachment());
            if (request.getProcessed() != null) {
                request.getProcessed().release();
                SimpleLog.v("operateFile release");
            }
            else {
                SimpleLog.v("operateFile semaphore is null");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean ackFromAll(Request request) {
        if (clocks == null) {
            SimpleLog.v("ackFromAll clock is null");
            return false;
        }
        List<Long> replicaClocks = new ArrayList<>();
        for (PhysicalNode node : LookupTable.getInstance().lookup(request.getHeader())) {
            if (clocks.containsKey(node.getAddress()))
                replicaClocks.add(clocks.get(node.getAddress()));
        }

        if (replicaClocks.isEmpty()) {
            SimpleLog.v("ackFromAll no replicas");
            return false;
        }

        for (long clock : replicaClocks) {
            if (request.getTimestamp() > clock) {
                SimpleLog.v("ackFromAll not ack all, request: " + request.getTimestamp() + ", clock: " + clock);
                return false;
            }
        }

        return true;
    }

    public void serve(Request request) {
        queue.add(request);
        this.semaphore.release();
    }

    public void release(Request request) {
        SimpleLog.v("release, sender: " + request.getSender());
        if (!queue.isEmpty() &&
                queue.peek().getSender().equals(request.getSender())) {

            SimpleLog.v("release, sender is same");
            queue.poll();
        }
        this.semaphore.release();

    }

    public void setClocks(Map<String, Long> clocks) {
        SimpleLog.v("setClocks. clock updated. thread is working: " + isWorking());
        SimpleLog.i(clocks);
        this.clocks = clocks;
        this.semaphore.release();
        SimpleLog.i("setClocks done!");
    }

    public boolean isWorking() {
        return working.get();
    }
}
