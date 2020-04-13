package managers;

import commonmodels.transport.Request;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileManager implements Observer {

    private static volatile FileManager instance = null;

    private Map<String, FileWorker> map;

    private ExecutorService executor;

    private FileManager() {
        map = new ConcurrentHashMap<>();
    }

    public static FileManager getInstance() {
        if (instance == null) {
            synchronized(FileManager.class) {
                if (instance == null) {
                    instance = new FileManager();
                }
            }
        }

        return instance;
    }

    public void init() {
        this.executor = Executors.newFixedThreadPool(128);
    }

    @Override
    public synchronized void update(Observable o, Object arg) {
        @SuppressWarnings("unchecked")
        Map<String, Long> clock = (Map<String, Long>) arg;
        for (FileWorker worker : map.values()) {
            worker.setClocks(clock);
        }
    }

    public synchronized void serve(Request request) {
        if (map.containsKey(request.getHeader())) {
            FileWorker worker = map.get(request.getHeader());
            worker.serve(request);
        }
        else {
            map.put(request.getHeader(), new FileWorker());
            FileWorker worker = map.get(request.getHeader());
            worker.serve(request);
            executor.execute(worker);
        }
    }

    public synchronized void release(Request request) {
        if (request.getHeader() == null) {
            for (FileWorker worker : map.values()) {
                worker.release(request);
            }
        }
        else {
            FileWorker worker = map.get(request.getHeader());
            if (worker != null) {
                worker.release(request);
                // if (!worker.isWorking()) map.remove(request.getHeader());
            }
        }
    }
}
