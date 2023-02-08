# symmetrical-pancake
//TODO trovare un nome migliore

Net2plan-based evaluation for a network topology divided in subregions. Heuristic evaluation of which transponder type to select for each node based on transponder cost and available modulation.



## Offline\_ipOverWdm\_routingSpectrumAndModulationAssignmentHeuristicNotGrooming.java

## Transponder.java

New class to define transponder info (name, cost and available modulation list).

* **getBestModulationFormat** method: returns the best suitable modulation given a certain path lenght.

* **getMaxReach** method: returns the max reach from all possible mopdulation formats.

## TrafficGenerator.java

Randomly select a node from the list of nodes. Generate a demand with random QoS (Priority or Best Effort). Intermediate Nodes between CCORE and METRO regions are considered CORE nodes.

Each new demand is generated from the selected node respecting the simulation proportions given below:

|           | CORE     |           | METRO    |           |
| --------- | -------- | --------- | -------- | --------- |
| SUBREGION | same     | different | same     | different |
| CORE      | 40%      | 30%       | 30%      |           |
| QOS       | Priority | Priority  | Priority |           |
| METRO     | 30%      |           | 50%      | 20%       |
| QOS       | Priority |           | 50P/50BE | 50P/50BE  |

