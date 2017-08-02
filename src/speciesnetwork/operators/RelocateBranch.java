package speciesnetwork.operators;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Operator;
import beast.util.Randomizer;
import speciesnetwork.Network;
import speciesnetwork.NetworkNode;
import speciesnetwork.SanityChecks;

/**
 * Pick an internal network node randomly.
 * If bifurcation node: move the end of either the two parent branches.
 * If reticulation node: move the top of either the two child branches.
 * Propose a destination branch randomly from all possible branches, and attach the picked branch to the new position.
 *
 * @author Chi Zhang
 */

@Description("Relocate the source of an edge starting with speciation node, " +
             "or the destination of an edge ending with hybridization node.")
public class RelocateBranch extends Operator {
    public final Input<Network> speciesNetworkInput =
            new Input<>("speciesNetwork", "The species network.", Validate.REQUIRED);
    public final Input<Boolean> isWideInput =
            new Input<>("isWide", "If true, change the node height (default is true).", true);

    private boolean isWide;

    // empty constructor to facilitate construction by XML + initAndValidate
    public RelocateBranch() {
    }

    @Override
    public void initAndValidate() {
        isWide = isWideInput.get();
    }

    @Override
    public double proposal() {
        final Network speciesNetwork = speciesNetworkInput.get();
        SanityChecks.checkNetworkSanity(speciesNetwork.getOrigin());

        // number of branches in the network
        final int branchCount = speciesNetwork.getBranchCount();

        // pick an internal node randomly
        final NetworkNode[] internalNodes = speciesNetwork.getInternalNodes();
        final int rIndex = Randomizer.nextInt(internalNodes.length);
        final NetworkNode pN = internalNodes[rIndex];

        // start moving
        speciesNetwork.startEditing(this);

        final double logProposalRatio;

        if (pN.isReticulation()) {
            // move the end of either the two parent branches
            final Integer pickedBranchNr, pNpNPBranchNr;
            if (Randomizer.nextBoolean()) {
                pickedBranchNr = pN.gammaBranchNumber;
                pNpNPBranchNr = pN.gammaBranchNumber + 1;
            } else {
                pickedBranchNr = pN.gammaBranchNumber + 1;
                pNpNPBranchNr = pN.gammaBranchNumber;
            }
            // determine nodes around pN
            final NetworkNode pP = pN.getParentByBranch(pickedBranchNr);
            final NetworkNode pNP = pN.getParentByBranch(pNpNPBranchNr);
            final Integer pNpCBranchNr = pN.childBranchNumbers.get(0);
            final NetworkNode pC = pN.getChildByBranch(pNpCBranchNr);
            // upper and lower bounds for the backward move
            final double bounds = pNP.getHeight() - pC.getHeight();

            // pick a candidate branch randomly
            Integer attachBranchNr;
            do {
                attachBranchNr = Randomizer.nextInt(branchCount);
            } while (attachBranchNr.equals(pickedBranchNr) || attachBranchNr.equals(pNpNPBranchNr));
            final int aCNodeNr = speciesNetwork.getNodeNumber(attachBranchNr);
            final NetworkNode aC = speciesNetwork.getNode(aCNodeNr);
            final NetworkNode aP = aC.getParentByBranch(attachBranchNr);

            final double upper = aP.getHeight();
            final double lower = aC.getHeight();
            // propose an attachment height
            final double newHeight = lower + (upper - lower) * Randomizer.nextDouble();

            // relocate the picked node and branch
            if (newHeight < pP.getHeight()) {
                // the reticulation direction is not changed
                pN.setHeight(newHeight);
                // join pC and pNP
                pNP.childBranchNumbers.remove(pNpNPBranchNr);
                pNP.childBranchNumbers.add(pNpCBranchNr);
                // separate aC and aP by pN
                aP.childBranchNumbers.remove(attachBranchNr);
                aP.childBranchNumbers.add(pNpNPBranchNr);
                pN.childBranchNumbers.remove(pNpCBranchNr);
                pN.childBranchNumbers.add(attachBranchNr);
                // update relationships
                pNP.updateRelationships();
                pC.updateRelationships();
                aP.updateRelationships();
                pN.updateRelationships();
                aC.updateRelationships();
            }
            else if (pP.isReticulation()) {
                // cannot move in this case
                return Double.NEGATIVE_INFINITY;
            }
            else {
                // the reticulation direction is changed
                pN.setHeight(pP.getHeight());
                pP.setHeight(newHeight);
                // determine nodes around pP
                final Integer pPpPPBranchNr = pP.gammaBranchNumber;
                final NetworkNode pPP = pP.getParentByBranch(pPpPPBranchNr);
                final Integer pPpPCBranchNr;
                if (pP.childBranchNumbers.get(0).equals(pickedBranchNr))
                    pPpPCBranchNr = pP.childBranchNumbers.get(1);
                else
                    pPpPCBranchNr = pP.childBranchNumbers.get(0);
                final NetworkNode pPC = pP.getChildByBranch(pPpPCBranchNr);

                // join pC and pNP
                pNP.childBranchNumbers.remove(pNpNPBranchNr);
                pNP.childBranchNumbers.add(pNpCBranchNr);
                // replace pP by pN
                pPP.childBranchNumbers.remove(pPpPPBranchNr);
                pPP.childBranchNumbers.add(pNpNPBranchNr);
                pN.childBranchNumbers.remove(pNpCBranchNr);
                pN.childBranchNumbers.add(pPpPCBranchNr);
                // separate aC and aP by pP
                aP.childBranchNumbers.remove(attachBranchNr);
                aP.childBranchNumbers.add(pPpPPBranchNr);
                pP.childBranchNumbers.remove(pPpPCBranchNr);
                pP.childBranchNumbers.add(attachBranchNr);
                // update relationships
                pNP.updateRelationships();
                pC.updateRelationships();
                pPP.updateRelationships();
                pN.updateRelationships();
                pPC.updateRelationships();
                aP.updateRelationships();
                pP.updateRelationships();
                aC.updateRelationships();
            }

            logProposalRatio = Math.log(upper - lower) - Math.log(bounds);
        }
        else {
            // move the top of either the two child branches
            final Integer pickedBranchNr, pNpNCBranchNr;
            if (Randomizer.nextBoolean()) {
                pickedBranchNr = pN.childBranchNumbers.get(0);
                pNpNCBranchNr = pN.childBranchNumbers.get(1);
            } else {
                pickedBranchNr = pN.childBranchNumbers.get(1);
                pNpNCBranchNr = pN.childBranchNumbers.get(0);
            }
            // determine nodes around pN
            final NetworkNode pC = pN.getChildByBranch(pickedBranchNr);
            final NetworkNode pNC = pN.getChildByBranch(pNpNCBranchNr);
            final Integer pNpPBranchNr = pN.gammaBranchNumber;
            final NetworkNode pP = pN.getParentByBranch(pNpPBranchNr);
            // upper and lower bounds for the backward move
            final double bounds = pP.getHeight() - pNC.getHeight();

            // pick a candidate branch randomly
            Integer attachBranchNr;
            do {
                attachBranchNr = Randomizer.nextInt(branchCount);
            } while (attachBranchNr.equals(pickedBranchNr) || attachBranchNr.equals(pNpPBranchNr));
            final int aCNodeNr = speciesNetwork.getNodeNumber(attachBranchNr);
            final NetworkNode aC = speciesNetwork.getNode(aCNodeNr);
            final NetworkNode aP = aC.getParentByBranch(attachBranchNr);

            final double upper = aP.getHeight();
            final double lower = aC.getHeight();
            // propose an attachment height
            final double newHeight = lower + (upper - lower) * Randomizer.nextDouble();

            // relocate the picked node and branch
            if (newHeight > pC.getHeight()) {
                pN.setHeight(newHeight);
                // join pP and pNC
                pP.childBranchNumbers.remove(pNpPBranchNr);
                pP.childBranchNumbers.add(pNpNCBranchNr);
                // separate aC and aP by pN
                aP.childBranchNumbers.remove(attachBranchNr);
                aP.childBranchNumbers.add(pNpPBranchNr);
                pN.childBranchNumbers.remove(pNpNCBranchNr);
                pN.childBranchNumbers.add(attachBranchNr);
                // update relationships
                pP.updateRelationships();
                pNC.updateRelationships();
                aP.updateRelationships();
                pN.updateRelationships();
                aC.updateRelationships();
            }
            else if (pC.isSpeciation()) {
                // cannot move in this case
                return Double.NEGATIVE_INFINITY;
            }
            else {
                // the reticulation direction is changed
                pN.setHeight(pC.getHeight());
                pC.setHeight(newHeight);
                // determine nodes around pC
                final Integer pCpCCBranchNr = pC.childBranchNumbers.get(0);
                final NetworkNode pCC = pC.getChildByBranch(pCpCCBranchNr);
                final Integer pCpCPBranchNr;
                if (pC.gammaBranchNumber.equals(pickedBranchNr))
                    pCpCPBranchNr = pC.gammaBranchNumber + 1;
                else
                    pCpCPBranchNr = pC.gammaBranchNumber;
                final NetworkNode pCP = pC.getParentByBranch(pCpCPBranchNr);

                // join pP and pNC
                pP.childBranchNumbers.remove(pNpPBranchNr);
                pP.childBranchNumbers.add(pNpNCBranchNr);
                // replace pC by pN
                pCP.childBranchNumbers.remove(pCpCPBranchNr);
                pCP.childBranchNumbers.add(pNpPBranchNr);
                pN.childBranchNumbers.remove(pNpNCBranchNr);
                pN.childBranchNumbers.add(pCpCCBranchNr);
                // separate aC and aP by pC
                aP.childBranchNumbers.remove(attachBranchNr);
                aP.childBranchNumbers.add(pCpCPBranchNr);
                pC.childBranchNumbers.remove(pCpCCBranchNr);
                pC.childBranchNumbers.add(attachBranchNr);
                // update relationships
                pP.updateRelationships();
                pNC.updateRelationships();
                pCP.updateRelationships();
                pN.updateRelationships();
                pCC.updateRelationships();
                aP.updateRelationships();
                pC.updateRelationships();
                aC.updateRelationships();
            }

            logProposalRatio = Math.log(upper - lower) - Math.log(bounds);
        }

        SanityChecks.checkNetworkSanity(speciesNetwork.getOrigin());

        return logProposalRatio;
    }
}
