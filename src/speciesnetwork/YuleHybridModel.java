package speciesnetwork;

import java.util.*;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.State;
import beast.core.parameter.RealParameter;
import beast.core.Distribution;
import beast.math.distributions.Beta;

/**
 * Pure birth model for the species network.
 * @author Chi Zhang
 */

@Description("Pure birth model (i.e. no death) with hybridization")
public class YuleHybridModel extends Distribution {
    public final Input<Network> networkInput =
            new Input<>("network", "The species network.", Validate.REQUIRED);
    public final Input<RealParameter> birthRateInput =
            new Input<>("birthRate", "Speciation rate, lambda.");
    public final Input<RealParameter> hybridRateInput =
            new Input<>("hybridRate", "Hybridization rate, nu.");
    public final Input<RealParameter> netDiversification =
            new Input<RealParameter>("netDiversification", "Net diversification rate: lambda-nu");
    public final Input<RealParameter> turnOverInput = new Input<RealParameter>("turnOver", "Turn over rate: nu/lambda");
    public final Input<RealParameter> rhoProbInput =
            new Input<>("rho", "Sampling prob. of extant species, rho.");
    public final Input<RealParameter> betaShapeInput =
            new Input<>("betaShape", "Shape of the symmetric beta prior distribution on gammas.", Validate.REQUIRED);
    // public final Input<RealParameter> originInput =
    //        new Input<RealParameter>("origin", "The time when the process started.");

    private static Comparator<NetworkNode> hc = new NodeHeightComparator();

    private double lambda, nu;
    private Beta betaPrior;

    @Override
    public void initAndValidate() {
        if (birthRateInput.get() != null && hybridRateInput.get() != null) {
            lambda = birthRateInput.get().getValue();
            nu = hybridRateInput.get().getValue();
        }
        else if (netDiversification.get() != null && turnOverInput.get() != null) {
            lambda = netDiversification.get().getValue() / (1 - turnOverInput.get().getValue());
            nu = lambda * turnOverInput.get().getValue();
        } else {
            throw new RuntimeException("Either specify speciationRate and hybridizationRate " +
                                        "OR specify netDiversification and turnOver.");
        }
        // rho = rhoProbInput.get() == null ? 1.0 : rhoProbInput.get().getValue();

        // make sure that all tips are at the same height, otherwise this Yule Model is not appropriate
        final Network network = networkInput.get();
        final double firstHeight = network.nodes[0].height;
        for (int i = 1; i < network.leafNodeCount; i++) {
            final double height = network.nodes[i].height;
            if (Math.abs(firstHeight - height) > 1e-8) {
                throw new RuntimeException("Yule Model cannot handle dated tips!");
            }
        }

        betaPrior = new Beta();
        betaPrior.alphaInput.setValue(betaShapeInput.get(), betaPrior);
        betaPrior.betaInput.setValue(betaShapeInput.get(), betaPrior);
    }

    @Override
    public double calculateLogP() {
        final Network network = networkInput.get();

        // sort the internal nodes according to their heights in ascending order
        List<NetworkNode> nodes = Arrays.asList(network.getInternalNodesWithOrigin());
        nodes.sort(hc);

        logP = 0.0;
        // calculate probability of the network
        for (int i = 0; i < nodes.size(); i++) {
            final NetworkNode node = nodes.get(i);
            final double nodeHeight = node.getHeight();
            final double nextHeight;
            if (i == 0)  // the youngest internal node
                nextHeight = 0.0;  // the tip
            else
                nextHeight = nodes.get(i-1).getHeight();

            final int nBranch = network.getBranchCount((nodeHeight + nextHeight) /2.0);
            logP += (nBranch * lambda + nu * nBranch * (nBranch -1) /2) * (nextHeight - nodeHeight);
            if (i > 0 && nodes.get(i-1).isReticulation())
                logP += Math.log(nu);
            else if (i > 0)
                logP += Math.log(lambda);

            if (node.isReticulation()) logP += betaPrior.logDensity(node.inheritProb);
        }
        return logP;
    }

    @Override
    protected boolean requiresRecalculation() {
        return networkInput.get().isDirty() ||
                birthRateInput.get().somethingIsDirty() ||
                hybridRateInput.get().somethingIsDirty() ||
                netDiversification.get().somethingIsDirty() ||
                turnOverInput.get().somethingIsDirty();
    }

    @Override
    public List<String> getArguments() {
        return null;
    }

    @Override
    public List<String> getConditions() {
        return null;
    }

    @Override
    public void sample(State state, Random random) { }
}
