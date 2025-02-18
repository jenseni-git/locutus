package link.locutus.discord.apiv1.enums.city.building.imp;

import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.apiv1.domains.Nation;
import link.locutus.discord.apiv1.domains.subdomains.SCityContainer;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.ResourceBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;

import java.util.function.Function;
import java.util.function.Predicate;

public class AResourceBuilding extends ABuilding implements ResourceBuilding {
    private final int baseInput;
    private final double boostFactor;
    private final ResourceType output;
    private final ResourceType[] inputs;
    private boolean[] continents;

    public AResourceBuilding(BuildingBuilder parent, ResourceType output) {
        this(parent, output.getBaseInput(), output.getBoostFactor(), output, output.getInputs());
    }
    public AResourceBuilding(BuildingBuilder parent, int baseInput, double boostFactor, ResourceType output, ResourceType... inputs) {
        super(parent);
        this.baseInput = baseInput;
        this.boostFactor = boostFactor;
        this.output = output;
        this.inputs = inputs;
    }

    public AResourceBuilding continents(Continent... continents) {
        if (this.continents == null) {
            this.continents = new boolean[Continent.values().length];
        }
        for (Continent continent : continents) {
            this.continents[continent.ordinal()] = true;
        }
        return this;
    }

    @Override
    public ResourceType resource() {
        return output;
    }

    @Override
    public int baseProduction() {
        return baseInput * 3;
    }

    @Override
    public double profitConverted(Continent continent, double rads, Predicate hasProjects, JavaCity city, int amt) {
        double profit = super.profitConverted(continent, rads, hasProjects, city, amt);

        int improvements = city.get(this);


        double production = output.getProduction(continent, rads, hasProjects, city.getLand(), improvements, -1);
        if (production != 0) {
            profit += PnwUtil.convertedTotalPositive(output, production);

            double inputAmt = output.getInput(continent, rads, hasProjects, city, improvements);

            if (inputAmt > 0) {
                switch (inputs.length) {
                    case 0:
                        break;
                    case 1:
                        profit -= PnwUtil.convertedTotalNegative(inputs[0], inputAmt);
                        break;
                    default:
                        for (ResourceType input : inputs) {
                            profit -= PnwUtil.convertedTotalNegative(input, inputAmt);
                        }
                }
            }
        }
        return profit;
    }

    @Override
    public double[] profit(Continent continent, double rads, long date, Predicate hasProjects, JavaCity city, double[] profitBuffer, int turns) {
        profitBuffer = super.profit(continent, rads, date, hasProjects, city, profitBuffer, turns);

        ResourceType type = resource();
        int improvements = city.get(this);

        double production = type.getProduction(continent, rads, hasProjects, city.getLand(), improvements, date);
        if (production != 0) {
            profitBuffer[type.ordinal()] += production * turns / 12;

            double inputAmt = type.getInput(continent, rads, hasProjects, city, improvements);

            for (ResourceType input : inputs) {
                if (inputAmt != 0) {
                    profitBuffer[input.ordinal()] -= inputAmt * turns / 12;
                }
            }
        }
        return profitBuffer;
    }

    @Override
    public boolean canBuild(Continent continent) {
        return this.continents == null || continents[continent.ordinal()];
    }

    @Override
    public double upkeep(ResourceType type, Predicate<Project> hasProject) {
        return super.upkeep(type, hasProject) * (hasProject.test(Projects.GREEN_TECHNOLOGIES) ? 0.9d : 1d);
    }

    @Override
    public double getUpkeepConverted(Predicate<Project> hasProject) {
        return super.getUpkeepConverted(hasProject) * (hasProject.test(Projects.GREEN_TECHNOLOGIES) ? 0.9d : 1d);
    }
}
