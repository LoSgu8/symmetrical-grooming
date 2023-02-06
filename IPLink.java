import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.Node;

import java.util.ArrayList;
import java.util.List;

public class IPLink {
    private Node startNode;
    private Node endNode;
    private List<Link> path;
    private Modulation modulation;
    private List<Demand> demands;
    private double spareCapacity;


    public IPLink(Node startNode, Node endNode, List<Link> path, Modulation modulation) {
        this.startNode = startNode;
        this.endNode = endNode;
        this.path = path;
        this.modulation = modulation;
        this.spareCapacity = modulation.getDatarate();
        this.demands = new ArrayList<>((int)spareCapacity/100);
    }

    public void addDemand(Demand demand) {
        demands.add(demand);
        spareCapacity -= demand.getOfferedTraffic();
    }

    public List<Link> getPath() {
        return path;
    }

    public Node getStartNode() {
        return startNode;
    }

    public Node getEndNode() {
        return endNode;
    }

    public Modulation getModulation() {
        return modulation;
    }

    public List<Demand> getDemands() {
        return demands;
    }

    public double getSpareCapacity() {
        return spareCapacity;
    }
}
