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
        this.executor = Executors.newFixedThreadPool(32);
    }

    @Override
    public void update(Observable o, Object arg) {
        @SuppressWarnings("unchecked")
        Map<String, Long> clock = (Map<String, Long>) arg;
        for (FileWorker worker : map.values()) {
            worker.setClocks(clock);
        }
    }

    public void serve(Request request) {
        if (!map.containsKey(request.getHeader()) || !map.get(request.getHeader()).isWorking())
            map.put(request.getHeader(), new FileWorker());

        FileWorker worker = map.get(request.getHeader());
        executor.execute(worker);
        worker.serve(request);
    }

    public void release(Request request) {
        FileWorker worker = map.get(request.getHeader());
        if (worker != null) {
            worker.release(request);
            if (!worker.isWorking()) map.remove(request.getHeader());
        }
    }
}
