import com.net2plan.interfaces.networkDesign.Net2PlanException;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.utils.*;



import java.util.*;

public class TrafficGenerator {

    private final NetPlan netPlan;
    private Map<String, List<Pair<Double, List<Pair<String, Double>>>>> distribution;
    private double probabilityOfStartingFromCore = 0.5;



    public TrafficGenerator(NetPlan netPlan, double probabilityOfStartingFromCore)
    {
        this.netPlan = netPlan;
        this.distribution = new HashMap<>(2);
        distribution.put("CORE", new ArrayList<>(3));
        // CORE -> SAME CORE
        distribution.get("CORE").add(new Pair<>(0.4, new ArrayList<>(Arrays.asList(
                new Pair<>("Priority",1.0, false),
                new Pair<>("BE",0.0, false)
        )),false));
        // CORE -> DIFFERENT CORE
        distribution.get("CORE").add(new Pair<>(0.3, new ArrayList<>(Arrays.asList(
                new Pair<>("Priority",1.0, false),
                new Pair<>("BE",0.0, false)
        )),false));
        // CORE -> METRO
        distribution.get("CORE").add(new Pair<>(0.3, new ArrayList<>(Arrays.asList(
                new Pair<>("Priority",1.0, false),
                new Pair<>("BE",0.0, false)
        )),false));

        distribution.put("METRO", new ArrayList<>(3));
        // METRO -> SAME METRO
        distribution.get("METRO").add(new Pair<>(0.5, new ArrayList<>(Arrays.asList(
                new Pair<>("Priority",0.5, false),
                new Pair<>("BE",0.5, false)
        )),false));
        // METRO -> different METRO
        distribution.get("METRO").add(new Pair<>(0.2, new ArrayList<>(Arrays.asList(
                new Pair<>("Priority",0.3, false),
                new Pair<>("BE",0.7, false)
        )),false));
        // METRO -> CORE
        distribution.get("METRO").add(new Pair<>(0.3, new ArrayList<>(Arrays.asList(
                new Pair<>("Priority",1.0, false),
                new Pair<>("BE",0.0, false)
        )),false));

        this.probabilityOfStartingFromCore = probabilityOfStartingFromCore;

    }

    public TrafficGenerator(NetPlan netPlan)
    {
        this(netPlan, 0.5);
    }

    public void generate(int numberOfDemands)
    {
        // Separate nodes according to their tag
        List <Node> coreNodes = new ArrayList<>(netPlan.getTaggedNodes("CORE"));
        List <Node> metroNodes = new ArrayList<>(netPlan.getTaggedNodes("METRO"));

        // Remove from metroNodes elements that are also in coreNodes
        metroNodes.removeAll(coreNodes);

        // Generate a random demand for each iteration
        for (int i = 0; i < numberOfDemands; i++) {
            Node sourceNode, destinationNode;
            String sourceType, destinationType;
            String destinationIsland;
            // Choose if the source of this demand is a core node or a metro node (probabilityOfStartingFromCore chance)
            if (Math.random() > this.probabilityOfStartingFromCore) {
                // Extract a random node from CORE node set
                int randomNodeIndex = (int) (Math.random() * coreNodes.size());
                sourceNode = coreNodes.get(randomNodeIndex);
                sourceType = "CORE";
            } else {
                // Extract a random node from METRO node set
                int randomNodeIndex = (int) (Math.random() * metroNodes.size());
                sourceNode = metroNodes.get(randomNodeIndex);
                sourceType = "METRO";
            }

            // Choose destination type
            double random = Math.random();
            double probSameRegion = distribution.get(sourceType).get(0).getFirst();
            double probSameType = distribution.get(sourceType).get(1).getFirst();
            double probDifferentType = distribution.get(sourceType).get(2).getFirst();
            List<Node> destinationCandidates = new ArrayList<>();

            if (random <= probSameRegion ){

                List <Node> destinationCandidatesToSelect = new ArrayList<>(netPlan.getTaggedNodes(sourceType));
                destinationCandidatesToSelect.remove(sourceNode);
                List<String> sourceNodeislands = new ArrayList<>(sourceNode.getTags());
                sourceNodeislands.remove("CORE");
                sourceNodeislands.remove("METRO");


                for(String island : sourceNodeislands) {
                    List<Node> nodesOnIsland = destinationCandidatesToSelect;
                    nodesOnIsland.retainAll(netPlan.getTaggedNodes(island));
                    destinationCandidates.addAll(nodesOnIsland);
                }


            }
            else if(random <= probSameType + probSameRegion){
                // same type, different region


            }
            else if(random <= probDifferentType + probSameType + probSameRegion ){
                // different type, different region

            }
            else
            {
                throw new Net2PlanException("Wrong distribution of probabilty in traffic generation");
            }

            // Choose destination node
            int randomNodeIndex = (int) (Math.random() * destinationCandidates.size());
            destinationNode = destinationCandidates.get(randomNodeIndex);
            // Choose QoS

        }

    }
}




/*

min 350 services of 100Gbps, increase by 50 services



                CORE     |       METRO
SUBREGION: same | diff   |  same  |  diff
------------------------------------------------------
CORE       0.4  |  0.3   |      0.3
QOS         H       H    |       H
------------------------------------------------------
METRO           0.3      |  0.5         |  0.2
QOS             H        | 0.5H/0.5BE   | 0.3H/0.7BE
------------------------------------------------------
 */