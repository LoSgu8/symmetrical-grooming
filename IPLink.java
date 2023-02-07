import cern.colt.matrix.tint.IntMatrix2D;
import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.libraries.WDMUtils;

import java.util.ArrayList;
import java.util.List;

public class IPLink {
    private final WDMUtils.RSA rsa;
    private final Modulation modulation;
    private final List<Demand> demands;
    private double spareCapacity;


    public IPLink(Node startNode, Node endNode, List<Link> path, IntMatrix2D seqFrequencySlots_se, Modulation modulation) {
        this.rsa = new WDMUtils.RSA(path, seqFrequencySlots_se);
        this.modulation = modulation;
        this.spareCapacity = modulation.getDatarate();
        this.demands = new ArrayList<>((int)spareCapacity/100);
    }

    public void addDemand(Demand demand) {
        demands.add(demand);
        spareCapacity -= demand.getOfferedTraffic();
    }

    public WDMUtils.RSA getRsa() {
        return rsa;
    }

    public List<Link> getPath() {
        return rsa.seqLinks;
    }

    public Node getStartNode() {
        return rsa.ingressNode;
    }

    public Node getEndNode() {
        return rsa.egressNode;
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
