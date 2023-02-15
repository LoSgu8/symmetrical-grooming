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
    private Link N2PLink;


    public IPLink(List<Link> path, int slotid, Modulation modulation) {
        this.rsa = new WDMUtils.RSA(path, slotid, modulation.getChannelSpacing());
        this.modulation = modulation;
        this.spareCapacity = modulation.getDatarate();
        this.demands = new ArrayList<>();
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

    public Link getN2PLink() {
        return N2PLink;
    }

    public void setN2PLink(Link n2PLink) {
        N2PLink = n2PLink;
    }

}
