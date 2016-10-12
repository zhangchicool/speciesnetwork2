package speciesnetwork.operators;

import java.util.*;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.parameter.RealParameter;
import beast.util.Randomizer;
import speciesnetwork.Network;
import speciesnetwork.NetworkNode;
import speciesnetwork.SanityChecks;
import beast.core.Operator;

/**
 * Randomly pick an internal network node including origin.
 * Change its height using a sliding window with reflection.
 *
 * @author Chi Zhang
 */

@Description("Randomly selects an internal network node and move its height using an uniform sliding window.")
public class NodeSlider extends Operator {
    public final Input<Network> speciesNetworkInput =
            new Input<>("speciesNetwork", "The species network.", Validate.REQUIRED);
    public final Input<RealParameter> originInput =
            new Input<>("origin", "The time when the process started.", Validate.REQUIRED);
    public final Input<Double> windowSizeInput =
            new Input<>("windowSize", "The size of the sliding window (default is 0.1).", 0.1);

    @Override
    public void initAndValidate() {
    }

    @Override
    public double proposal() {
        final Network speciesNetwork = speciesNetworkInput.get();
        final double windowSize = windowSizeInput.get();

        // pick an internal node randomly, including origin
        final NetworkNode[] internalNodes = speciesNetwork.getInternalNodesWithOrigin();
        final int randomIndex = Randomizer.nextInt(internalNodes.length);
        NetworkNode snNode = internalNodes[randomIndex];

        // determine the lower and upper bounds
        double upper = Double.MAX_VALUE;
        for (NetworkNode p: snNode.getParents()) {
            upper = Math.min(upper, p.getHeight());
        }

        double lower = 0.0;
        for (NetworkNode c: snNode.getChildren()) {
            lower = Math.max(lower, c.getHeight());
        }

        // propose a new height, reflect it back if it's outside the boundary
        final double oldHeight = snNode.getHeight();
        double newHeight = oldHeight + (Randomizer.nextDouble() - 0.5) * windowSize;
        while (newHeight < lower || newHeight > upper) {
            if (newHeight < lower)
                newHeight = 2.0 * lower - newHeight;
            if (newHeight > upper)
                newHeight = 2.0 * upper - newHeight;
        }

        // update the new node height
        if (snNode.isOrigin()) {
            final RealParameter originTime = originInput.get();
            if (outsideBounds(newHeight, originTime))
                return Double.NEGATIVE_INFINITY;

            originTime.setValue(newHeight);
        }
        speciesNetwork.startEditing(this);
        snNode.setHeight(newHeight);
        SanityChecks.checkNetworkSanity(speciesNetwork.getOrigin());

        return 0.0;
    }

    private boolean outsideBounds(final double value, final RealParameter param) {
        final Double l = param.getLower();
        final Double h = param.getUpper();

        return (value < l || value > h);
    }
}
