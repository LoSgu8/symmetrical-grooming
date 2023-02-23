import java.util.List;

public class Transponder {
    private final String name;
    private final double cost;
    private final List<Modulation> modulationList;

    public Transponder(String name, double cost, List<Modulation> modulationList)
    {
        this.name = name;
        this.cost = cost;
        this.modulationList = modulationList;
    }

    public String getName () { return this.name; }
    public double getCost () { return this.cost; }
    public List<Modulation> getModulations () { return this.modulationList; }


    public enum OBJECTIVE{
        HIGHEST_SPECTRAL_EFFICIENCY,LOWEST_SPECTRUM_OCCUPANCY
    }
    public Modulation getBestModulationFormat(double pathLength, OBJECTIVE objective)
    {
        Modulation best = null;
        switch (objective){
            case LOWEST_SPECTRUM_OCCUPANCY:
                int bestChannelSpacing = Integer.MAX_VALUE;
                for (Modulation modulation : modulationList) {
                    if (modulation.getReach() >= pathLength) {
                        if (modulation.getChannelSpacing() < bestChannelSpacing
                                || (modulation.getChannelSpacing()==bestChannelSpacing
                                && modulation.getDatarate()>(best==null?0:best.getDatarate())))
                        {
                            best = modulation;
                            bestChannelSpacing = modulation.getChannelSpacing();
                        }
                    }
                }
                break;
            case HIGHEST_SPECTRAL_EFFICIENCY:
            default:
                double bestSpectralEfficiency = 0;
                for (Modulation modulation : modulationList) {
                    if (modulation.getReach() >= pathLength) {
                        if (modulation.getSpectralEfficiency() > bestSpectralEfficiency) {
                            best = modulation;
                            bestSpectralEfficiency = modulation.getSpectralEfficiency();
                        }
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
