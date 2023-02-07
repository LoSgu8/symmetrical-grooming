import java.util.List;

public class Transponder {
    private final String name;
    private final double cost;
    private List<Modulation> modulationList;

    public Transponder(String name, double cost, List<Modulation> modulationList)
    {
        this.name = name;
        this.cost = cost;
        this.modulationList = modulationList;
    }

    public String getName () { return this.name; }
    public double getCost () { return this.cost; }
    public List<Modulation> getModulations () { return this.modulationList; }

    public Modulation getBestModulationFormat(double pathLength)
    {
        Modulation best = null;
        double bestSpectralEfficiency = 0;
        for(Modulation modulation: modulationList){
            if(modulation.getReach() >= pathLength)
            {
                if(modulation.getSpectralEfficiency()>bestSpectralEfficiency)
                {
                    best = modulation;
                    bestSpectralEfficiency = modulation.getSpectralEfficiency();
                }
            }
        }
        return best;
    }

    public int getMaxReach()
    {
        int maxReach = 0;
        for(Modulation modulation: modulationList)
        {
            if(modulation.getReach()>maxReach) maxReach = modulation.getReach();
        }
        return maxReach;
    }
}
