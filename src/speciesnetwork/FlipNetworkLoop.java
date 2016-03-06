package speciesnetwork;

import beast.core.Input;
import beast.core.Operator;
import beast.core.parameter.IntegerParameter;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.util.Randomizer;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.*;

/**
 * Flip a random gene tree lineage with all its descendants in one side of the loop to the other side in the network.
 * @author Alexei Drummond
 * @author Chi Zhang
 */
public class FlipNetworkLoop extends Operator {
    public Input<Tree> geneTreeInput =
            new Input<>("geneTree", "The gene tree.", Input.Validate.REQUIRED);
    public Input<Network> speciesNetworkInput =
            new Input<>("speciesNetwork", "The species network.", Input.Validate.REQUIRED);
    public Input<IntegerParameter> embeddingInput =
            new Input<>("embedding", "The matrix to embed the gene tree within the species network.", Input.Validate.REQUIRED);

    private IntegerParameter embedding = embeddingInput.get();
    private Multimap<NetworkNode, String> pathDirections = HashMultimap.create();
    private Multimap<Node, NetworkNode> lineagePath = HashMultimap.create();
    private int speciesLeafCount;

    @Override
    public void initAndValidate() {
        Network speciesNetwork = speciesNetworkInput.get();
        speciesLeafCount = speciesNetwork.getLeafNodes().size();
    }

    @Override
    public double proposal() {
        Tree geneTree = geneTreeInput.get();
        Network speciesNetwork = speciesNetworkInput.get();

        List<NetworkNode> hybridNodes = speciesNetwork.getReticulationNodes();
        // if there is no reticulation node, this operator doesn't apply
        if (hybridNodes.isEmpty()) return Double.NEGATIVE_INFINITY;

        // pick a hybrid node randomly from the network
        int rnd = Randomizer.nextInt(hybridNodes.size());
        NetworkNode hybridNode = hybridNodes.get(rnd);
        // find the top node of the minimal loop with the hybrid node at the bottom
        NetworkNode topNode = findLoop(hybridNode, true);  // pathDirections is also set
        final int topNodeNr = topNode.getNr();
        final int bottomNodeNr = hybridNode.getNr();

        final int geneNodeCount = geneTree.getNodeCount();
        for (int j = 0; j < geneNodeCount; j++) {
            // find the gene lineages traversing the loop-top network node
            if (embedding.getMatrixValue(topNodeNr-speciesLeafCount, j) > -1) {
                final Node geneNodeTop = geneTree.getNode(j);
                Set<NetworkNode> traversedNodes = new HashSet<>();
                // check if all the descendants of geneNodeTop traverse one side of the loop
                if (allLineagesInLoop(geneNodeTop, topNode, hybridNode, traversedNodes))
                    lineagePath.putAll(geneNodeTop, traversedNodes);
            }
        }
        // if there is no lineage traversing, this operator doesn't apply
        if (lineagePath.isEmpty()) return Double.NEGATIVE_INFINITY;

        // pick a lineage randomly, flip it (and all its descendant lineages) to the other side of the loop
        List<Node> keys = new ArrayList<>(lineagePath.keySet());
        rnd = Randomizer.nextInt(keys.size());
        final Node geneNodeTop = keys.get(rnd);

        // convert the path to direction
        final Collection<NetworkNode> geneNodeTopPath = lineagePath.get(geneNodeTop);
        String pathDir = "";  // empty path string
        convertToDirection(geneNodeTopPath, topNode, hybridNode, pathDir);
        // delete the current direction from the collection
        final Collection<String> loopPathDirections = pathDirections.get(hybridNode);
        loopPathDirections.remove(pathDir);
        // pick a new direction, store it to pathDir
        assert (loopPathDirections.size() > 0);
        rnd = Randomizer.nextInt(loopPathDirections.size());
        int idx = 0;
        for (String s :loopPathDirections) {
            if (idx == rnd) pathDir = s;
            idx++;
        }

        // make the flip


        return 0.0;
    }

    /**
     * @param hybridNode the hybrid node forming the bottom of the loop
     * @return the top network node on the minimal loop from the given hybridization node
     */
    private NetworkNode findLoop(NetworkNode hybridNode, boolean cleanup) {
        // check if hybridNode is actually hybrid
        if (!hybridNode.isReticulation()) throw new RuntimeException();

        NetworkNode topNode = new NetworkNode();

        // traverse left, label A; traverse right, label B
        label(hybridNode.getLeftParent(), "A", null, null);
        label(hybridNode.getRightParent(), "B", "A", topNode);

        // find all the paths connecting top node and hybrid node
        getPathDirections(topNode, topNode, hybridNode, "A");
        getPathDirections(topNode, topNode, hybridNode, "B");

        if (cleanup) {
            unlabel(hybridNode.getLeftParent(), "A");
            unlabel(hybridNode.getRightParent(), "B");
        }

        return topNode;
    }

    private void unlabel(NetworkNode node, String label) {
        node.removeLabel(label);

        if (node.getLeftParent() != null) unlabel(node.getLeftParent(), label);
        if (node.getRightParent() != null) unlabel(node.getRightParent(), label);
    }

    private void label(NetworkNode node, String label, String checkLabel, NetworkNode returnNode) {
        node.addLabel(label);

        if (checkLabel != null && node.hasLabel(checkLabel)) {
            if (returnNode == null || node.getHeight() < returnNode.getHeight())
                returnNode = node;
        }

        if (node.getLeftParent() != null) label(node.getLeftParent(), label, checkLabel, returnNode);
        if (node.getRightParent() != null) label(node.getRightParent(), label, checkLabel, returnNode);
    }

    /**
     * get (forward in time) directions of the loop
     * @param topNode    top node of the loop (speciation node)
     * @param bottomNode bottom node of the loop (hybrid node)
     * @param checkLabel label
     */
    private void getPathDirections(NetworkNode node, NetworkNode topNode, NetworkNode bottomNode, String checkLabel) {
        if (node == topNode) {
            pathDirections.put(node, "");  // initialize as empty string
        }
        if (node == bottomNode)
            return;

        NetworkNode leftNode = node.getLeftChild();
        if (leftNode != null && (leftNode.hasLabel(checkLabel) || leftNode == bottomNode)) {
            for (final String s : pathDirections.get(node)) {
                pathDirections.put(leftNode, s + "0");  // traversing left
            }
            getPathDirections(leftNode, topNode, bottomNode, checkLabel);
        }

        NetworkNode rightNode = node.getRightChild();
        if (rightNode != null && (rightNode.hasLabel(checkLabel) || rightNode == bottomNode)) {
            for (final String s : pathDirections.get(node)) {
                pathDirections.put(rightNode, s + "1"); // traversing right
            }
            getPathDirections(rightNode, topNode, bottomNode, checkLabel);
        }
    }

    /**
     * Are geneNode and its descendants traversing one side of the loop
     */
    private boolean allLineagesInLoop (Node geneNode, NetworkNode netNode, NetworkNode bottomNode, Set<NetworkNode> traversedNodes) {
        if (netNode.isLeaf()) return false;
        traversedNodes.add(netNode); // add the node to the path set
        if (netNode == bottomNode) return true;

        if (geneNode.getHeight() < netNode.getHeight()) {
            final int traversalNodeNr = netNode.getNr()-speciesLeafCount;
            final int geneNodeNr = geneNode.getNr();
            final NetworkNode leftNode = netNode.getLeftChild();
            final NetworkNode rightNode = netNode.getRightChild();

            if (embedding.getMatrixValue(traversalNodeNr, geneNodeNr) == 0 && leftNode != null) {
                return allLineagesInLoop(geneNode, leftNode, bottomNode, traversedNodes);
            }
            else if (embedding.getMatrixValue(traversalNodeNr, geneNodeNr) == 1 && rightNode != null) {
                return allLineagesInLoop(geneNode, rightNode, bottomNode, traversedNodes);
            } else {
                return false; // something is wrong
            }
        } else {
            return !geneNode.isLeaf() && allLineagesInLoop(geneNode.getLeft(), netNode, bottomNode, traversedNodes) &&
                    allLineagesInLoop(geneNode.getRight(), netNode, bottomNode, traversedNodes);
        }
    }

    private void convertToDirection(Collection<NetworkNode> pathNodes, NetworkNode start, NetworkNode end, String direction) {
        // pathNodes should contain network nodes forming a path connecting start and end
        while (start != end) {
            NetworkNode left = start.getLeftChild();
            NetworkNode right = start.getRightChild();
            if (left != null && pathNodes.contains(left)) {
                direction += "0";
                start = left;
            } else if (right != null && pathNodes.contains(right)) {
                direction += "1";
                start = right;
            } else return;
        }
    }
}