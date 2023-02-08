# symmetrical-pancake
* Python
  - GNPy?

# SIUM
WDMUtils for transponders


# Offline\_ipOverWdm\_routingSpectrumAndModulationAssignmentHeuristicNotGrooming.java

# Transponder.java

New class to define transponder info (name, cost and available modulation list).

* **getBestModulationFormat** method: returns the best suitable modulation given a certain path lenght.

* **getMaxReach** method: returns the max reach from all possible mopdulation formats.

# TrafficGenerator.java

Randomly select a node from the list of nodes. Generate a demand with random QoS (Priority or Best Effort). Intermediate Nodes between CCORE and METRO regions are considered CORE nodes.


|------------------------------------------------------|  
|                CORE     |       METRO                |  
|SUBREGION: same | diff   |  same  |  diff             |  
|------------------------------------------------------|  
|CORE       0.4  |  0.3   |      0.3                   |  
|QOS         H       H    |       H                    |  
|------------------------------------------------------|  
|METRO           0.7      |  0.1         |  0.2        |  
|QOS             H        | 0.5H/0.5BE   | 0.3H/0.7BE  |  
|------------------------------------------------------|  
