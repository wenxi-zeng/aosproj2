package ring;

import commonmodels.PhysicalNode;
import util.Config;
import util.FileHelper;
import util.MathX;

import java.util.ArrayList;
import java.util.List;

public class LookupTable {

    private static volatile LookupTable instance = null;

    private List<PhysicalNode> nodes;

    public LookupTable() {
        nodes = Config.getInstance().getServers();
    }

    public static LookupTable getInstance() {
        if (instance == null) {
            synchronized(LookupTable.class) {
                if (instance == null) {
                    instance = new LookupTable();
                }
            }
        }

        return instance;
    }

    public List<PhysicalNode> lookup(String filename) {
        int h = FileHelper.hash(filename);
        List<PhysicalNode> replicas = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            PhysicalNode node = nodes.get((h + 1) % 7);
            if (node.isActive())
                replicas.add(node);
        }

        return replicas;
    }

    public String lookupInactive(String filename) {
        int h = FileHelper.hash(filename);
        List<String> replicas = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            PhysicalNode node = nodes.get((h + 1) % 7);
            if (!node.isActive())
                replicas.add(node.getId());
        }

        return String.join(" and ", replicas);
    }

    public void disrupt(String nodeId) {
        for (PhysicalNode node : nodes) {
            if (node.getId().equals(nodeId)) {
                node.setActive(false);
                break;
            }
        }
    }

    public void resume(String nodeId) {
        for (PhysicalNode node : nodes) {
            if (node.getId().equals(nodeId)) {
                node.setActive(true);
                break;
            }
        }
    }

    public PhysicalNode chooseServer(String file) {
        List<PhysicalNode> pnodes = lookup(file);
        return pnodes.get(MathX.nextInt(pnodes.size()));
    }

    public PhysicalNode chooseServer(String file, int replica) {
        if (replica < 0) return chooseServer(file);

        List<PhysicalNode> pnodes = lookup(file);
        return pnodes.get(replica % 7);
    }
}
