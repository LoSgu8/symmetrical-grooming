public class Modulation {

    private String modulationFormat;
    private int datarate;
    private int channelSpacing;
    private int reach;

    public Modulation(String modulationFormat, int datarate, int channelSpacing, int reach)
    {
        this.modulationFormat = modulationFormat;
        this.datarate = datarate;
        this.channelSpacing = channelSpacing;
        this.reach = reach;
    }

    public String getModulationFormat() {return this.modulationFormat;}
    public int getDatarate() {return this.datarate;}
    public int getChannelSpacing() {return this.channelSpacing;}
    public int getReach() {return this.reach;}
    public double getSpectralEfficiency() {return (double) this.datarate/this.channelSpacing;}

}
