package speciesnetwork;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Operator;
import beast.core.StateNode;
import beast.core.parameter.IntegerParameter;
import beast.evolution.alignment.Taxon;
import beast.evolution.alignment.TaxonSet;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.util.Randomizer;

import java.util.*;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * @author Huw Ogilvie
 * @author Chi Zhang
 */

@Description("Combine a gene tree operator with RebuildEmbedding.")
public class JointReembedding extends Operator {
    public Input<Tree> geneTreeInput = new Input<>("geneTree", "The gene tree.", Validate.REQUIRED);
    public Input<Network> speciesNetworkInput = new Input<>("speciesNetwork", "The species network.", Validate.REQUIRED);
    public Input<TaxonSet> taxonSuperSetInput =
            new Input<>("taxonSuperset", "Super-set of taxon sets mapping lineages to species.", Validate.REQUIRED);
    public Input<IntegerParameter> embeddingInput =
            new Input<>("embedding", "The matrix to embed the gene tree within the species network.", Validate.REQUIRED);
    public Input<Operator> operatorInput
            = new Input<>("operator", "Tree operator to combine into RebuildEmbedding.");

    private Map<String, NetworkNode> tipMap = new HashMap<>();
    // heirs are the gene tree leaf tip numbers below each gene tree node or species network node
    private Multimap<Node, Integer> geneNodeHeirs = HashMultimap.create();
    private Multimap<NetworkNode, Integer> speciesNodeHeirs = HashMultimap.create();
    
    private int speciesLeafCount;
    private Tree geneTree;
    private Network speciesNetwork;
    private IntegerParameter embedding;

    @Override
    public void initAndValidate() {
        // generate map of species network tip names to species network tip nodes
        geneTree = geneTreeInput.get();
        speciesNetwork = speciesNetworkInput.get();
        embedding = embeddingInput.get();
        final Map<String, NetworkNode> speciesNodeMap = new HashMap<>();

        final List<NetworkNode> speciesLeafNodes = speciesNetwork.getLeafNodes();
        speciesLeafCount = speciesLeafNodes.size();
        for (NetworkNode leafNode: speciesLeafNodes) {
            final String speciesName = leafNode.getID();
            speciesNodeMap.put(speciesName, leafNode);
        }

        // generate map of gene tree tip names to species network tip nodes
        final TaxonSet taxonSuperSet = taxonSuperSetInput.get();
        final Set<Taxon> speciesSet = new HashSet<>(taxonSuperSet.taxonsetInput.get());

        for (Taxon species: speciesSet) {
            final String speciesName = species.getID();
            final NetworkNode speciesNode = speciesNodeMap.get(speciesName);
            final TaxonSet speciesTaxonSet = (TaxonSet) species;
            final Set<Taxon> tipSet = new HashSet<>(speciesTaxonSet.taxonsetInput.get());

            for (Taxon tip: tipSet) {
                final String tipName = tip.getID();
                tipMap.put(tipName, speciesNode);
            }
        }
    }

    @Override
    public double proposal() {
        // count the number of alternative traversing choices for the current state (n0)
        final int oldChoices = getNumberOfChoices();
        if (oldChoices < 0)
            return Double.NEGATIVE_INFINITY;  // not a valid embedding

        // first make the operation
        Operator op = operatorInput.get();
        double logHR = op.proposal();
        // Update calculation nodes as subsequent operators may depend on state nodes made dirty by this operation.
        if (!op.listStateNodes().isEmpty())  // copied from JointOperator
            op.listStateNodes().get(0).getState().checkCalculationNodesDirtiness();

        // then rebuild the embedding
        resetEmbedding();
        for (final Node geneLeaf : geneTree.getExternalNodes()) {
            setLeafNodeHeirs(geneLeaf);
            recurseGeneHeirs(geneLeaf);
        }
        for (final NetworkNode speciesLeaf : speciesNetwork.getLeafNodes()) {
            recurseSpeciesHeirs(speciesLeaf);
        }
        if (!recurseRebuild(geneTree.getRoot(), speciesNetwork.getRoot()))
            return Double.NEGATIVE_INFINITY;

        final int newChoices = getNumberOfChoices();
        if (newChoices < 0)
            return Double.NEGATIVE_INFINITY;  // not a valid embedding

        return logHR + (newChoices - oldChoices) * Math.log(2);
    }

    private void resetEmbedding() {
        // nothing traverses through species leaf nodes
        final int traversalNodeCount = speciesNetwork.getNodeCount() - speciesNetwork.getLeafNodeCount();
        final int geneNodeCount = geneTree.getNodeCount();

        embedding.setDimension(traversalNodeCount * geneNodeCount);
        embedding.setMinorDimension(geneNodeCount);

        // initialize embedding matrix to -1 (no traversal)
        for (int i = 0; i < traversalNodeCount; i++) {
            for (int j = 0; j < geneNodeCount; j++) {
                embedding.setMatrixValue(i, j, -1);
            }
        }
    }

    // the heir for each gene leaf node is itself
    // the heirs for each species leaf node is the associated gene leaf nodes
    private void setLeafNodeHeirs(final Node geneTreeNode) {
        final String tipName = geneTreeNode.getID();
        final NetworkNode speciesNetworkNode = tipMap.get(tipName);
        // System.out.println(String.format("%s - %d", speciesNetworkNode.getID(), geneTreeNode.getNr()));
        geneNodeHeirs.put(geneTreeNode, geneTreeNode.getNr());
        speciesNodeHeirs.put(speciesNetworkNode, geneTreeNode.getNr());
    }

    private void recurseGeneHeirs(final Node geneTreeNode) {
        if (!geneTreeNode.isLeaf()) {
            final Node leftChild = geneTreeNode.getLeft();
            final Node rightChild = geneTreeNode.getRight();
            geneNodeHeirs.putAll(geneTreeNode, geneNodeHeirs.get(leftChild));
            geneNodeHeirs.putAll(geneTreeNode, geneNodeHeirs.get(rightChild));
        }

        final Node parentNode = geneTreeNode.getParent();
        if (parentNode != null) recurseGeneHeirs(parentNode);
    }

    private void recurseSpeciesHeirs(final NetworkNode speciesNetworkNode) {
        if (!speciesNetworkNode.isLeaf()) {
            final NetworkNode leftChild = speciesNetworkNode.getLeftChild();
            final NetworkNode rightChild = speciesNetworkNode.getRightChild();
            if (leftChild != null) speciesNodeHeirs.putAll(speciesNetworkNode, speciesNodeHeirs.get(leftChild));
            if (rightChild != null) speciesNodeHeirs.putAll(speciesNetworkNode, speciesNodeHeirs.get(rightChild));
        }

        final NetworkNode leftParent = speciesNetworkNode.getLeftParent();
        final NetworkNode rightParent = speciesNetworkNode.getRightParent();

        if (leftParent != null) recurseSpeciesHeirs(leftParent);
        if (rightParent != null) recurseSpeciesHeirs(rightParent);
    }

    private boolean recurseRebuild(final Node geneTreeNode, final NetworkNode speciesNetworkNode) {
        final double geneTreeNodeHeight = geneTreeNode.getHeight();
        final double speciesNetworkNodeHeight = speciesNetworkNode.getHeight();
        // this coalescence node must be embedded in a descendant species network branch
        if (geneTreeNodeHeight < speciesNetworkNodeHeight) {
            final int traversalNodeNumber = speciesNetworkNode.getNr() - speciesLeafCount;
            final int geneTreeNodeNumber = geneTreeNode.getNr();

            final Collection<Integer> requiredHeirs = geneNodeHeirs.get(geneTreeNode);

            final NetworkNode leftSpecies = speciesNetworkNode.getLeftChild();
            final NetworkNode rightSpecies = speciesNetworkNode.getRightChild();

            if (leftSpecies != null && speciesNodeHeirs.get(leftSpecies).containsAll(requiredHeirs)) {
                if (rightSpecies != null && speciesNodeHeirs.get(rightSpecies).containsAll(requiredHeirs)) {
                    // both species children are compatible with the gene tree, in which case the embedding becomes stochastic
                    if (Randomizer.nextBoolean()) {
                        embedding.setMatrixValue(traversalNodeNumber, geneTreeNodeNumber, 0);
                        return recurseRebuild(geneTreeNode, leftSpecies);
                    } else {
                        embedding.setMatrixValue(traversalNodeNumber, geneTreeNodeNumber, 1);
                        return recurseRebuild(geneTreeNode, rightSpecies);
                    }
                } else {
                    // only the left branch is compatible with the gene tree
                    embedding.setMatrixValue(traversalNodeNumber, geneTreeNodeNumber, 0);
                    return recurseRebuild(geneTreeNode, leftSpecies);
                }
            } else if (rightSpecies != null && speciesNodeHeirs.get(rightSpecies).containsAll(requiredHeirs)) {
                // only the right branch is compatible with the gene tree
                embedding.setMatrixValue(traversalNodeNumber, geneTreeNodeNumber, 1);
                return recurseRebuild(geneTreeNode, rightSpecies);
            } else {
                // neither child branch is compatible with the gene tree
                return false;
            }
        } else if (geneTreeNode.isLeaf()) {
            return true;
        } else {
            // embed both gene tree children
            return recurseRebuild(geneTreeNode.getLeft(), speciesNetworkNode) &&
                   recurseRebuild(geneTreeNode.getRight(), speciesNetworkNode);
        }
    }

    protected int getNumberOfChoices () {
        int[] nChoices = new int[1];
        return recurseNumberOfChoices(geneTree.getRoot(), speciesNetwork.getRoot(), nChoices) ? nChoices[0] : -1;
    }

    private boolean recurseNumberOfChoices(final Node geneTreeNode, final NetworkNode speciesNetworkNode, int[] nChoices) {
        final double geneTreeNodeHeight = geneTreeNode.getHeight();
        final double speciesNetworkNodeHeight = speciesNetworkNode.getHeight();

        if (geneTreeNodeHeight < speciesNetworkNodeHeight) {
            final int traversalNodeNumber = speciesNetworkNode.getNr() - speciesLeafCount;
            final int geneTreeNodeNumber = geneTreeNode.getNr();
            final Collection<Integer> requiredHeirs = geneNodeHeirs.get(geneTreeNode);
            final NetworkNode leftSpecies = speciesNetworkNode.getLeftChild();
            final NetworkNode rightSpecies = speciesNetworkNode.getRightChild();

            if (leftSpecies != null && speciesNodeHeirs.get(leftSpecies).containsAll(requiredHeirs)) {
                if (rightSpecies != null && speciesNodeHeirs.get(rightSpecies).containsAll(requiredHeirs)) {
                    // both species children are compatible with the gene tree
                    nChoices[0]++;
                    if (embedding.getMatrixValue(traversalNodeNumber, geneTreeNodeNumber) == 0)
                        return recurseNumberOfChoices(geneTreeNode, leftSpecies, nChoices);
                    else if (embedding.getMatrixValue(traversalNodeNumber, geneTreeNodeNumber) == 1)
                        return recurseNumberOfChoices(geneTreeNode, rightSpecies, nChoices);
                    else return false;
                } else
                    return recurseNumberOfChoices(geneTreeNode, leftSpecies, nChoices);
            } else if (rightSpecies != null && speciesNodeHeirs.get(rightSpecies).containsAll(requiredHeirs)) {
                return recurseNumberOfChoices(geneTreeNode, rightSpecies, nChoices);
            } else
                return false;  // for a valid embedding, should never go here
        } else if (geneTreeNode.isLeaf()) {
            return true;
        } else {
            return recurseNumberOfChoices(geneTreeNode.getLeft(), speciesNetworkNode, nChoices) &&
                   recurseNumberOfChoices(geneTreeNode.getRight(), speciesNetworkNode, nChoices);
        }
    }
    
    @Override
    public List<StateNode> listStateNodes() {
        List<StateNode> stateNodeList = new ArrayList<>();

        stateNodeList.addAll(operatorInput.get().listStateNodes());
        stateNodeList.addAll(super.listStateNodes());

        return stateNodeList;
    }
}
