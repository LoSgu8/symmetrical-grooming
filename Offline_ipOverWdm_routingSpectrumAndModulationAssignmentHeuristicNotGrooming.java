
/*******************************************************************************
 * Copyright (c) 2017 Pablo Pavon Marino and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the 2-clause BSD License 
 * which accompanies this distribution, and is available at
 * https://opensource.org/licenses/BSD-2-Clause
 *
 * Contributors:
 *     Pablo Pavon Marino and others - initial API and implementation
 *******************************************************************************/


import cern.colt.matrix.tdouble.DoubleFactory1D;
import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import com.net2plan.interfaces.networkDesign.*;
import com.net2plan.libraries.WDMUtils;

import com.net2plan.utils.Constants.OrderingType;
import com.net2plan.utils.Constants.RoutingType;
import com.net2plan.utils.*;

import java.util.*;
import java.util.function.IntUnaryOperator;

/**
 * Algorithm based on an heuristic solving the Routing, Spectrum, Modulation Assignment (RSMA) problem with regenerator placement, 
 * in flexi (elastic) or fixed grid optical WDM networks, with or without fault tolerance and/or latency requisites.
 *
 * <p>The input design typically has two layers (IP and WDM layers), according to the typical conventions in {@link com.net2plan.libraries.WDMUtils WDMUtils}.
 * If the design has one single layer, it is first converted into a two-layer design: WDM layer taking the links (fibers) with no demands,
 * IP layer taking the traffic demands, without IP links. Any previous routes are removed.</p>
 * <p>The WDM layer is compatible with {@link com.net2plan.libraries.WDMUtils WDMUtils} Net2Plan
 * library usual assumptions:</p>
 * <ul>
 * <li>Each network node is assumed to be an Optical Add/Drop Multiplexer WDM node</li>
 * <li>Each network link at WDM layer is assumed to be an optical fiber.</li>
 * <li>The spectrum in the fibers is assumed to be composed of a number of frequency slots.
 * In fixed-grid network, each frequency slot would correspond to a wavelength channel.
 * In flexi-grid networks, it is just a frequency slot, and lightpaths can occupy more than one. In any case,
 * two lightpaths that use overlapping frequency slots cannot traverse the same fiber, since their signals would mix.</li>
 * <li>No traffic demands initially exist at the WDM layer. In this algorithms, each lightpath is associated to a WDM demand </li>
 * <li> Each traffic demand at the IP layer is a need to transmit an amount of Gbps between two nodes.
 * A demand traffic can be carried using one or more lightpaths.</li>
 * </ul>
 *
 * <p>Each lightpath (primary or backup) produced by the design is returned as a {@code Route} object.</p>
 *
 * <p>Each lightpath starts and ends in a transponder. The user is able to define a set of available transpoder types, so
 * the design can create lightpaths using any combination of them. The information user-defined per transponder is:</p>
 * <ul>
 * <li>Line rate in Gbps (typically 10, 40, 100 in fixed-grid cases, and other multiples in flexi-grid networks).</li>
 * <li>Cost</li>
 * <li>Number of frequency slots occupied (for a given line rate, this depends on the modulation the transponder uses)</li>
 * <li>Optical reach in km: Maximum allowed length in km of the lightpaths with this transponder.
 * Higher distances can be reached using signal regenerators.</li>
 * <li>Cost of a regenerator for this transponder. Regenerators can be placed at intermdiate nodes of the lightpath route,
 * regenerate its optical signal, and then permit extending its reach. We consider that regenerators cannot change the
 * frequency slots occupied by the lightpath (that is, they are not capable of wavelength conversion)</li>
 * </ul>
 *
 * <p>We assume that all the fibers use the same wavelength grid, composed of a user-defined number of frequency slots.
 * The user can also select a limit in the maximum propagation delay of a lightpath. </p>
 * <p>The output design consists in the set of lightpaths to establish, in the 1+1 case also with a 1+1 lightpath each.
 * Each lightpath is characterized by the transponder type used (which sets its line rate and number of occupied slots in the
 * traversed fibers), the particular set of contiguous frequency slots occupied, and the set of signal regeneration points (if any).
 * This information is stored in the {@code Route} object using the regular methods in WDMUTils,
 * and can be retrieved in the same form (e.g. by a report showing the WDM network information).
 * If a feasible solution is not found (one where all the demands are satisfied with the given constraints), a message is shown.</p>.
 *
 * <h2>Failure tolerance</h2>
 * <p>The user can choose among three possibilities for designing the network:</p>
 * <ul>
 * <li>No failure tolerant: The lightpaths established should be enough to carry the traffic of all the demands when no failure
 * occurs in the network, but any losses are accepted if the network suffers failures in links or nodes.</li>
 * <li>Tolerant to single-SRG (Shared Risk Group) failures with static lightpaths: All the traffic demands should be satisfied using
 * lightpaths, so that under any single-SRG failure (SRGs are taken from the input design), the surviving lightpaths are enough
 * to carry the 100% of the traffic. Note that lightpaths are static, in the sense that they are not rerouted when affected by a
 * failure (they just also fail), and the design should just overprovision the number of lightpaths to establish with that in mind.</li>
 * <li>1+1 SRG-disjoint protection: This is another form to provide single-SRG failure tolerance. Each lightpath is backed up by
 * a SRG-disjoint lightpath. The backup lightpath uses the same type of transponder
 * as the primary (and thus the same line rate, an occupies the same number of slots), its path is SRG-disjoint, and the particular
 * set of slots occupied can be different. </li>
 * </ul>
 * <h2>Use cases</h2>
 * <p>This algorithm is quite general, and fits a number of use cases designing WDM networks, for instance:</p>
 * <ul>
 * <li>Single line rate, fixed grid networks: Then, one single type of transponder will be available, which occupies one frequency slot</li>
 * <li>Mixed-Line Rate fixed-grid networks: In this case, several transponders can be available at different line rates and with different optical
 * reaches. However, all of them occupy one slot (one wavelength channel)</li>
 * <li>Single line rate, flexi-grid networks using varying-modulation transponders: Several transponders are available (or the same
 * transponder with varying configurations), all of them with the same line rate, but thanks to the different usable modulations
 * they can have different optical reaches and/or number of occupied slots.</li>
 * <li>Multiple line rate, flexi-grid networks using Bandwidth Variable Transponders: Here, it is possible to use different transponders
 * with different line rates, e.g. to reflect more sophisticated transponders which can have different configurations, varying its line rate,
 * optical reach, and number of occupied slots.</li>
 * <li>...</li>
 * </ul>
 * <h2>Some details of the algorithm</h2>
 * <p>The algorithm is based on a heuristic. Initially, at most {@code k} paths are selected for each demand and transponder type.
 * Then, in each iteration, the algorithm first orders the demands in descending order according to the traffic pending
 * to be carried (if single-SRG failure tolerance is chosen, this is the average among all the
 * states -no failure and single SRG failure). Then, all the transponder types and possible routes (or SRG-disjoint 1+1 pairs in the
 * 1+1 case) are attempted for that demand, using a first-fit approach for the slots. If an RSA is found for more than one
 * transponder and route, the one chosen is first, the one with best performance metric, and among them, the first transponder
 * according to the order in which the user put it in the input parameter, and among them the shortest one in km .
 * The performance metric used is the amount of extra traffic carried if the lightpath is established, divided by the lightpath cost,
 * summing the transponder cost, and the cost of the signal regenerators if any.</p>
 * <p>The details of the algorithm will be provided in a publication currently under elaboration.</p>
 *
 * @net2plan.keywords WDM
 * @net2plan.inputParameters
 * @author Pablo Pavon-Marino
 */
@SuppressWarnings("unchecked")
public class Offline_ipOverWdm_routingSpectrumAndModulationAssignmentHeuristicNotGrooming implements IAlgorithm
{
	private InputParameter k = new InputParameter ("k", (int) 5 , "Maximum number of admissible paths per input-output node pair" , 1 , Integer.MAX_VALUE);
	private InputParameter numFrequencySlotsPerFiber = new InputParameter ("numFrequencySlotsPerFiber", (int) 40 , "Number of wavelengths per link" , 1, Integer.MAX_VALUE);
	//private InputParameter transponderTypesInfo = new InputParameter ("transponderTypesInfo", "10 1 1 600 1" , "Transpoder types separated by \";\" . Each type is characterized by the space-separated values: (i) Line rate in Gbps, (ii) cost of the transponder, (iii) number of slots occupied in each traversed fiber, (iv) optical reach in km (a non-positive number means no reach limit), (v) cost of the optical signal regenerator (regenerators do NOT make wavelength conversion ; if negative, regeneration is not possible).");

	private InputParameter ipLayerIndex = new InputParameter ("ipLayerIndex", (int) 1 , "Index of the IP layer (-1 means default layer)");
	private InputParameter wdmLayerIndex = new InputParameter ("wdmLayerIndex", (int) 0 , "Index of the WDM layer (-1 means default layer)");
	private InputParameter maxPropagationDelayMs = new InputParameter ("maxPropagationDelayMs", (double) -1 , "Maximum allowed propagation time of a lighptath in miliseconds. If non-positive, no limit is assumed");
	private NetPlan netPlan;
	private Map<Pair<Node,Node>,List<List<Link>>> cpl;
	private NetworkLayer wdmLayer, ipLayer;
	//private WDMUtils.TransponderTypesInfo transponderInfo;
	private Map<String,Transponder> transponders = new HashMap<>();
	private int NodeNumber, LinkNumberWDM, DemandsNumberIP, SlotPerFiber, TransponderNumber;
	private DoubleMatrix2D frequencySlot2FiberOccupancy_se;

	private DoubleMatrix1D RegeneratorOccupancy;
	private Demand.IntendedRecoveryType recoveryTypeNewLps;

	private static int maxReach;



	@Override
	public String executeAlgorithm(NetPlan netPlan, Map<String, String> algorithmParameters, Map<String, String> net2planParameters)
	{
		/* Initialize all InputParameter objects defined in this object (this uses Java reflection) */
		InputParameter.initializeAllInputParameterFieldsOfObject(this, algorithmParameters);

		this.netPlan = netPlan;
		netPlan.setRoutingTypeAllDemands(RoutingType.SOURCE_ROUTING,netPlan.getNetworkLayerDefault());

		/* Create a two-layer IP over WDM design if the input is single layer */
		if (netPlan.getNumberOfLayers() == 1)
		{
			this.wdmLayer = netPlan.getNetworkLayerDefault();
			this.netPlan.setDemandTrafficUnitsName("Gbps");
			this.netPlan.setLinkCapacityUnitsName("Frequency slots");
			this.ipLayer = netPlan.addLayer("IP" , "IP layer" , "Gbps" , "Gbps" , null , null);
			for (Demand wdmDemand : netPlan.getDemands(wdmLayer)) {
				//Map <String,String> attrib= new TreeMap<>(wdmDemand.getAttributes());
				netPlan.addDemand(wdmDemand.getIngressNode(), wdmDemand.getEgressNode() , wdmDemand.getOfferedTraffic(),RoutingType.SOURCE_ROUTING , wdmDemand.getAttributes(), ipLayer);
			}
		}
		else
		{
			this.wdmLayer = wdmLayerIndex.getInt () == -1? netPlan.getNetworkLayerDefault() : netPlan.getNetworkLayer(wdmLayerIndex.getInt ());
			this.ipLayer = ipLayerIndex.getInt () == -1? netPlan.getNetworkLayerDefault() : netPlan.getNetworkLayer(ipLayerIndex.getInt ());
		}

		/* Basic checks */
		this.NodeNumber = netPlan.getNumberOfNodes();
		this.LinkNumberWDM = netPlan.getNumberOfLinks(wdmLayer);
		this.DemandsNumberIP = netPlan.getNumberOfDemands(ipLayer);
		this.SlotPerFiber = numFrequencySlotsPerFiber.getInt();
		if (NodeNumber == 0 || LinkNumberWDM == 0 || DemandsNumberIP == 0) throw new Net2PlanException("This algorithm requires a topology with links and a demand set");

		recoveryTypeNewLps = Demand.IntendedRecoveryType.NONE;

		/* Store transpoder info */
		WDMUtils.setFibersNumFrequencySlots(netPlan, SlotPerFiber, wdmLayer);

		initializeTransponders();

		/*
		this.transponderInfo = new WDMUtils.TransponderTypesInfo(transponderTypesInfo.getString());
		this.TransponderNumber = transponderInfo.getNumTypes();
		*/

		/* Remove all routes in current netPlan object. Initialize link capacities and attributes, and demand offered traffic */
		/* WDM and IP layer are in source routing type */
		netPlan.removeAllLinks (ipLayer);
		netPlan.removeAllDemands (wdmLayer);
		netPlan.removeAllMulticastDemands(wdmLayer);

		/* Initialize the slot occupancy */
		this.frequencySlot2FiberOccupancy_se = DoubleFactory2D.dense.make(SlotPerFiber, LinkNumberWDM);
		this.RegeneratorOccupancy = DoubleFactory1D.dense.make(NodeNumber);

		/* Compute the candidate path list of possible paths */
		this.cpl = netPlan.computeUnicastCandidatePathList(netPlan.getVectorLinkLengthInKm(wdmLayer) , k.getInt(), maxReach, -1, maxPropagationDelayMs.getDouble(), -1, -1, -1 , null , wdmLayer);

		/* Compute the CPL, adding the routes */
		/* 1+1 case: as many routes as 1+1 valid pairs (then, the same sequence of links can be in more than one Route).  */
		/* rest of the cases: each sequence of links appears at most once */
		Map<Link,Double> linkLengthMap = new HashMap<Link,Double> (); for (Link e : netPlan.getLinks(wdmLayer)) linkLengthMap.put(e , e.getLengthInKm());
		final int maximumNumberOfPaths = TransponderNumber *k.getInt()* DemandsNumberIP;
		List<Integer> transponderType_p = new ArrayList<Integer> (maximumNumberOfPaths);
		List<Double> cost_p = new ArrayList<Double> (maximumNumberOfPaths);
		List<Double> lineRate_p = new ArrayList<Double> (maximumNumberOfPaths);
		List<Integer> numSlots_p = new ArrayList<Integer> (maximumNumberOfPaths);
		List<List<Link>> seqLinks_p = new ArrayList<List<Link>> (maximumNumberOfPaths);
		List<int []> regPositions_p = new ArrayList<int []> (maximumNumberOfPaths);
		List<Demand> ipDemand_p = new ArrayList<Demand> (maximumNumberOfPaths);
		Map<Demand,List<Integer>> ipDemand2WDMPathListMap = new HashMap<Demand,List<Integer>> ();
		Map<Pair<Node,Node>,List<IPLink>> mapIPLinks = new HashMap<>();



		/* order demands according to QoS: first priority traffic, then best-effort.
		 * If not able to satisfy priority traffic then throw Exception (no solution).
		 * If not able to satisfy best effort traffic then go on and store statistics.
		 */

		for (Demand ipDemand : netPlan.getDemands(ipLayer)) {
			final Pair<Node, Node> nodePair = Pair.of(ipDemand.getIngressNode(), ipDemand.getEgressNode());
			boolean atLeastOnePathOrPathPair = false;
			List<Integer> pathListThisDemand = new LinkedList<Integer>();
			ipDemand2WDMPathListMap.put(ipDemand, pathListThisDemand);

			for (List<Link> singlePath : cpl.get(nodePair)) {
				//path -> list(subpath)
				List<List<Link>> subpathsList = calculateSubPath(singlePath);
				List<Modulation> modulationsList = new ArrayList<>();
				for (int ind = 0; ind < subpathsList.size(); ind++) {
					List<Link> subpath = subpathsList.get(ind);

					// If subpath length is longer than the maximum reach of the transponder -> split the subpath in shorter subpaths
					String tag = subpath.get(0).getTags().first(); // "METRO" or "CORE"
					if (this.transponders.get(tag).getMaxReach() <= getLengthInKm(subpath)) {
						List<List<Link>> subsubpaths = calculateSubPathsBasedOnTransponder(subpath, this.transponders.get(tag));
						int index = subpathsList.indexOf(subpath);
						subpathsList.remove(subpath);
						subpathsList.addAll(index, subsubpaths);
						subpath = subpathsList.get(ind);
					}
					//find the best modulation
					Modulation bestModulation = this.transponders.get(tag).getBestModulationFormat(getLengthInKm(subpath));
					modulationsList.add(bestModulation);
				}
				// guarda se ci sono ip link liberi

				//non c'è nessun link IP (un link ip per ogni demand)
				boolean successInFindingPath = true;

				// check if the entire path has available resources
				for (int ind = 0; ind < subpathsList.size(); ind++) {
					List<Link> subpath = subpathsList.get(ind);
					Modulation modulation = modulationsList.get(ind);

					int slotid = WDMUtils.spectrumAssignment_firstFit(subpath, frequencySlot2FiberOccupancy_se, modulation.getChannelSpacing());
					if (slotid == -1) {
						successInFindingPath = false;
						break;
					}
				}

				// if the entire path is able to accomodate the demand, assign the resources to it, otherwise check for the next path
				if (successInFindingPath) {

					atLeastOnePathOrPathPair = true;

					for (int ind = 0; ind < subpathsList.size(); ind++) {
						List<Link> subpath = subpathsList.get(ind);
						Modulation modulation = modulationsList.get(ind);
						//create IPLink
						int slotid = WDMUtils.spectrumAssignment_firstFit(subpath, frequencySlot2FiberOccupancy_se, modulation.getChannelSpacing());
						IPLink ipLink = new IPLink(subpath, slotid, modulation);
						Demand newDemand = netPlan.addDemand(ipLink.getStartNode(), ipLink.getEndNode(), ipLink.getModulation().getDatarate(), RoutingType.SOURCE_ROUTING, null, wdmLayer);
						final Route lp = WDMUtils.addLightpath(newDemand, ipLink.getRsa(), ipLink.getModulation().getDatarate());
						final Link n2pIPlink = newDemand.coupleToNewLinkCreated(ipLayer);
						final double ipTrafficToCarry = Math.min(ipLink.getModulation().getDatarate(), ipDemand.getBlockedTraffic());
						netPlan.addRoute(ipDemand, ipTrafficToCarry, ipTrafficToCarry, ipLink.getPath(), null);
						WDMUtils.allocateResources(ipLink.getRsa(), frequencySlot2FiberOccupancy_se, RegeneratorOccupancy);
					}

					break;
				}

				//Demand attributes Map<String><String> attributes =


				// check if the demand has not been satisfied due to insufficient resources in all paths

				if (!atLeastOnePathOrPathPair) {
					break;
					// if best-effort ignore otherwise throw exception
				}
			}
		}

		/*
			for (int t = 0; t < TransponderNumber; t++)
			{
				System.out.println(t);
				final boolean isRegenerable = transponderInfo.isOpticalRegenerationPossible(t);
				isRegenerable
				for (Object sp (singlepath) :  cpl.get(nodePair))
				{
					List<Link> firstPath = (List<Link>) sp;
					if (!isRegenerable && (getLengthInKm(firstPath) > transponderInfo.getOpticalReachKm(t))) break;
					final int[] regPositions1;
					try {
						regPositions1 = isRegenerable ? WDMUtils.computeRegeneratorPositions(firstPath, frequencySlot2FiberOccupancy_se, transponderInfo.getOpticalReachKm(t)) : new int[firstPath.size()];
					} catch(Exception e)
					{
						continue;
					}
					final int numRegeneratorsNeeded = !isRegenerable? 0 : (int) IntUtils.sum(regPositions1);
					final double costOfLightpathOr11Pair = transponderInfo.getCost(t) + (transponderInfo.getRegeneratorCost(t) * numRegeneratorsNeeded);

					final int pathIndex = cost_p.size();
					cost_p.add (costOfLightpathOr11Pair);
					transponderType_p.add (t);
					lineRate_p.add(transponderInfo.getLineRateGbps(t));
					numSlots_p.add(transponderInfo.getNumSlots(t));
					ipDemand_p.add(ipDemand);
					seqLinks_p.add(firstPath);
					regPositions_p.add(regPositions1);
					pathListThisDemand.add(pathIndex);
					atLeastOnePathOrPathPair = true;
				}
			}


			if (!atLeastOnePathOrPathPair) throw new Net2PlanException ("There are no possible routes (or 1+1 pairs) for a demand (" + ipDemand + "). The topology may be not connected enough, or the optical reach may be too small");
		}
		final int P = transponderType_p.size(); // one per potential sequence of links (or 1+1 pairs of sequences) and transponder

		/* Main algorithm loop. Take one demand at a time, in a HLDA loop (ordered by average blocked traffic).
		 * In each demand, try all possible path-transponder pairs, and take the best according to the performance metric:
		 * avExtraTrafficCarried/transponderCost */
		/*boolean atLeastOneLpAdded = false;
		Set<Integer> ipDemandIndexesNotToTry = new HashSet<Integer> ();
		double totalCost = 0;
		do
		{
			double [] b_d = getVectorIPDemandAverageAllStatesBlockedTraffic ();
			int [] ipDemandIndexes = DoubleUtils.sortIndexes(b_d , OrderingType.DESCENDING);
			atLeastOneLpAdded = false;
			for (int ipDemandIndex : ipDemandIndexes)
			{
				// Not to try a demand if already fully satisfied or we tried and could not add a lp to it
				if (ipDemandIndexesNotToTry.contains(ipDemandIndex)) continue;

				final Demand ipDemand = netPlan.getDemand(ipDemandIndex , ipLayer);
				//ipDemand.setRoutingType(RoutingType.SOURCE_ROUTING);

				/* If the demand is already fully satisfied, skip it
				if (isIpDemandFullySatisfied(ipDemand)) { ipDemandIndexesNotToTry.add(ipDemandIndex); continue; }

				/* Try all the possible routes and all the possible transponder types. Take the solution with the best
				 * performance metric (average extra carried traffic / transponder cost)
				WDMUtils.RSA best_rsa = null;
				double best_performanceMetric = 0;
				int best_pathIndex = -1;
				for (int pathIndex : ipDemand2WDMPathListMap.get (ipDemand))
				{
					List<Link> firstPath = seqLinks_p.get(pathIndex);
					int slotId = -1;

					slotId = WDMUtils.spectrumAssignment_firstFit(firstPath, frequencySlot2FiberOccupancy_se, numSlots_p.get(pathIndex));

					// Check if the path (or 1+1 path pair) is not feasible
					if (slotId == -1) continue;

					/* If the performance metric is better than existing, this is the best choice
					final double extraCarriedTraffic = getAverageAllStatesExtraCarriedTrafficAfterPotentialAllocation (ipDemand , lineRate_p.get(pathIndex) , seqLinks_p.get(pathIndex));
					final double performanceIndicator = extraCarriedTraffic / cost_p.get(pathIndex);
					if (performanceIndicator > best_performanceMetric)
					{
						best_performanceMetric = performanceIndicator;
						best_rsa = new WDMUtils.RSA(firstPath , slotId , numSlots_p.get(pathIndex) , regPositions_p.get(pathIndex));
						best_pathIndex = pathIndex;
					}
				}


				// No lp could be added to this demand, try with the next
				if (best_pathIndex == -1) { ipDemandIndexesNotToTry.add(ipDemand.getIndex()); continue; }

				// Add the lightpath to the design
				atLeastOneLpAdded = true;
				totalCost += cost_p.get(best_pathIndex);
				final Demand newWDMDemand = netPlan.addDemand(best_rsa.ingressNode , best_rsa.egressNode , lineRate_p.get(best_pathIndex), RoutingType.SOURCE_ROUTING, null , wdmLayer);
				newWDMDemand.setIntendedRecoveryType(recoveryTypeNewLps);
				final Route lp = WDMUtils.addLightpath(newWDMDemand , best_rsa , lineRate_p.get(best_pathIndex));
				final Link ipLink = newWDMDemand.coupleToNewLinkCreated(ipLayer);
				final double ipTrafficToCarry = Math.min(lineRate_p.get(best_pathIndex) , ipDemand.getBlockedTraffic());
				netPlan.addRoute(ipDemand , ipTrafficToCarry , ipTrafficToCarry , Arrays.asList(ipLink), null);
				WDMUtils.allocateResources(best_rsa , frequencySlot2FiberOccupancy_se , RegeneratorOccupancy);
				break;
			}

		} while (atLeastOneLpAdded);
		*/
		WDMUtils.checkResourceAllocationClashing(netPlan,true,true,wdmLayer);

		String outMessage = "Total cost: " + totalCost + ". Num lps (not including 1+1 backup if any) " + netPlan.getNumberOfRoutes(wdmLayer);
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

	/* A vector with the blocked traffic for each demand (in the single-SRG failure tolerance, is averaged for each state) */
	private double [] getVectorIPDemandAverageAllStatesBlockedTraffic ()
	{
		double [] res = new double [DemandsNumberIP];
		for (Demand ipDemand : netPlan.getDemands(ipLayer))
		{
			res [ipDemand.getIndex()] = ipDemand.getBlockedTraffic();
		}
		return res;
	}

	/* The average for all the states (no failure, and potentially one per SRG if single-SRG failure tolerance option is chosen),
	 * of all the blocked traffic */
	private double getAverageAllStatesExtraCarriedTrafficAfterPotentialAllocation (Demand ipDemand , double lineRateGbps , List<Link> seqLinksIfSingleSRGToleranceIsNeeded)
	{
		double extraCarriedTraffic = Math.min(ipDemand.getBlockedTraffic() , lineRateGbps);
		return extraCarriedTraffic;
	}

	/* True if the demand is fully satisfied (in the single-SRG failure case, also in each SRG) */
	private boolean isIpDemandFullySatisfied (Demand d)
	{
		if (d.getBlockedTraffic() > 1e-3) return false;

		return true;
	}

	private void initializeTransponders()
	{
		/* ZR+ OEO Transponder definition */
		List<Modulation> zrmods = new ArrayList<>(4);
		zrmods.add(new Modulation("16 QAM", 400, 75, 600));
		zrmods.add(new Modulation("8 QAM", 300, 75, 1800));
		zrmods.add(new Modulation("QPSK", 200, 75, 3000));
		zrmods.add(new Modulation("QPSK", 100, 75, 3000));

		this.transponders.put("METRO", new Transponder("ZR+ OEO", 0.5, zrmods));

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

		this.transponders.put("CORE", new Transponder("Long Reach OEO", 1, lrmods));
		maxReach = 4700;
	}


	/* --- ADDED FUNCTIONS --- */
	/*
	* calculateSubPath method
	* Split the path into subpaths each one belonging to a single network category (METRO and CORE)
	*/
	private List<List<Link>> calculateSubPath(List<Link> path) {
		List<List<Link>> subPaths = new ArrayList<>();
		SortedSet<String> tags = path.get(0).getTags();
		tags.retainAll(Arrays.asList("METRO", "CORE"));
		List<Link> currentSubPath = new ArrayList<>();
		for (Link link : path) {
			SortedSet<String> tags2 = link.getTags();
			tags2.retainAll(Arrays.asList("METRO", "CORE"));
			tags.retainAll(tags2);
			if(tags.isEmpty())
			{
				subPaths.add(currentSubPath);
				currentSubPath = new ArrayList<>();
				tags = link.getTags();
				tags.retainAll(Arrays.asList("METRO", "CORE"));
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

		// Find the best modulation(best spectral efficiency) with that requires the minimum number of regenerators
		List<Modulation> modulations = transponder.getModulations();
		modulations.sort(Comparator.comparingDouble(Modulation::getSpectralEfficiency));
		for(Modulation modulation: modulations) {
			int[] regenerators = WDMUtils.computeRegeneratorPositions(path,modulation.getReach());

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
}

/*
http://www.net2plan.com/documentation/current/javadoc/examples/com/net2plan/examples/general/offline/Offline_ipOverWdm_routingSpectrumAndModulationAssignmentILPNotGrooming.html
https://github.com/girtel/Net2Plan/blob/master/Net2Plan-Core/src/main/java/com/net2plan/libraries/WDMUtils.java
 */