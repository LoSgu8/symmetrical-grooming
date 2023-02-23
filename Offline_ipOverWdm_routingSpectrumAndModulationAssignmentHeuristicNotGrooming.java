
import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import com.net2plan.interfaces.networkDesign.*;
import com.net2plan.libraries.WDMUtils;
import com.net2plan.utils.Constants.RoutingType;
import com.net2plan.utils.InputParameter;
import com.net2plan.utils.Pair;
import com.net2plan.utils.Triple;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

// Main class


public class Offline_ipOverWdm_routingSpectrumAndModulationAssignmentHeuristicNotGrooming implements IAlgorithm
{
	private final InputParameter k = new InputParameter ("k", 10, "Maximum number of admissible paths per input-output node pair" , 1 , Integer.MAX_VALUE);
	private final InputParameter numFrequencySlotsPerFiber = new InputParameter ("numFrequencySlotsPerFiber", 4950 , "Number of wavelengths per link" , 1, Integer.MAX_VALUE);
	// InputParameter to use a single type of transponder in the entire network
	private final InputParameter singleTransponderForAll = new InputParameter ("singleTransponderForAll", false , "If true, a single transponder type is used in the entire network");
	// InputParameter to define the type of transponder to use in case of singleTransponderForAll = true
	private final InputParameter singleTransponderType = new InputParameter ("singleTransponderType", true , "Transponder type to use in case of singleTransponderForAll = true, if true LR is used, if false ZR+ is used");
	// InputParameter to define the percentage of the total traffic generated by core nodes
	private final InputParameter percentageOfCoreTraffic = new InputParameter ("percentageOfCoreTraffic", 0.5 , "Percentage of the total traffic generated by core nodes" , 0 , true , 1 , true);
	private final InputParameter maxPropagationDelayMs = new InputParameter ("maxPropagationDelayMs", -1.0 , "Maximum allowed propagation time of a lightpath in milliseconds. If non-positive, no limit is assumed");
	private final InputParameter NumberOfDemands = new InputParameter("NumberOfDemands", 350, "Number of demands to be generated");
	private final InputParameter resultPath = new InputParameter("resultPath", "result", "Path of the folder for the result file");
	private NetPlan netPlan;
	private Map<Pair<Node,Node>,List<List<Link>>> cpl;
	private NetworkLayer wdmLayer, ipLayer;
	//private WDMUtils.TransponderTypesInfo transponderInfo;
	private final Map<String,Transponder> transponders = new HashMap<>();
	private int NodeNumber;
	private int LinkNumberWDM;
	private int SlotPerFiber;
	private DoubleMatrix2D frequencySlot2FiberOccupancy_se;
	private int totalZR = 0;
	private int totalLR = 0;
	private int totalCost = 0;
	private int demandNumber;
	private static final String QOS_TYPE_PRIORITY = "PRIORITY";
	private static final String QOS_TYPE_BEST_EFFORT = "BEST_EFFORT";
	private static final String SUBREGION_TYPE_CORE = "CORE";
	private static final String SUBREGION_TYPE_METRO = "METRO";

	@Override
	public String executeAlgorithm(NetPlan netPlan, Map<String, String> algorithmParameters, Map<String, String> net2planParameters) {
		/* Initialize all InputParameter objects defined in this object (this uses Java reflection) */
		InputParameter.initializeAllInputParameterFieldsOfObject(this, algorithmParameters);

		this.netPlan = netPlan;
		netPlan.setRoutingTypeAllDemands(RoutingType.SOURCE_ROUTING, netPlan.getNetworkLayerDefault());

		/* Create a two-layer IP over WDM design if the input is single layer */
		if (netPlan.getNumberOfLayers() == 1) {
			this.wdmLayer = netPlan.getNetworkLayer("WDM");
			this.netPlan.setDemandTrafficUnitsName("Gbps");
			this.netPlan.setLinkCapacityUnitsName("GHz");
			this.ipLayer = netPlan.addLayer("IP", "IP layer", "Gbps", "Gbps", null, null);
			for (Demand wdmDemand : netPlan.getDemands(wdmLayer)) {
				netPlan.addDemand(wdmDemand.getIngressNode(), wdmDemand.getEgressNode(), wdmDemand.getOfferedTraffic(), RoutingType.SOURCE_ROUTING, wdmDemand.getAttributes(), ipLayer);
			}
		} else {
			this.wdmLayer = netPlan.getNetworkLayer("WDM");
			this.ipLayer = netPlan.getNetworkLayer("IP");
		}

		/* Basic checks */
		this.NodeNumber = netPlan.getNumberOfNodes();
		this.LinkNumberWDM = netPlan.getNumberOfLinks(wdmLayer);
		this.SlotPerFiber = numFrequencySlotsPerFiber.getInt();
		if (NodeNumber == 0 || LinkNumberWDM == 0)
			throw new Net2PlanException("This algorithm requires a topology with links");

		demandNumber = NumberOfDemands.getInt();

		/* Store transponder info */
		WDMUtils.setFibersNumFrequencySlots(netPlan, SlotPerFiber, wdmLayer);

		initializeTransponders();

		/* Remove all routes in current netPlan object. Initialize link capacities and attributes, and demand offered traffic */
		/* WDM and IP layer are in source routing type */
		netPlan.removeAllLinks(ipLayer);
		netPlan.removeAllDemands(wdmLayer);
		netPlan.removeAllDemands(ipLayer);
		netPlan.removeAllMulticastDemands(wdmLayer);

		/* Initialize the slot occupancy */
		this.frequencySlot2FiberOccupancy_se = DoubleFactory2D.dense.make(SlotPerFiber, LinkNumberWDM);

		/* Compute the candidate path list of possible paths */
		this.cpl = netPlan.computeUnicastCandidatePathList(netPlan.getVectorLinkLengthInKm(wdmLayer), k.getInt(), -1, -1, maxPropagationDelayMs.getDouble(), -1, -1, -1, null, wdmLayer);

		/* Compute the CPL, adding the routes */
		Map<Pair<Node, Node>, List<IPLink>> mapIPLinks = new HashMap<>();

		int unsatisfiedDemands = 0;

		List<Demand> orderedDemands;
		for(Node n1: netPlan.getNodes())
		{
			for(Node n2: netPlan.getNodes())
			{
				if(!n1.equals(n2))
				{
					mapIPLinks.put(Pair.of(n1,n2),new ArrayList<>());
				}
			}
		}
        for(Node node: netPlan.getNodes())
        {
            node.setAttribute("ZR",0);
            node.setAttribute("LR",0);
        }
		for(Link link: netPlan.getLinks(wdmLayer))
		{
			link.setAttribute("ZR",0);
			link.setAttribute("LR",0);
		}

		// Generate the demands in the IP layer using TrafficGenerator Class
		TrafficGenerator trafficGenerator = new TrafficGenerator(netPlan, percentageOfCoreTraffic.getDouble());
		trafficGenerator.generate(demandNumber);


		// Order netPlan.getDemands(ipLayer) according
		// to qosType (priority first, best-effort last) and length of the shortest path
		orderedDemands = new ArrayList<>(netPlan.getDemands(ipLayer));
		orderedDemands.sort((d1, d2) -> {
			/*if (d1.getQosType().equals(QOS_TYPE_PRIORITY) && d2.getQosType().equals(QOS_TYPE_BEST_EFFORT)) {
				return -1;
			} else if (d1.getQosType().equals(QOS_TYPE_BEST_EFFORT) && d2.getQosType().equals(QOS_TYPE_PRIORITY)) {
				return 1;
			} else {*/
				return Double.compare(
						(d1.getQosType().equals(QOS_TYPE_PRIORITY)?1:2)*getLengthInKm(cpl.get(Pair.of(d1.getIngressNode(), d1.getEgressNode())).get(0)),
						(d2.getQosType().equals(QOS_TYPE_PRIORITY)?1:2)*getLengthInKm(cpl.get(Pair.of(d2.getIngressNode(), d2.getEgressNode())).get(0))
				);

		});

		for (Demand ipDemand : orderedDemands) {

			boolean atLeastOnePath = false;
			int bestPathCost = Integer.MAX_VALUE;
			List<List<Link>> bestPath = null;
			List<Modulation> bestPathModulations = null;


			for (List<Link> singlePath : cpl.get(Pair.of(ipDemand.getIngressNode(), ipDemand.getEgressNode()))) {

				List<List<Link>> subpathsList;

				// if singleTransponderForAll is false, then the path is split in subpaths, each one with a different transponder
				if (!singleTransponderForAll.getBoolean()){
					//path -> list(subpath)
					subpathsList = calculateSubPath(singlePath);
				} else {
					//path -> list(path)
					subpathsList = new ArrayList<>();
					subpathsList.add(singlePath);
				}

				List<Modulation> modulationsList = new ArrayList<>();
				for (int ind = 0; ind < subpathsList.size(); ind++) {
					List<Link> subpath = subpathsList.get(ind);

					// If subpath length is longer than the maximum reach of the transponder -> split the subpath in shorter subpaths

					String tag;
					if (!singleTransponderForAll.getBoolean()) {
						List<String> tags = new ArrayList<>(subpath.get(0).getTags());
						tags.retainAll(Arrays.asList(SUBREGION_TYPE_CORE,SUBREGION_TYPE_METRO));
						tag = tags.get(0); // "METRO" or "CORE"
					} else {
						if (singleTransponderType.getBoolean()) {
							tag = SUBREGION_TYPE_CORE; // Long Reach is used in the entire network
						} else {
							tag = SUBREGION_TYPE_METRO; // ZR+ is used in the entire network
						}
					}
					if (this.transponders.get(tag).getMaxReach() <= getLengthInKm(subpath)) {
						List<List<Link>> subsubpaths = calculateSubPathsBasedOnTransponder(subpath, this.transponders.get(tag));
						int index = subpathsList.indexOf(subpath);
						subpathsList.remove(subpath);
						subpathsList.addAll(index, subsubpaths);
						subpath = subpathsList.get(ind);
					}
					//find the best modulation
					Modulation bestModulation = this.transponders.get(tag).getBestModulationFormat(getLengthInKm(subpath), singleTransponderForAll.getBoolean()?Transponder.OBJECTIVE.LOWEST_SPECTRUM_OCCUPANCY :Transponder.OBJECTIVE.HIGHEST_SPECTRAL_EFFICIENCY);
					modulationsList.add(bestModulation);
				}

				boolean successInFindingPath = true;

				// check if the entire path has available resources
				for (int ind = 0; ind < subpathsList.size(); ind++) {
					List<Link> subpath = subpathsList.get(ind);
					Modulation modulation = modulationsList.get(ind);
					//check if an ipLink already exists, if not check the wdm availability
					boolean ipToAdd = true;
					for(IPLink link: mapIPLinks.get(Pair.of(subpath.get(0).getOriginNode(),subpath.get(subpath.size()-1).getDestinationNode())))
					{
						if(link.getSpareCapacity()>=ipDemand.getOfferedTraffic())
						{
							ipToAdd = false;
							break;
						}
					}
					if(ipToAdd) {
						int slotid = WDMUtils.spectrumAssignment_firstFit(subpath, frequencySlot2FiberOccupancy_se, modulation.getChannelSpacing());
						if (slotid == -1) {
							successInFindingPath = false;
							break;
						}
					}
				}

				// if the entire path is able to accommodate the demand, calculate cost and store the path with the smallest cost
				if (successInFindingPath) {

					atLeastOnePath = true;
					int cost = 0;
					for(int ind = 0; ind < subpathsList.size(); ind++)
					{
						List<Link> subpath = subpathsList.get(ind);
						Modulation modulation = modulationsList.get(ind);
						boolean ipToAdd = true;
						for(IPLink link: mapIPLinks.get(Pair.of(subpath.get(0).getOriginNode(),subpath.get(subpath.size()-1).getDestinationNode())))
						{
							if(link.getSpareCapacity()>=ipDemand.getOfferedTraffic())
							{
								ipToAdd = false;
								break;
							}
						}
						if(ipToAdd)
						{
							if (transponders.get(SUBREGION_TYPE_CORE).getModulations().contains(modulation)) {
								cost += transponders.get(SUBREGION_TYPE_CORE).getCost() * 2;

							} else {
								cost += transponders.get(SUBREGION_TYPE_METRO).getCost() * 2;
							}
						}
					}
					if(cost<bestPathCost)
					{
						bestPathCost = cost;
						bestPath = subpathsList;
						bestPathModulations = modulationsList;
					}
				}
			}


			//if no path has been found, handle the possible error
			if (!atLeastOnePath) {
				// if the demand Priority QoS, then return a message
				if (Objects.equals(ipDemand.getQosType(), QOS_TYPE_PRIORITY)) {
					throw new Net2PlanException("The demand from " + ipDemand.getIngressNode().getName() + " to " + ipDemand.getEgressNode().getName() + '\n' +
							"has not been satisfied due to insufficient resources (Priority) ");
				} else {
					// check if the threshold has been reached
					unsatisfiedDemands++;
					if((double)unsatisfiedDemands/orderedDemands.size()>0.01)
					{
						throw new Net2PlanException("BE demands drop larger than 0.01");
					}
				}
			}
			else
			{
				assert bestPath != null;
				List<Link> IPPath = new ArrayList<>();
				for (int ind = 0; ind < bestPath.size(); ind++) {
					List<Link> subpath = bestPath.get(ind);
					Modulation modulation = bestPathModulations.get(ind);
					IPLink ipLink;
					boolean ipToAdd=true;
					//check for an existing ip link with spare capacity
					for(IPLink link: mapIPLinks.get(Pair.of(subpath.get(0).getOriginNode(),subpath.get(subpath.size()-1).getDestinationNode())))
					{
						if(link.getSpareCapacity()>=ipDemand.getOfferedTraffic())
						{
							ipToAdd = false;
							ipLink = link;
							IPPath.add(link.getN2PLink());
							ipLink.addDemand(ipDemand);
							break;
						}
					}
					// if no ip link is available, another is created
					if(ipToAdd) {
						int slotid = WDMUtils.spectrumAssignment_firstFit(subpath, frequencySlot2FiberOccupancy_se, modulation.getChannelSpacing());
						ipLink = new IPLink(subpath, slotid, modulation);
						Demand newDemand = netPlan.addDemand(ipLink.getStartNode(), ipLink.getEndNode(), ipLink.getModulation().getChannelSpacing(), RoutingType.SOURCE_ROUTING, null, wdmLayer);
						Link n2pIPlink = netPlan.addLink(ipLink.getStartNode(),ipLink.getEndNode(),ipLink.getModulation().getDatarate(),ipLink.getRsa().getLengthInKm(),200000,null,ipLayer);
						ipLink.setN2PLink(n2pIPlink);
						ipLink.addDemand(ipDemand);
						IPPath.add(n2pIPlink);
						final double occupiedBandwidth = ipLink.getModulation().getChannelSpacing();
						netPlan.addRoute(newDemand, occupiedBandwidth, occupiedBandwidth, ipLink.getPath(), null);
						List<IPLink> ipList = mapIPLinks.get(Pair.of(ipLink.getStartNode(),ipLink.getEndNode()));
						if(ipList == null)
						{
							mapIPLinks.put(Pair.of(ipLink.getStartNode(),ipLink.getEndNode()),new ArrayList<>(Collections.singletonList(ipLink)));
						}
						else
						{
							ipList.add(ipLink);
						}
						WDMUtils.allocateResources(ipLink.getRsa(), frequencySlot2FiberOccupancy_se, null);
						if (transponders.get(SUBREGION_TYPE_CORE).getModulations().contains(modulation)) {
							totalCost += transponders.get(SUBREGION_TYPE_CORE).getCost() * 2;
                            ipLink.getStartNode().setAttribute("LR",Integer.parseInt(ipLink.getStartNode().getAttribute("LR"))+1);
                            ipLink.getEndNode().setAttribute("LR",Integer.parseInt(ipLink.getEndNode().getAttribute("LR"))+1);
							ipLink.getPath().get(0).setAttribute("LR",Integer.parseInt(ipLink.getPath().get(0).getAttribute("LR"))+1);
							ipLink.getPath().get(ipLink.getPath().size()-1).setAttribute("LR",Integer.parseInt(ipLink.getPath().get(ipLink.getPath().size()-1).getAttribute("LR"))+1);
							totalLR += 2;
						} else {
							totalCost += transponders.get(SUBREGION_TYPE_METRO).getCost() * 2;
                            ipLink.getStartNode().setAttribute("ZR",Integer.parseInt(ipLink.getStartNode().getAttribute("ZR"))+1);
                            ipLink.getEndNode().setAttribute("ZR",Integer.parseInt(ipLink.getEndNode().getAttribute("ZR"))+1);
							ipLink.getPath().get(0).setAttribute("ZR",Integer.parseInt(ipLink.getPath().get(0).getAttribute("ZR"))+1);
							ipLink.getPath().get(ipLink.getPath().size()-1).setAttribute("ZR",Integer.parseInt(ipLink.getPath().get(ipLink.getPath().size()-1).getAttribute("ZR"))+1);
							totalZR += 2;
						}
					}
				}
				netPlan.addRoute(ipDemand, ipDemand.getOfferedTraffic(), ipDemand.getOfferedTraffic(), IPPath, null);
			}
		}

		saveToXML();
		String outMessage = "Total cost: " + totalCost + ". Num lps " + netPlan.getNumberOfRoutes(wdmLayer);
		//System.out.println (outMessage);
		return "Ok! " + outMessage;
	}

	private static double getLengthInKm (Collection<Link> r) { double res = 0; for (Link e : r) res += e.getLengthInKm(); return res; }

	@Override
	public String getDescription()
	{
		return "Algorithm based on an ILP solving the Routing, Spectrum, Modulation Assignment (RSMA) problem with regenerator placement, in flexi (elastic) or fixed grid optical WDM networks, with or without fault tolerance, latency and/or lightpath bidirectionality requisites (see the Javadoc for details).";
	}

	@Override
	public List<Triple<String, String, String>> getParameters()
	{
		/* Returns the parameter information for all the InputParameter objects defined in this object (uses Java reflection) */
		return InputParameter.getInformationAllInputParameterFieldsOfObject(this);
	}

	private void initializeTransponders()
	{
		/* ZR+ OEO Transponder definition */
		List<Modulation> zrmods = new ArrayList<>(4);
		zrmods.add(new Modulation("16 QAM", 400, 75, 600));
		zrmods.add(new Modulation("8 QAM", 300, 75, 1800));
		zrmods.add(new Modulation("QPSK", 200, 75, 3000));
		zrmods.add(new Modulation("QPSK", 100, 75, 3000));

		this.transponders.put(SUBREGION_TYPE_METRO, new Transponder("ZR+ OEO", 0.5, zrmods));

		/* Long reach OEO Transponder definition */
		List<Modulation> lrmods = new ArrayList<>(9);
		lrmods.add(new Modulation("PCS 64 QAM", 800, 100, 150));
		lrmods.add(new Modulation("PCS 64 QAM", 700, 100, 400));
		lrmods.add(new Modulation("16 QAM", 600, 100, 700));
		lrmods.add(new Modulation("PCS 16 QAM", 500, 100, 1300));
		lrmods.add(new Modulation("PCS 16 QAM", 400, 100, 2500));
		lrmods.add(new Modulation("PCS 16 QAM", 300, 100, 4700));
		lrmods.add(new Modulation("64 QAM", 300, 50, 100));
		lrmods.add(new Modulation("16 QAM", 200, 50, 900));
		lrmods.add(new Modulation("QPSK", 100, 50, 3000));

		this.transponders.put(SUBREGION_TYPE_CORE, new Transponder("Long Reach OEO", 1, lrmods));
	}


	/* --- ADDED FUNCTIONS --- */
	/*
	 * calculateSubPath method
	 * Split the path into subpaths each one belonging to a single network category (METRO and CORE)
	 */
	private List<List<Link>> calculateSubPath(List<Link> path) {
		List<List<Link>> subPaths = new ArrayList<>();
		List<String> tags = new ArrayList<>(path.get(0).getTags());
		tags.retainAll(Arrays.asList(SUBREGION_TYPE_METRO, SUBREGION_TYPE_CORE));
		List<Link> currentSubPath = new ArrayList<>();
		for (Link link : path) {

			List<String> tags2 = new ArrayList<>(link.getTags());
			tags2.retainAll(Arrays.asList(SUBREGION_TYPE_METRO, SUBREGION_TYPE_CORE));
			tags.retainAll(tags2);
			if(tags.isEmpty())
			{
				subPaths.add(currentSubPath);
				currentSubPath = new ArrayList<>();
				tags = new ArrayList<>(link.getTags());
				tags.retainAll(Arrays.asList(SUBREGION_TYPE_METRO, SUBREGION_TYPE_CORE));
			}
			currentSubPath.add(link);
		}
		if(!currentSubPath.isEmpty() && !subPaths.contains(currentSubPath))
		{
			subPaths.add(currentSubPath);
		}

		return subPaths;
	}

	/*
	 * calculateSubPathBasedOnTransponder method
	 * Used for subpaths with longer distance of the maxReach, the subpath is split in the minimum number of subpaths
	 * having distance supported by the transponder modulation
	 */
	private List<List<Link>> calculateSubPathsBasedOnTransponder(List<Link> path, Transponder transponder)
	{
		int[] best = new int[path.size()];
		Arrays.fill(best,1);

		// Find the best modulation (the best spectral efficiency) with that requires the minimum number of regenerators
		List<Modulation> modulations = transponder.getModulations();
		modulations.sort(Comparator.comparingDouble(Modulation::getSpectralEfficiency));
		for(Modulation modulation: modulations) {
			int[] regenerators;
			try {
				regenerators = WDMUtils.computeRegeneratorPositions(path, modulation.getReach());
			} catch (Net2PlanException exception) {
				continue;
			}
			if(Arrays.stream(regenerators).sum()<=Arrays.stream(best).sum())
			{
				best = regenerators;
			}
		}

		// Found the best modulation, split the given path in multiple subpaths
		List<List<Link>> subPaths = new ArrayList<>();
		List<Link> currentSubPath = new ArrayList<>();
		currentSubPath.add(path.get(0));
		for(int link=1; link<path.size(); link++)
		{
			if(best[link]==1)
			{
				subPaths.add(currentSubPath);
				currentSubPath = new ArrayList<>();
			}
			currentSubPath.add(path.get(link));
		}
		subPaths.add(currentSubPath);
		return subPaths;
	}

	public void saveToXML() {
		Document dom;
		Element e;



		// instance of a DocumentBuilderFactory
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			// use factory to get an instance of document builder
			DocumentBuilder db = dbf.newDocumentBuilder();
			// create instance of DOM
			dom = db.newDocument();

			Element rootElem = dom.createElement("root");
			Element dataElem = dom.createElement("data");

			//number of demands
			e = dom.createElement("demands");
			e.appendChild(dom.createTextNode(Integer.toString(demandNumber)));
			dataElem.appendChild(e);
			//Qos demands
			int pr = 0;
			int be = 0;
			for(Demand demand: netPlan.getDemands(ipLayer))
			{
				if(demand.getQosType().equals(QOS_TYPE_PRIORITY)) {
					pr++;
				}
				else {
					be++;
				}
			}
			e = dom.createElement("priority");
			e.appendChild(dom.createTextNode(Integer.toString(pr)));
			dataElem.appendChild(e);
			e = dom.createElement("best_effort");
			e.appendChild(dom.createTextNode(Integer.toString(be)));
			dataElem.appendChild(e);

			// core metro relation
			e = dom.createElement("priority_percentage");
			e.appendChild(dom.createTextNode(Double.toString(percentageOfCoreTraffic.getDouble())));
			dataElem.appendChild(e);

			//
			e = dom.createElement("single_transponder_for_all");
			e.appendChild(dom.createTextNode(Boolean.toString(singleTransponderForAll.getBoolean())));
			dataElem.appendChild(e);

			e = dom.createElement("single_transponder_type");
			e.appendChild(dom.createTextNode(Boolean.toString(singleTransponderForAll.getBoolean())));
			dataElem.appendChild(e);

			// create data elements and place them under root
			e = dom.createElement("number_ZR");
			e.appendChild(dom.createTextNode(Integer.toString(totalZR)));
			dataElem.appendChild(e);

			e = dom.createElement("number_LR");
			e.appendChild(dom.createTextNode(Integer.toString(totalLR)));
			dataElem.appendChild(e);

			e = dom.createElement("total_Cost");
			e.appendChild(dom.createTextNode(Integer.toString(totalCost)));
			dataElem.appendChild(e);

			//per island:

			for(int island=1;island<10; island++){
				List<Link> IslandLinks = new ArrayList<>(netPlan.getTaggedLinks("Island"+island));
				//if(IslandLinks.isEmpty()) throw new Net2PlanException("no link for island " + island);
				int islandTransponder = 0;
				int island_ZR = 0;
				int island_LR = 0;

				for(Link link:IslandLinks){

					island_ZR += Integer.parseInt(link.getAttribute("ZR"));
					island_LR += Integer.parseInt(link.getAttribute("LR"));

				}


				// Add transponder per island, ZR and LR per island
				islandTransponder += island_ZR + island_LR;

				e = dom.createElement("Transponder_Island"+island);
				e.appendChild(dom.createTextNode(Integer.toString(islandTransponder)));
				dataElem.appendChild(e);

				e = dom.createElement("ZR_Island"+island);
				e.appendChild(dom.createTextNode(Integer.toString(island_ZR)));
				dataElem.appendChild(e);

				e = dom.createElement("LR_Island"+island);
				e.appendChild(dom.createTextNode(Integer.toString(island_LR)));
				dataElem.appendChild(e);

			}

			// Add info per NODE:

			List<Node> nodesList = new ArrayList<>(netPlan.getNodes());

			int node_ZR;
			int node_LR;

			int number_of_demands_per_node;

			char ch = '-';


			for(Node node: nodesList ){

				// Transponder info
				node_ZR  = Integer.parseInt(node.getAttribute("ZR"));
				node_LR  = Integer.parseInt(node.getAttribute("LR"));

				e = dom.createElement("ZR_Node"+node.getName().replace(' ', ch));
				e.appendChild(dom.createTextNode(Integer.toString(node_ZR)));
				dataElem.appendChild(e);

				e = dom.createElement("LR_Node"+node.getName().replace(' ', ch));
				e.appendChild(dom.createTextNode(Integer.toString(node_LR)));
				dataElem.appendChild(e);

				// Demands info

				List<Demand> demandListNode = new ArrayList<>(node.getOutgoingDemands(wdmLayer));

				number_of_demands_per_node = demandListNode.size();

				e = dom.createElement("num_demands_Node"+node.getName().replace(' ', ch));
				e.appendChild(dom.createTextNode(Integer.toString(number_of_demands_per_node)));
				dataElem.appendChild(e);

				int count_dem = 0;
				for(Demand dem: demandListNode){

					e = dom.createElement("Node"+node.getName().replace(' ', ch)+"number"+count_dem);
					e.appendChild(dom.createTextNode(dem.getEgressNode().getName().replace(' ', ch)));
					dataElem.appendChild(e);
					count_dem ++;
				}
			}

			rootElem.appendChild(dataElem);
			dom.appendChild(rootElem);

			try {
				Transformer tr = TransformerFactory.newInstance().newTransformer();
				tr.setOutputProperty(OutputKeys.INDENT, "yes");
				tr.setOutputProperty(OutputKeys.METHOD, "xml");
				tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
				tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

				// send DOM to file
				tr.transform(new DOMSource(dom),
						new StreamResult(Files.newOutputStream(Paths.get(resultPath.getString(),
								new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date())+
										"simulationOutput.xml"))));

			} catch (TransformerException | IOException te) {
				te.printStackTrace();
			}
		} catch (ParserConfigurationException pce) {
			System.out.println("UsersXML: Error trying to instantiate DocumentBuilder " + pce);
		}
	}



}

/*
http://www.net2plan.com/documentation/current/javadoc/examples/com/net2plan/examples/general/offline/Offline_ipOverWdm_routingSpectrumAndModulationAssignmentILPNotGrooming.html
https://github.com/girtel/Net2Plan/blob/master/Net2Plan-Core/src/main/java/com/net2plan/libraries/WDMUtils.java
 */